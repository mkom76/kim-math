import gzip
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import unittest


REPO_ROOT = Path(__file__).resolve().parents[2]


class ServerBackupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'scripts').mkdir()
        shutil.copy(REPO_ROOT / 'scripts/backup-mysql.sh', self.root / 'scripts/backup-mysql.sh')
        self.backups = self.root / 'backups'
        self.backups.mkdir()
        self.log = self.root / 'commands.log'
        self.config = {
            'MYSQL_ROOT_PASSWORD': 'test-password',
            'MYSQL_DATABASE': 'test_database',
            'MYSQL_BACKUP_DIR': str(self.backups),
        }
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        mock = '#!' + sys.executable + '\n' + '''
import os
from pathlib import Path
import sys

tool = Path(sys.argv[0]).name
args = sys.argv[1:]
failure = os.environ.get('MOCK_FAILURE', '')
if tool == 'docker' and args[0] == 'exec':
    event = 'dump'
elif tool == 'docker-compose':
    event = 'compose ' + ' '.join(args[2:])
else:
    event = tool + (' ' + args[0] if args else '')
with open(os.environ['MOCK_LOG'], 'a') as log:
    log.write(event + '\\n')
if failure == event:
    if event == 'dump':
        print('-- incomplete SQL dump')
    print('Simulated failure: ' + event, file=sys.stderr)
    sys.exit(1)
if event == 'dump' and failure != 'empty':
    print('CREATE TABLE example (id INT);')
    print('INSERT INTO example VALUES (42);')
elif event == 'docker login':
    sys.stdin.read()
'''
        for name in ('docker', 'docker-compose', 'git'):
            path = self.bin / name
            path.write_text(mock)
            path.chmod(0o755)
        self.env = {
            **os.environ,
            'PATH': str(self.bin) + os.pathsep + os.environ['PATH'],
            'MYSQL_BACKUP_ENV_FILE': str(self.root / '.env'),
            'MOCK_LOG': str(self.log),
            'MOCK_FAILURE': '',
        }

    def run_backup(self, *, deploy=False, failure=''):
        (self.root / '.env').write_text(''.join(
            name + '=' + shlex.quote(value) + '\n'
            for name, value in self.config.items()
        ))
        if deploy:
            workflow = (REPO_ROOT / '.github/workflows/deploy.yml').read_text()
            remote = workflow.split('          script: |\n', 1)[1]
            script = '\n'.join(line[12:] for line in remote.splitlines())
            script = script.replace('cd /home/suhui/kim-math', 'cd ' + shlex.quote(str(self.root)))
            script = re.sub(r'\$\{\{.*?\}\}', 'test-value', script)
            args = ['bash', '-c', script]
        else:
            args = ['bash', str(self.root / 'scripts/backup-mysql.sh')]
        return subprocess.run(
            args, env={**self.env, 'MOCK_FAILURE': failure}, cwd=self.root,
            capture_output=True, text=True, timeout=15,
        )

    def events(self):
        return self.log.read_text().splitlines() if self.log.exists() else []

    def archives(self):
        return list(self.backups.glob('*.sql.gz'))

    def test_successful_local_backup_precedes_container_changes(self):
        result = self.run_backup(deploy=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.events(), [
            'git pull', 'dump', 'docker login', 'compose pull',
            'compose up -d', 'docker image',
        ])
        archive, = self.archives()
        self.assertEqual(gzip.decompress(archive.read_bytes()),
                         b'CREATE TABLE example (id INT);\nINSERT INTO example VALUES (42);\n')
        self.assertEqual(archive.stat().st_mode & 0o777, 0o600)

    def test_failed_or_empty_dump_stops_deployment_and_removes_partial_file(self):
        for failure in ('dump', 'empty'):
            with self.subTest(failure=failure):
                self.log.unlink(missing_ok=True)
                result = self.run_backup(deploy=True, failure=failure)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.events(), ['git pull', 'dump'])
                self.assertEqual(list(self.backups.iterdir()), [])

    def test_invalid_database_config_stops_before_dump(self):
        del self.config['MYSQL_ROOT_PASSWORD']
        result = self.run_backup(deploy=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.events(), ['git pull'])
        self.assertIn('MYSQL_ROOT_PASSWORD is required', result.stderr)

    def test_unavailable_backup_directory_stops_deployment(self):
        blocked = self.root / 'not-a-directory'
        blocked.write_text('occupied')
        self.config['MYSQL_BACKUP_DIR'] = str(blocked)
        result = self.run_backup(deploy=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.events(), ['git pull'])

    def test_compression_failure_or_corrupt_archive_stops_deployment(self):
        real_gzip = shutil.which('gzip')
        for corrupt in (False, True):
            with self.subTest(corrupt=corrupt):
                self.log.unlink(missing_ok=True)
                proxy = self.bin / 'gzip'
                proxy.write_text('#!/bin/bash\n'
                                 'if [[ "${1:-}" == "-cd" ]]; then\n'
                                 '  exec ' + shlex.quote(real_gzip) + ' "$@"\n'
                                 'fi\ncat >/dev/null\n'
                                 + ('printf broken-archive\n' if corrupt else 'exit 1\n'))
                proxy.chmod(0o755)
                result = self.run_backup(deploy=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.events(), ['git pull', 'dump'])
                self.assertEqual(list(self.backups.iterdir()), [])

    def test_expired_backups_are_deleted_only_after_success(self):
        old = self.backups / 'kim_math_backup_20000101_000000.sql.gz'
        old.write_bytes(b'old backup')
        expired = time.time() - 30.5 * 86400
        os.utime(old, (expired, expired))
        self.assertNotEqual(self.run_backup(failure='dump').returncode, 0)
        self.assertTrue(old.exists())
        result = self.run_backup()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(old.exists())

    def test_repeated_backups_have_distinct_names(self):
        for _ in range(2):
            result = self.run_backup()
            self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(self.archives()), 2)

if __name__ == '__main__':
    unittest.main()
