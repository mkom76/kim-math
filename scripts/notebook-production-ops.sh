#!/usr/bin/env bash
# Owner-dispatched deployment setup. No student data, SQL, paid calls, or secret output.
set -euo pipefail
cd /home/suhui/kim-math
python3 - <<'PY'
import base64, hashlib, json, os, pathlib, re, stat, subprocess, tempfile, time, uuid
ROOT = pathlib.Path('/home/suhui/kim-math')
REFS = ROOT / 'notebook-reference-data'
PINS = [
    ('dd07_p001_eq01.png', 'abfe862ae8d05db877fc576f84bbb4cd2e80c841fc8b948d05835029fc0df16e'),
    ('dd07_p005_ann01.png', '8111e655ca71ed45d386d63259c394b6f8ca4436614f525c8eaa52496bd1129e'),
]
COMPOSE = ['docker-compose', '-f', str(ROOT / 'docker-compose.prod.yml')]
def run(args):
    return subprocess.run(args, cwd=ROOT, check=True, text=True, capture_output=True).stdout.strip()
def inspect(template):
    return run(['docker','inspect','--format',template,'kim-math-backend'])
def atomic(path, data, mode):
    fd, name = tempfile.mkstemp(prefix='.notebook-', dir=path.parent)
    try:
        with os.fdopen(fd,'wb') as f:
            f.write(data); f.flush(); os.fsync(f.fileno())
        os.chmod(name, mode)
        os.replace(name, path)
    finally:
        if os.path.exists(name): os.unlink(name)
def settings(values):
    path = ROOT / '.env'
    lines = path.read_text().splitlines()
    for key, value in values.items():
        lines = [line for line in lines if not re.match(r'^\s*(?:export\s+)?'+re.escape(key)+r'\s*=',line)]
        if '\n' in value or '\r' in value: raise RuntimeError('Invalid environment value')
        lines.append(key+'='+value)
    atomic(path, ('\n'.join(lines)+'\n').encode(), stat.S_IMODE(path.stat().st_mode))
def verify_files():
    for name, digest in PINS:
        if hashlib.sha256((REFS/name).read_bytes()).hexdigest() != digest:
            raise RuntimeError('Private reference checksum mismatch')
def wait_healthy():
    for _ in range(40):
        if inspect('{{.State.Health.Status}}') == 'healthy': return
        time.sleep(3)
    raise RuntimeError('Backend did not become healthy')
def verify_ready(expected):
    if inspect('{{index .Config.Labels "org.opencontainers.image.revision"}}') != expected:
        raise RuntimeError('Unexpected backend image revision')
    verify_files()
    values = json.loads(run(['docker','inspect','--format','{{json .Config.Env}}','kim-math-backend']))
    env = dict(value.split('=',1) for value in values if '=' in value)
    required = {'SOLUTION_NOTEBOOK_ENABLED':'true','SOLUTION_NOTEBOOK_DAILY_LIMIT':'3',
                'SOLUTION_NOTEBOOK_GLOBAL_DAILY_LIMIT':'20','SOLUTION_NOTEBOOK_GLOBAL_MONTHLY_LIMIT':'200',
                'SOLUTION_NOTEBOOK_REFERENCE_DIR':'/run/notebook-references',
                'SOLUTION_NOTEBOOK_USAGE_DIR':'/var/lib/kim-math/notebook-usage','SPRING_PROFILES_ACTIVE':'prod'}
    if any(env.get(k) != v for k,v in required.items()) or not env.get('OPENAI_API_KEY'):
        raise RuntimeError('Notebook runtime configuration is not ready')
    for name,digest in PINS:
        actual = run(['docker','exec','kim-math-backend','sha256sum','/run/notebook-references/'+name]).split()[0]
        if actual != digest: raise RuntimeError('Container reference checksum mismatch')
    run(['docker','exec','kim-math-backend','sh','-c','test -w /var/lib/kim-math/notebook-usage'])
    print('notebook_enabled=true')
    print('student_daily_limit=3')
    print('site_daily_limit=20')
    print('site_monthly_limit=200')
    print('private_references_verified=2')
    print('openai_key_configured=true')
