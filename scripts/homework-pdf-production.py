#!/usr/bin/env python3
"""Owner-dispatched PDF homework operations. Never print credentials or records."""
import hashlib
import gzip
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import stat
import subprocess
import tarfile
import tempfile
import time
import urllib.request
from datetime import datetime, timezone

ROOT = Path('/home/suhui/kim-math')
BACKUPS = Path('/home/suhui/backups/homework-pdf')
FEATURE_TABLES = ('homework_pdf_imports', 'homework_pdf_questions')
MIGRATION = 'migrations/2026-11-13-01-add-homework-pdf-imports.sql'
DATABASE = 'kim_math'
TARGET_FINGERPRINT = 'fb69afbd343e0186c389b2aaaf7c1c8123fdedfb253c0cf4f1701d0d32dccdec'
MIGRATION_HASH = '866c147c002d714ba1f9f726580a8600d9f03ce995c7c8c6fa24966e3564f528'
PDF_VOLUME = 'kim-math_homework-pdfs'


def run(args, data=None, timeout=60):
    result = subprocess.run(args, input=data, cwd=ROOT, capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError('Command failed: ' + ' '.join(args[:2]) + ' (no automatic retry)')
    return result.stdout.decode().strip()


def inspect(container, expression):
    return run(['docker', 'inspect', '--format', expression, container])


def environment(container):
    values = json.loads(inspect(container, '{{json .Config.Env}}'))
    return dict(value.split('=', 1) for value in values if '=' in value)


def sql(statement, container='kim-math-mysql', database=None):
    target = '"$MYSQL_DATABASE"' if database is None else '"$1"'
    args = ['docker', 'exec', '-i', container, 'sh', '-c',
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --batch --skip-column-names '
            + target, 'sh']
    if database is not None:
        args.append(database)
    return run(args, data=statement.encode())


def source_file(source, path):
    if not re.fullmatch('[a-f0-9]{40}', source):
        raise RuntimeError('Invalid source revision')
    with urllib.request.urlopen('https://raw.githubusercontent.com/mkom76/kim-math/' + source + '/' + path, timeout=30) as response:
        return response.read()


def migration_source(source):
    data = source_file(source, MIGRATION).replace(b'\r\n', b'\n')
    if hashlib.sha256(data).hexdigest() != MIGRATION_HASH:
        raise RuntimeError('Migration checksum mismatch')
    return data


def file_sha256(path):
    digest = hashlib.sha256()
    with open(path, 'rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def database_guard():
    name, version, fingerprint = sql('SELECT DATABASE(),VERSION(),SHA2(@@server_uuid,256);').split('\t')
    if name != DATABASE or fingerprint != TARGET_FINGERPRINT or not version.startswith('8.'):
        raise RuntimeError('Production database target does not match the reviewed preflight')


def feature_tables():
    return sql("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
               "AND TABLE_NAME IN ('homework_pdf_imports','homework_pdf_questions') ORDER BY TABLE_NAME;").splitlines()


def schema_signature():
    clauses = " TABLE_SCHEMA=DATABASE() AND TABLE_NAME NOT IN ('homework_pdf_imports','homework_pdf_questions') "
    columns = sql("SELECT TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION,COLUMN_TYPE,IS_NULLABLE,COALESCE(COLUMN_DEFAULT,'<NULL>'),EXTRA "
                  "FROM information_schema.COLUMNS WHERE" + clauses + "ORDER BY TABLE_NAME,ORDINAL_POSITION;")
    indexes = sql("SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,INDEX_TYPE "
                  "FROM information_schema.STATISTICS WHERE" + clauses + "ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;")
    return hashlib.sha256((columns + '\n' + indexes).encode()).hexdigest()


def verify_feature_schema(database=None):
    rows = sql("SELECT TABLE_NAME,COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
               "AND TABLE_NAME IN ('homework_pdf_imports','homework_pdf_questions') GROUP BY TABLE_NAME ORDER BY TABLE_NAME;", database=database)
    if rows.splitlines() != ['homework_pdf_imports\t22', 'homework_pdf_questions\t13']:
        raise RuntimeError('PDF table columns do not match the reviewed migration')
    counts = sql("SELECT CONSTRAINT_TYPE,COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() "
                 "AND TABLE_NAME IN ('homework_pdf_imports','homework_pdf_questions') GROUP BY CONSTRAINT_TYPE ORDER BY CONSTRAINT_TYPE;", database=database)
    if dict(line.split('\t') for line in counts.splitlines()) != {'CHECK':'3', 'FOREIGN KEY':'6', 'PRIMARY KEY':'2', 'UNIQUE':'4'}:
        raise RuntimeError('PDF table constraints do not match the reviewed migration')


def validate_dump(path):
    forbidden = re.compile(rb'^\s*(?:USE\s|(?:CREATE|DROP|ALTER)\s+(?:DATABASE|USER)\s|GRANT\s|REVOKE\s|SET\s+@@GLOBAL)', re.I)
    completed = False
    with gzip.open(path, 'rb') as source:
        for line in source:
            if forbidden.match(line):
                raise RuntimeError('Backup contains a database switch or global operation')
            if line.startswith(b'-- Dump completed on '):
                completed = True
    if not completed:
        raise RuntimeError('Backup is incomplete')


def backup_database(directory):
    path = directory / 'database.sql.gz'
    command = ['docker', 'exec', 'kim-math-mysql', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --user=root --single-transaction --quick '
               '--routines --triggers --events --hex-blob --set-gtid-purged=OFF --no-tablespaces '
               '--skip-add-locks --default-character-set=utf8mb4 "$MYSQL_DATABASE"']
    with open(path, 'xb') as target, tempfile.TemporaryFile() as errors:
        os.chmod(path, 0o600)
        process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=errors)
        try:
            with gzip.GzipFile(fileobj=target, mode='wb') as compressed:
                shutil.copyfileobj(process.stdout, compressed, 1024 * 1024)
            if process.wait(timeout=300):
                raise RuntimeError('Database backup failed; no production SQL applied')
        finally:
            process.stdout.close()
            if process.poll() is None:
                process.kill()
                process.wait()
    validate_dump(path)
    return path


def scratch_names(nonce):
    if not re.fullmatch('[a-f0-9]{24}', nonce):
        raise RuntimeError('Invalid restore verification identifier')
    return 'km_pdf_verify_' + nonce, 'pdfv_' + nonce


def restricted_command(username, database):
    return ['docker', 'exec', '-i', 'kim-math-mysql', 'sh', '-c',
            'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql --batch --skip-column-names --binary-mode '
            '--default-character-set=utf8mb4 --user="$1" --database="$2"', 'sh', username, database]


def restore_check(backup, migration=None):
    nonce = secrets.token_hex(12)
    database, username = scratch_names(nonce)
    password = secrets.token_hex(24)
    expected_tables = int(sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE';"))
    if sql("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + database + "';") != '0' or sql("SELECT COUNT(*) FROM mysql.user WHERE User='" + username + "';") != '0':
        raise RuntimeError('Restore verification resource already exists')
    try:
        sql('CREATE DATABASE `' + database + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
            "CREATE USER '" + username + "'@'localhost' IDENTIFIED BY '" + password + "';"
            'GRANT ALL PRIVILEGES ON `' + database + "`.* TO '" + username + "'@'localhost';")
        with gzip.open(backup, 'rb') as source, tempfile.TemporaryFile() as errors:
            process = subprocess.Popen(restricted_command(username, database), cwd=ROOT, stdin=subprocess.PIPE,
                                       stdout=subprocess.DEVNULL, stderr=errors)
            try:
                process.stdin.write(password.encode() + b'\n')
                shutil.copyfileobj(source, process.stdin, 1024 * 1024)
                process.stdin.close()
                if process.wait(timeout=300):
                    raise RuntimeError('Isolated backup restore failed; no production SQL applied')
            except BrokenPipeError:
                raise RuntimeError('Isolated backup restore stopped; no production SQL applied') from None
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait()
        restored_tables = int(sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE';", database=database))
        if restored_tables != expected_tables:
            raise RuntimeError('Restored table count differs from production')
        names = sql("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;", database=database).splitlines()
        if any(not re.fullmatch('[A-Za-z0-9_]+', name) for name in names):
            raise RuntimeError('Unexpected restored table name')
        checked = sql('CHECK TABLE ' + ','.join('`' + name + '`' for name in names) + ';', database=database)
        if len(checked.splitlines()) != expected_tables or any(not line.endswith('\tstatus\tOK') for line in checked.splitlines()):
            raise RuntimeError('Restored table integrity check failed')
        if migration is not None:
            run(restricted_command(username, database), data=password.encode() + b'\n' + migration, timeout=120)
        verify_feature_schema(database)
        emit('backup_restore_verification', 'passed')
        if migration is not None:
            emit('migration_rehearsal', 'passed')
        emit('restored_existing_tables', restored_tables)
    finally:
        # Names come only from scratch_names; never delete the application database.
        if (database, username) != scratch_names(nonce) or database == DATABASE:
            raise RuntimeError('Invalid restore cleanup target')
        sql('DROP DATABASE IF EXISTS `' + database + "`; DROP USER IF EXISTS '" + username + "'@'localhost';")


def atomic_file(path, data, mode=0o600):
    descriptor, name = tempfile.mkstemp(prefix='.homework-pdf-', dir=path.parent)
    try:
        with os.fdopen(descriptor, 'wb') as target:
            target.write(data)
            target.flush()
            os.fsync(target.fileno())
        os.chmod(name, mode)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def update_settings(values):
    path = ROOT / '.env'
    if path.is_symlink():
        raise RuntimeError('Unexpected environment file link')
    lines = path.read_text().splitlines()
    for key, value in values.items():
        if not re.fullmatch('[A-Z0-9_]+', key) or '\n' in value or '\r' in value:
            raise RuntimeError('Invalid setting')
        lines = [line for line in lines if not re.match(r'^\s*(?:export\s+)?' + re.escape(key) + r'\s*=', line)]
        lines.append(key + '=' + value)
    atomic_file(path, ('\n'.join(lines) + '\n').encode(), stat.S_IMODE(path.stat().st_mode))


def backup_directory():
    if BACKUPS.is_symlink():
        raise RuntimeError('Unexpected backup directory link')
    BACKUPS.mkdir(parents=True, mode=0o700, exist_ok=True)
    os.chmod(BACKUPS, 0o700)
    path = BACKUPS / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + secrets.token_hex(6))
    path.mkdir(mode=0o700)
    with open(path / 'environment.before', 'xb') as target:
        os.chmod(target.name, 0o600)
        target.write((ROOT / '.env').read_bytes())
    return path


def prepare_volume(directory):
    image = inspect('kim-math-backend', '{{.Image}}')
    uid = run(['docker', 'exec', 'kim-math-backend', 'id', '-u'])
    gid = run(['docker', 'exec', 'kim-math-backend', 'id', '-g'])
    if not uid.isdecimal() or not gid.isdecimal():
        raise RuntimeError('Invalid application user identity')
    volumes = run(['docker', 'volume', 'ls', '--format', '{{.Name}}']).splitlines()
    if PDF_VOLUME in volumes:
        # Before first migration the reviewed source volume must contain no prior originals.
        run(['docker', 'run', '--rm', '--network', 'none', '--read-only', '--user', '0',
             '--mount', 'type=volume,source=' + PDF_VOLUME + ',target=/pdf,readonly', '--entrypoint', 'sh', image,
             '-c', 'test -z "$(find /pdf -mindepth 1 -print -quit)"'])
    else:
        run(['docker', 'volume', 'create', '--label', 'com.docker.compose.project=kim-math',
             '--label', 'com.docker.compose.volume=homework-pdfs', PDF_VOLUME])
    run(['docker', 'run', '--rm', '--network', 'none', '--read-only', '--user', '0',
         '--mount', 'type=volume,source=' + PDF_VOLUME + ',target=/pdf', '--entrypoint', 'sh', image,
         '-c', 'chown "$1:$2" /pdf && chmod 700 /pdf', 'sh', uid, gid])
    probe = '.write-probe-' + secrets.token_hex(8)
    run(['docker', 'run', '--rm', '--network', 'none', '--read-only', '--user', uid + ':' + gid,
         '--mount', 'type=volume,source=' + PDF_VOLUME + ',target=/pdf', '--entrypoint', 'sh', image,
         '-c', ': > "/pdf/$1" && rm -- "/pdf/$1"', 'sh', probe])
    atomic_file(directory / 'pdf-volume-before.json', json.dumps({'volume': PDF_VOLUME, 'registeredOriginals': 0, 'empty': True}).encode())
    emit('private_pdf_volume_writable', True)


def migrate(expected, source):
    env = verify_target(expected)
    database_guard()
    if feature_tables():
        raise RuntimeError('Feature tables already exist; do not rerun a one-time migration')
    if sql('SELECT @@read_only,@@super_read_only;') != '0\t0':
        raise RuntimeError('Production database is read-only')
    if sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' AND ENGINE<>'InnoDB';") != '0':
        raise RuntimeError('Snapshot requires InnoDB application tables')
    objects = sum(int(sql('SELECT COUNT(*) FROM information_schema.' + table + ' WHERE ' + field + '=DATABASE();'))
                  for table, field in [('VIEWS','TABLE_SCHEMA'), ('TRIGGERS','TRIGGER_SCHEMA'), ('ROUTINES','ROUTINE_SCHEMA'), ('EVENTS','EVENT_SCHEMA')])
    if objects or sql('SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_SCHEMA IS NOT NULL AND REFERENCED_TABLE_SCHEMA<>DATABASE();') != '0':
        raise RuntimeError('Backup restore needs separate review for stored programs, views or cross-schema references')
    if shutil.disk_usage(ROOT).free < 1024 ** 3:
        raise RuntimeError('Insufficient free space for a verified backup')
    migration = migration_source(source)
    signature = schema_signature()
    directory = backup_directory()
    backup = backup_database(directory)
    restore_check(backup, migration)
    manifest = {'source': source, 'serverBefore': expected, 'database': DATABASE, 'targetFingerprint': TARGET_FINGERPRINT,
                'migrationSha256': MIGRATION_HASH, 'backup': str(backup), 'backupSha256': file_sha256(backup),
                'backupRestoreVerified': True, 'migrationRehearsalPassed': True, 'state': 'backup-verified'}
    atomic_file(directory / 'manifest.json', json.dumps(manifest, indent=2).encode())
    prepare_volume(directory)
    settings = {'HOMEWORK_PDF_ENABLED':'false', 'HOMEWORK_PDF_MODEL':'gpt-6.1-sol',
                'HOMEWORK_PDF_DAILY_LIMIT':'3', 'HOMEWORK_PDF_GLOBAL_DAILY_LIMIT':'20'}
    # Preserve the live key if the existing environment file lacks it. Never send it to the runner.
    resolved = json.loads(run(['docker-compose', '-f', str(ROOT / 'docker-compose.prod.yml'), 'config', '--format', 'json']))
    if not resolved['services']['backend']['environment'].get('OPENAI_API_KEY'):
        key = env.get('OPENAI_API_KEY', '')
        if not re.fullmatch(r'sk-[A-Za-z0-9_-]+', key):
            raise RuntimeError('Existing AI key unavailable')
        settings['OPENAI_API_KEY'] = key
    update_settings(settings)
    # Recheck immediately before the only production DDL. No retry or rollback DROP is used.
    verify_target(expected)
    database_guard()
    if feature_tables():
        raise RuntimeError('Feature tables appeared during preparation; stop without rerunning SQL')
    sql('SET SESSION lock_wait_timeout=15;\n' + migration.decode())
    verify_feature_schema()
    if schema_signature() != signature:
        raise RuntimeError('Existing application schema changed during migration')
    verify_target(expected)
    manifest.update(state='migration-verified', existingSchemaUnchanged=True, privateVolume=PDF_VOLUME)
    atomic_file(directory / 'manifest.json', json.dumps(manifest, indent=2).encode())
    atomic_file(BACKUPS / 'last-success.json', json.dumps(manifest, indent=2).encode())
    emit('backup_directory', directory)
    emit('database_backup_sha256', manifest['backupSha256'])
    emit('production_pdf_tables', 2)
    emit('existing_schema_unchanged', True)
    emit('homework_pdf_migration', 'passed')


def wait_healthy():
    for _ in range(60):
        if inspect('kim-math-backend', '{{.State.Health.Status}}') == 'healthy':
            return
        time.sleep(2)
    raise RuntimeError('Backend health timeout')


def verify_runtime(expected):
    env = verify_target(expected)
    database_guard()
    verify_feature_schema()
    if inspect('kim-math-backend', '{{index .Config.Labels "org.opencontainers.image.revision"}}') != expected:
        raise RuntimeError('Backend image is not the expected deployed revision')
    required = {'HOMEWORK_PDF_ENABLED':'true', 'HOMEWORK_PDF_STORAGE_DIR':'/var/lib/kim-math/homework-pdfs',
                'HOMEWORK_PDF_MODEL':'gpt-6.1-sol', 'HOMEWORK_PDF_DAILY_LIMIT':'3', 'HOMEWORK_PDF_GLOBAL_DAILY_LIMIT':'20'}
    if any(env.get(key) != value for key, value in required.items()) or not env.get('OPENAI_API_KEY'):
        raise RuntimeError('PDF runtime settings are not ready')
    if env.get('APP_HOMEWORK_PDF_MOCK', 'false').lower() == 'true':
        raise RuntimeError('Mock analysis must not be enabled in production')
    mounts = json.loads(inspect('kim-math-backend', '{{json .Mounts}}'))
    if not any(mount.get('Name') == PDF_VOLUME and mount.get('Destination') == required['HOMEWORK_PDF_STORAGE_DIR'] and mount.get('RW') for mount in mounts):
        raise RuntimeError('Private PDF volume is not mounted')
    run(['docker', 'exec', 'kim-math-backend', 'sh', '-c', 'test -w /var/lib/kim-math/homework-pdfs'])
    emit('pdf_enabled', True)
    emit('mock_analysis_enabled', False)
    emit('private_pdf_volume_writable', True)
    emit('openai_key_configured', True)
    emit('homework_pdf_verification', 'passed')


def activate(expected):
    verify_target(expected)
    database_guard()
    verify_feature_schema()
    record = json.loads((BACKUPS / 'last-success.json').read_text())
    if record.get('state') != 'migration-verified' or record.get('migrationSha256') != MIGRATION_HASH or record.get('targetFingerprint') != TARGET_FINGERPRINT:
        raise RuntimeError('Verified migration and backup record is unavailable')
    if inspect('kim-math-backend', '{{index .Config.Labels "org.opencontainers.image.revision"}}') != expected:
        raise RuntimeError('Expected application code has not been deployed')
    update_settings({'HOMEWORK_PDF_ENABLED':'true'})
    try:
        run(['docker-compose', '-f', str(ROOT / 'docker-compose.prod.yml'), 'up', '-d', '--no-deps', 'backend'], timeout=180)
        wait_healthy()
        verify_runtime(expected)
        emit('homework_pdf_activation', 'passed')
    except Exception:
        update_settings({'HOMEWORK_PDF_ENABLED':'false'})
        run(['docker-compose', '-f', str(ROOT / 'docker-compose.prod.yml'), 'up', '-d', '--no-deps', 'backend'], timeout=180)
        emit('homework_pdf_activation', 'failed_feature_disabled')
        raise


def backup_pair(expected):
    verify_runtime(expected)
    directory = backup_directory()
    backup = backup_database(directory)
    # Registered originals are immutable; archive after the DB snapshot so every snapshot reference exists.
    originals = sql("SELECT storage_key,file_sha256 FROM homework_pdf_imports WHERE state='REGISTERED' ORDER BY id;")
    required = dict(line.split('\t') for line in originals.splitlines())
    if any(not re.fullmatch('[a-f0-9-]{36}', key) or not re.fullmatch('[a-f0-9]{64}', digest) for key, digest in required.items()):
        raise RuntimeError('Invalid registered original metadata')
    archive = directory / 'pdf-originals.tar.gz'
    image = inspect('kim-math-backend', '{{.Image}}')
    with open(archive, 'xb') as target, tempfile.TemporaryFile() as errors:
        os.chmod(archive, 0o600)
        result = subprocess.run(['docker', 'run', '--rm', '--network', 'none', '--read-only', '--user', '0',
                                 '--mount', 'type=volume,source=' + PDF_VOLUME + ',target=/pdf,readonly',
                                 '--entrypoint', 'tar', image, '-C', '/pdf', '-czf', '-', '.'],
                                cwd=ROOT, stdout=target, stderr=errors, timeout=300)
        if result.returncode:
            raise RuntimeError('Original archive failed; do not use this backup set')
    with tarfile.open(archive, 'r:gz') as archived:
        members = {member.name.removeprefix('./'): member for member in archived.getmembers() if member.isfile()}
        for key, digest in required.items():
            member = members.get(key + '.pdf')
            if member is None:
                raise RuntimeError('Registered original is missing from the backup set')
            hasher = hashlib.sha256()
            with archived.extractfile(member) as data:
                for chunk in iter(lambda: data.read(1024 * 1024), b''):
                    hasher.update(chunk)
            if hasher.hexdigest() != digest:
                raise RuntimeError('Original checksum differs from the registered PDF')
    restore_check(backup)
    manifest = {'state':'paired-backup-verified', 'database':DATABASE, 'targetFingerprint':TARGET_FINGERPRINT,
                'server':expected, 'databaseBackup':str(backup), 'originalsBackup':str(archive),
                'databaseSha256':file_sha256(backup),
                'originalsSha256':file_sha256(archive),
                'registeredOriginalsVerified':len(required), 'backupRestoreVerified':True}
    atomic_file(directory / 'manifest.json', json.dumps(manifest, indent=2).encode())
    atomic_file(BACKUPS / 'last-paired-backup.json', json.dumps(manifest, indent=2).encode())
    emit('backup_directory', directory)
    emit('registered_originals_verified', len(required))
    emit('homework_pdf_paired_backup', 'passed')


def emit(name, value):
    print(name + '=' + (str(value).lower() if isinstance(value, bool) else str(value)), flush=True)


def verify_target(expected):
    if not re.fullmatch('[a-f0-9]{40}', expected):
        raise RuntimeError('Invalid expected production revision')
    if run(['git', 'remote', 'get-url', 'origin']) not in (
            'https://github.com/mkom76/kim-math.git', 'git@github.com:mkom76/kim-math.git'):
        raise RuntimeError('Unexpected production repository')
    current = run(['git', 'rev-parse', 'HEAD'])
    emit('server_sha', current)
    if current != expected or run(['git', 'status', '--porcelain', '--untracked-files=no']):
        raise RuntimeError('Production revision changed or tracked edits exist')
    for container in ('kim-math-backend', 'kim-math-frontend', 'kim-math-mysql'):
        health = inspect(container, '{{.State.Health.Status}}')
        emit(container + '_health', health)
        if health != 'healthy':
            raise RuntimeError('Production container is not healthy')
    if inspect('kim-math-backend', '{{index .Config.Labels "com.docker.compose.project"}}') != 'kim-math':
        raise RuntimeError('Unexpected compose project')
    env = environment('kim-math-backend')
    if env.get('SPRING_PROFILES_ACTIVE') != 'prod':
        raise RuntimeError('Unexpected backend profile')
    return env


def preflight(expected, source):
    env = verify_target(expected)
    db = environment('kim-math-mysql')
    name = db.get('MYSQL_DATABASE', '')
    if not re.fullmatch('[A-Za-z0-9_]+', name) or not db.get('MYSQL_ROOT_PASSWORD'):
        raise RuntimeError('Production database identity or credential unavailable')
    actual_name, version, target_id = sql('SELECT DATABASE(),VERSION(),SHA2(@@server_uuid,256);').split('\t')
    if name != actual_name or not version.startswith('8.'):
        raise RuntimeError('Unexpected database identity or version')
    tables = sql("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
                 "AND TABLE_NAME IN ('homework_pdf_imports','homework_pdf_questions') ORDER BY TABLE_NAME;")
    emit('database', name)
    emit('mysql_version', version)
    emit('database_target_fingerprint', target_id)
    emit('feature_tables_present', len(tables.splitlines()) if tables else 0)
    emit('database_bytes', sql('SELECT COALESCE(SUM(DATA_LENGTH+INDEX_LENGTH),0) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE();'))
    emit('non_innodb_tables', sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' AND ENGINE<>'InnoDB';"))
    emit('filesystem_available_mb', shutil.disk_usage(ROOT).free // 1024 // 1024)
    memory = dict(line.split(':', 1) for line in Path('/proc/meminfo').read_text().splitlines())
    emit('memory_available_mb', int(memory['MemAvailable'].strip().split()[0]) // 1024)
    emit('openai_key_configured', bool(env.get('OPENAI_API_KEY')))
    emit('pdf_enabled', env.get('HOMEWORK_PDF_ENABLED') == 'true')
    emit('backend_uid', run(['docker', 'exec', 'kim-math-backend', 'id', '-u']))
    emit('backend_gid', run(['docker', 'exec', 'kim-math-backend', 'id', '-g']))
    backups = sorted(Path('/home/suhui/backups').glob('kim_math_backup_*.sql.gz'))
    emit('existing_backup_files', len(backups))
    if backups:
        emit('latest_existing_backup_name', backups[-1].name)
        emit('latest_existing_backup_bytes', backups[-1].stat().st_size)
    if not re.fullmatch('[a-f0-9]{40}', source):
        raise RuntimeError('Invalid source revision')
    url = 'https://raw.githubusercontent.com/mkom76/kim-math/' + source + '/docker-compose.prod.yml'
    with urllib.request.urlopen(url, timeout=30) as response:
        compose = response.read()
    with tempfile.TemporaryDirectory(prefix='homework-pdf-preflight-') as temporary:
        path = Path(temporary) / 'compose.yml'
        path.write_bytes(compose)
        run(['docker-compose', '--project-directory', str(ROOT), '--env-file', str(ROOT / '.env'), '-f', str(path), 'config', '--quiet'])
    emit('homework_pdf_preflight', 'passed')


def main():
    operation = os.environ.get('HOMEWORK_PDF_OPERATION', '')
    expected = os.environ.get('HOMEWORK_PDF_EXPECTED_SERVER_SHA', '')
    source = os.environ.get('HOMEWORK_PDF_SOURCE_SHA', '')
    if operation == 'homework-pdf-preflight':
        preflight(expected, source)
    elif operation == 'homework-pdf-migrate':
        migrate(expected, source)
    elif operation == 'homework-pdf-activate':
        activate(expected)
    elif operation == 'homework-pdf-verify':
        verify_runtime(expected)
    elif operation == 'homework-pdf-backup':
        backup_pair(expected)
    else:
        raise RuntimeError('Operation not prepared; no changes performed')


if __name__ == '__main__':
    main()
