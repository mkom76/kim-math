#!/usr/bin/env python3
"""Owner-dispatched PDF homework operations. Never print credentials or records."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import urllib.request

ROOT = Path('/home/suhui/kim-math')
BACKUPS = Path('/home/suhui/backups/homework-pdf')
FEATURE_TABLES = ('homework_pdf_imports', 'homework_pdf_questions')
MIGRATION = 'migrations/2026-11-13-01-add-homework-pdf-imports.sql'


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
    target = '"$MYSQL_DATABASE"' if database is None else '"$2"'
    args = ['docker', 'exec', container, 'sh', '-c',
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --batch --skip-column-names '
            + target + ' --execute="$1"', 'sh', statement]
    if database is not None:
        args.append(database)
    return run(args)


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
    else:
        raise RuntimeError('Operation not prepared; no changes performed')


if __name__ == '__main__':
    main()