operation = os.environ.get('NOTEBOOK_OPERATION','')
expected = os.environ.get('NOTEBOOK_EXPECTED_SERVER_SHA','')
if not re.fullmatch('[a-f0-9]{40}', expected): raise RuntimeError('Invalid expected server revision')
if run(['git','rev-parse','HEAD']) != expected: raise RuntimeError('Production revision changed')
if run(['git','status','--porcelain','--untracked-files=no']): raise RuntimeError('Production has tracked edits')
if operation == 'notebook-prepare':
    # Only the two explicitly approved immutable reference images are accepted.
    payloads = [os.environ.get('NOTEBOOK_REF1_A','')+os.environ.get('NOTEBOOK_REF1_B',''),os.environ.get('NOTEBOOK_REF2','')]
    decoded = [base64.b64decode(v, validate=True) for v in payloads]
    for data, (_, digest) in zip(decoded,PINS):
        if hashlib.sha256(data).hexdigest() != digest: raise RuntimeError('Transfer checksum mismatch')
    REFS.mkdir(mode=0o750, exist_ok=True)
    for data,(name,digest) in zip(decoded,PINS):
        path = REFS/name
        if path.exists():
            if hashlib.sha256(path.read_bytes()).hexdigest() != digest: raise RuntimeError('Unexpected existing reference')
        else: atomic(path,data,0o600)
    uid = int(run(['docker','exec','kim-math-backend','id','-u']))
    gid = int(run(['docker','exec','kim-math-backend','id','-g']))
    project = inspect('{{index .Config.Labels "com.docker.compose.project"}}')
    if project != 'kim-math': raise RuntimeError('Unexpected compose project')
    image = inspect('{{.Image}}')
    volume = 'kim-math_notebook-usage'
    run(['docker','volume','create','--label','com.docker.compose.project=kim-math',
         '--label','com.docker.compose.volume=notebook-usage',volume])
    run(['docker','run','--rm','--network','none','--read-only','--user','0',
         '--mount','type=bind,source='+str(REFS)+',target=/refs',
         '--mount','type=volume,source='+volume+',target=/usage','--entrypoint','sh',image,'-c',
         'chgrp '+str(gid)+' /refs /refs/*.png && chmod 750 /refs && chmod 440 /refs/*.png && chown '+str(uid)+':'+str(gid)+' /usage && chmod 700 /usage'])
    probe = '.notebook-probe-'+uuid.uuid4().hex
    run(['docker','run','--rm','--network','none','--read-only','--user',str(uid)+':'+str(gid),
         '--mount','type=bind,source='+str(REFS)+',target=/refs,readonly',
         '--mount','type=volume,source='+volume+',target=/usage','--entrypoint','sh',image,'-c',
         'test -r /refs/dd07_p001_eq01.png && test -r /refs/dd07_p005_ann01.png && : > "/usage/$1" && rm -- "/usage/$1"','sh',probe])
    # Preserve the resolved existing feedback key; never send it to the workflow runner.
    config = json.loads(run(COMPOSE+['config','--format','json']))
    key = config['services']['backend']['environment'].get('OPENAI_API_KEY','')
    updates = {'SOLUTION_NOTEBOOK_ENABLED':'false','SOLUTION_NOTEBOOK_DAILY_LIMIT':'3',
               'SOLUTION_NOTEBOOK_GLOBAL_DAILY_LIMIT':'20','SOLUTION_NOTEBOOK_GLOBAL_MONTHLY_LIMIT':'200',
               'SOLUTION_NOTEBOOK_REFERENCE_HOST_DIR':str(REFS)}
    if not key:
        key = run(['docker','exec','kim-math-backend','printenv','OPENAI_API_KEY'])
        if not re.fullmatch(r'sk-[A-Za-z0-9_-]+',key): raise RuntimeError('Existing feedback key unavailable')
        updates['OPENAI_API_KEY'] = key
    settings(updates)
    verify_files()
    print('notebook_prepare=passed')
    print('private_references_verified=2')
    print('usage_volume_permissions=passed')
    print('openai_key_reused=true')
elif operation == 'notebook-activate':
    if inspect('{{index .Config.Labels "org.opencontainers.image.revision"}}') != expected:
        raise RuntimeError('Expected code has not been deployed')
    verify_files()
    settings({'SOLUTION_NOTEBOOK_ENABLED':'true'})
    try:
        run(COMPOSE+['up','-d','--no-deps','backend'])
        wait_healthy()
        verify_ready(expected)
        print('notebook_activation=passed')
    except Exception:
        # Fail closed once; do not retry image generation or change database state.
        settings({'SOLUTION_NOTEBOOK_ENABLED':'false'})
        run(COMPOSE+['up','-d','--no-deps','backend'])
        print('notebook_activation=failed_feature_disabled')
        raise
elif operation == 'notebook-verify':
    wait_healthy()
    verify_ready(expected)
    print('notebook_verification=passed')
else:
    raise RuntimeError('Unsupported notebook operation')
PY
