import gzip
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace

spec = importlib.util.spec_from_file_location('pdf_production', Path(__file__).parents[1] / 'homework-pdf-production.py')
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)


class ProductionGuardTests(unittest.TestCase):
    def test_pdf_replay_budget_stops_before_paid_generation_on_unknown_or_large_input(self):
        for count in (None,{}, {'input_tokens':0},{'input_tokens':100001},{'input_tokens':True},{'input_tokens':'100'}):
            self.assertFalse(ops.pdf_replay_budget(count))
        self.assertTrue(ops.pdf_replay_budget({'input_tokens':100000}))

    def test_pdf_replay_preserves_prompt_and_schema_and_uses_private_bounded_request(self):
        source=(Path(__file__).parents[2]/'src/main/java/com/example/service/homeworkpdf/OpenAiHomeworkPdfAi.java').read_text(encoding='utf-8')
        body=ops.homework_replay_body(b'%PDF-test',[{'id':'test-id','path':['a','b','c','d']}],source)
        self.assertFalse(body['store'])
        self.assertEqual(body['max_output_tokens'],24000)
        self.assertEqual(body['service_tier'],'default')
        self.assertIn('한국어 수학 숙제 PDF',body['instructions'])
        self.assertTrue(body['text']['format']['strict'])
        self.assertIn('test-id',body['input'][0]['content'][1]['text'])
        self.assertEqual(ops.pdf_replay_validation({'questions':[{'number':'1','text':'sample','regions':[{'page':11,'x':0,'y':0,'width':2,'height':1}]}]},10),{'REGION_PAGE':1,'REGION_BOUNDS':1})

    def test_diagnosis_reads_only_metadata_and_never_prints_raw_logs_or_key(self):
        sample = 'FAILED\tANALYSIS_FAILED\t3\t1\t1000\tgpt-6.1-sol\t2026-11-14 10:00:00\t150'
        raw = SimpleNamespace(stdout=b'private-pdf-content private-key', stderr=b'TimeoutException')
        with patch.object(ops, 'verify_target', return_value={'OPENAI_API_KEY':'private-key'}), patch.object(ops, 'database_guard'), \
                patch.object(ops, 'sql', return_value=sample) as sql, patch.object(ops, 'inspect', side_effect=['{"OOMKilled":false}', '0']), \
                patch.object(ops.subprocess, 'run', return_value=raw) as command, patch.object(ops.urllib.request, 'urlopen') as model, patch.object(ops, 'emit') as emit:
            model.return_value.__enter__.return_value.status = 200
            ops.diagnose_pdf('a' * 40)
            self.assertTrue(all(call.args[0].startswith('SELECT ') for call in sql.call_args_list))
            self.assertTrue(all(call.args[0][:2] == ['docker','logs'] for call in command.call_args_list))
            self.assertEqual(model.call_args.args[0].get_method(), 'GET')
            self.assertIsNone(model.call_args.args[0].data)
            output = str(emit.call_args_list)
            self.assertNotIn('private-key', output)
            self.assertNotIn('private-pdf-content', output)
            self.assertIn('pdf_diagnosis', output)

    def test_exam_migration_is_pinned_to_the_reviewed_sql(self):
        source = Path(__file__).parents[2] / ops.EXAM_MIGRATION
        with patch.object(ops, 'source_file', return_value=source.read_bytes()):
            self.assertIn(b'CREATE TABLE exam_pdf_imports', ops.exam_migration_source('a' * 40))
        with patch.object(ops, 'source_file', return_value=b'DROP TABLE tests;'):
            with self.assertRaisesRegex(RuntimeError, 'checksum mismatch'):
                ops.exam_migration_source('a' * 40)

    def test_existing_exam_tables_stop_before_backup_or_ddl(self):
        with patch.object(ops, 'verify_runtime'), patch.object(ops, 'exam_feature_tables', return_value=['exam_pdf_imports']), \
                patch.object(ops, 'backup_pair') as backup, patch.object(ops, 'sql') as sql:
            with self.assertRaisesRegex(RuntimeError, 'do not rerun'):
                ops.migrate_exam('a' * 40, 'b' * 40)
            backup.assert_not_called()
            sql.assert_not_called()

    def test_failed_exam_rehearsal_never_executes_production_ddl(self):
        with patch.object(ops, 'verify_runtime'), patch.object(ops, 'exam_feature_tables', return_value=[]), \
                patch.object(ops, 'sql', side_effect=['0\t0', '0', '0', '0', '0', '0', '0']) as sql, \
                patch.object(ops.shutil, 'disk_usage') as disk, patch.object(ops, 'exam_migration_source', return_value=b'CREATE TABLE exam_pdf_imports(id INT);'), \
                patch.object(ops, 'schema_signature'), patch.object(ops, 'backup_pair', return_value={'databaseBackup':'/backup/database.sql.gz','originalsBackup':'/backup/pdf.tar.gz'}), \
                patch.object(ops, 'restore_check', side_effect=RuntimeError('rehearsal failed')), patch.object(ops, 'atomic_file') as write:
            disk.return_value.free = 2 * 1024 ** 3
            with self.assertRaisesRegex(RuntimeError, 'rehearsal failed'):
                ops.migrate_exam('a' * 40, 'b' * 40)
            self.assertTrue(all(call.args[0].startswith('SELECT ') for call in sql.call_args_list))
            write.assert_not_called()

    def test_exam_schema_verification_rejects_partial_tables_and_missing_constraints(self):
        with patch.object(ops, 'sql', return_value='exam_pdf_imports\t23'):
            with self.assertRaisesRegex(RuntimeError, 'columns do not match'):
                ops.verify_exam_feature_schema()
        columns = 'exam_pdf_imports\t23\nexam_pdf_questions\t17'
        constraints = 'CHECK\t4\nFOREIGN KEY\t6\nPRIMARY KEY\t2\nUNIQUE\t4'
        with patch.object(ops, 'sql', side_effect=[columns, constraints]):
            ops.verify_exam_feature_schema()
        with patch.object(ops, 'sql', side_effect=[columns, 'PRIMARY KEY\t2']):
            with self.assertRaisesRegex(RuntimeError, 'constraints do not match'):
                ops.verify_exam_feature_schema()

    def test_paired_backup_includes_exam_originals_when_schema_exists(self):
        homework = 'a' * 36 + '\t' + 'b' * 64
        exam = 'c' * 36 + '\t' + 'd' * 64
        with patch.object(ops, 'sql', side_effect=['1', homework + '\n' + exam]) as sql:
            self.assertEqual(len(ops.registered_originals()), 2)
            self.assertIn('UNION ALL SELECT storage_key,file_sha256 FROM exam_pdf_imports', sql.call_args.args[0])
        with patch.object(ops, 'sql', side_effect=['0', homework]) as sql:
            self.assertEqual(len(ops.registered_originals()), 1)
            self.assertNotIn('exam_pdf_imports', sql.call_args.args[0])

    def test_duplicate_original_keys_are_rejected_before_archiving(self):
        row = 'a' * 36 + '\t' + 'b' * 64
        with patch.object(ops, 'sql', side_effect=['1', row + '\n' + row]):
            with self.assertRaisesRegex(RuntimeError, 'Invalid registered original metadata'):
                ops.registered_originals()

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

    def test_old_frontend_revision_is_not_reported_as_a_completed_deployment(self):
        expected = 'a' * 40
        with patch.object(ops, 'verify_target', return_value={}), patch.object(ops, 'database_guard'), \
                patch.object(ops, 'verify_feature_schema'), patch.object(ops, 'inspect', side_effect=[expected, 'old-frontend']):
            with self.assertRaisesRegex(RuntimeError, 'kim-math-frontend image'):
                ops.verify_runtime(expected)

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
