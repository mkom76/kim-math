import gzip
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('pdf_production', Path(__file__).parents[1] / 'homework-pdf-production.py')
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)


class ProductionGuardTests(unittest.TestCase):
    def test_wrong_database_fingerprint_stops_operation(self):
        with patch.object(ops, 'sql', return_value='kim_math\t8.0.46\twrong-server'):
            with self.assertRaisesRegex(RuntimeError, 'target does not match'):
                ops.database_guard()

    def test_migration_content_is_pinned(self):
        with patch.object(ops, 'source_file', return_value=b'DROP DATABASE kim_math;'):
            with self.assertRaisesRegex(RuntimeError, 'checksum mismatch'):
                ops.migration_source('a' * 40)

    def test_scratch_names_never_accept_an_application_database_name(self):
        for identifier in ('kim_math', '../kim_math', 'a' * 23, 'z' * 24):
            with self.assertRaises(RuntimeError):
                ops.scratch_names(identifier)
        database, user = ops.scratch_names('a' * 24)
        self.assertNotEqual(database, ops.DATABASE)
        self.assertLessEqual(len(user), 32)

    def test_restore_rejects_database_switches_and_incomplete_backups(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'database.sql.gz'
            for data in (b'USE kim_math;\n-- Dump completed on today\n', b'CREATE TABLE t(id INT);\n'):
                with gzip.open(path, 'wb') as file:
                    file.write(data)
                with self.assertRaises(RuntimeError):
                    ops.validate_dump(path)
            with gzip.open(path, 'wb') as file:
                file.write(b'CREATE TABLE t(id INT);\n-- Dump completed on today\n')
            ops.validate_dump(path)

    def test_setting_update_preserves_unrelated_secrets_and_removes_duplicate_flags(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(ops, 'ROOT', Path(temporary)):
            path = Path(temporary) / '.env'
            path.write_text('OPENAI_API_KEY=unit-test-only\nHOMEWORK_PDF_ENABLED=false\nexport HOMEWORK_PDF_ENABLED=true\nOTHER=keep\n')
            ops.update_settings({'HOMEWORK_PDF_ENABLED': 'false'})
            result = path.read_text()
            self.assertEqual(result.count('HOMEWORK_PDF_ENABLED='), 1)
            self.assertIn('OPENAI_API_KEY=unit-test-only\n', result)
            self.assertIn('OTHER=keep\n', result)
            with self.assertRaises(RuntimeError):
                ops.update_settings({'HOMEWORK_PDF_ENABLED': 'true\nOTHER=changed'})

    def test_existing_feature_tables_stop_before_backup_or_any_ddl(self):
        with patch.object(ops, 'verify_target'), patch.object(ops, 'database_guard'), \
                patch.object(ops, 'feature_tables', return_value=['homework_pdf_imports']), \
                patch.object(ops, 'backup_directory') as backup, patch.object(ops, 'sql') as sql:
            with self.assertRaisesRegex(RuntimeError, 'do not rerun'):
                ops.migrate('a' * 40, 'b' * 40)
            backup.assert_not_called()
            sql.assert_not_called()

    def test_partial_schema_is_rejected(self):
        with patch.object(ops, 'sql', return_value='homework_pdf_imports\t22'):
            with self.assertRaisesRegex(RuntimeError, 'columns do not match'):
                ops.verify_feature_schema()

    def test_failed_restore_cleans_only_its_own_schema_and_user(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'database.sql.gz'
            with gzip.open(path, 'wb') as file:
                file.write(b'-- Dump completed on today\n')
            with patch.object(ops.secrets, 'token_hex', side_effect=['a' * 24, 'b' * 48]), \
                    patch.object(ops, 'sql', side_effect=['1', '0', '0', '', '']) as sql, \
                    patch.object(ops.subprocess, 'Popen', side_effect=RuntimeError('restore unavailable')):
                with self.assertRaisesRegex(RuntimeError, 'restore unavailable'):
                    ops.restore_check(path, b'CREATE TABLE homework_pdf_imports(id INT);')
                cleanup = sql.call_args.args[0]
                self.assertIn('DROP DATABASE IF EXISTS `km_pdf_verify_' + 'a' * 24 + '`', cleanup)
                self.assertNotIn('`kim_math`', cleanup)
                self.assertIn("DROP USER IF EXISTS 'pdfv_" + 'a' * 24 + "'@'localhost'", cleanup)


if __name__ == '__main__':
    unittest.main()
