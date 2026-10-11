import json
import os
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
from unittest.mock import MagicMock, Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import ingest_date_run
from core.mongo_loader import MongoCompressionLoader


class EventsOnlyTests(unittest.TestCase):
    def setUp(self):
        self.events = {
            "alarms": [{"alarmId": "ALM-MC081-20261005-1", "batchNo": "B1"}],
            "operations": [{"auditId": "AUD-MC081-20261005-2", "batchNo": "B1"}],
            "logins": [{"loginId": "LOG-MC081-20261005-3"}],
        }
        self.loader = MongoCompressionLoader.__new__(MongoCompressionLoader)
        self.collections = {}

        def collection(name):
            if name not in self.collections:
                item = Mock()
                item.bulk_write.return_value = SimpleNamespace(upserted_count=1, modified_count=0)
                item.count_documents.return_value = 1
                item.update_many.return_value = SimpleNamespace(matched_count=1)
                self.collections[name] = item
            return self.collections[name]

        self.loader.db = MagicMock()
        self.loader.db.__getitem__.side_effect = collection

    def test_all_destinations_verified_and_only_history_fields_updated(self):
        counts = self.loader.repair_event_histories(self.events, ["ProductionReport-test.xls"])
        self.assertEqual(counts, {"alarmsIngested": 1, "auditsIngested": 1, "loginsIngested": 1})
        self.assertEqual(set(self.collections), {
            "iiot_ts_alarm_MC081", "iiot_alarm_events", "iiot_ts_audit_MC081",
            "iiot_batch_audit_trail", "iiot_ts_login_MC081", "login_history",
            "iiot_ts_batch_MC081",
        })
        for name, collection in self.collections.items():
            collection.delete_many.assert_not_called()
            if name != "iiot_ts_batch_MC081":
                operation = collection.bulk_write.call_args.args[0][0]
                self.assertTrue(operation._upsert)
                self.assertEqual(operation._doc["$set"]["equipmentCode"], "MC081")
                self.assertEqual(collection.count_documents.call_args.args[0]["equipmentCode"], "MC081")
        query, update = self.collections["iiot_ts_batch_MC081"].update_many.call_args.args
        self.assertEqual(query, {"compression_details.metadata.sourceFile": {"$in": ["ProductionReport-test.xls"]}})
        self.assertEqual(set(update["$set"]), {
            "compression_details.alarm_history", "compression_details.operation_history",
            "compression_details.login_history",
        })

    def test_shared_write_failure_propagates(self):
        self.loader.db["login_history"].bulk_write.side_effect = RuntimeError("write failed")
        with self.assertRaisesRegex(RuntimeError, "write failed"):
            self.loader.repair_event_histories(self.events, [])

    def test_verification_failure_propagates(self):
        self.loader.db["iiot_alarm_events"].count_documents.return_value = 0
        with self.assertRaisesRegex(RuntimeError, "verification failed"):
            self.loader.repair_event_histories(self.events, [])

    def test_invalid_or_empty_events_do_not_write(self):
        with self.assertLogs("compression.mongo_loader", level="WARNING"):
            counts = self.loader.repair_event_histories(
                {"alarms": [], "operations": [], "logins": []}, ["ProductionReport-test.xls"],
            )
        self.assertEqual(counts, {"alarmsIngested": 0, "auditsIngested": 0, "loginsIngested": 0})
        self.assertFalse(self.collections)
        self.events["alarms"][0]["alarmId"] = "ALM-OTHER-1"
        with self.assertRaisesRegex(ValueError, "Invalid MC081"):
            self.loader.repair_event_histories(self.events, [])
        self.assertFalse(self.collections)

    def test_events_only_skips_reports_fetch_and_checkpoint(self):
        with TemporaryDirectory() as directory:
            source_date = Path(directory) / "2026-10-05"
            source_date.mkdir()
            (source_date / "ProductionReport-test.xls").touch()
            fetcher = Mock(source_path=Path(directory))
            reader = Mock()
            reader.read_sawc_events.return_value = self.events
            loader = Mock()
            loader.repair_event_histories.return_value = {
                "alarmsIngested": 1, "auditsIngested": 1, "loginsIngested": 1,
            }
            with patch.object(ingest_date_run, "parse_production_report_xls") as parser:
                summary = ingest_date_run.ingest_single_date(
                    "2026-10-05",
                    {"output_paths": {"extracted_json_dir": str(Path(directory) / "output")}},
                    fetcher, reader, loader, events_only=True,
                )
            parser.assert_not_called()
            fetcher.fetch_date_files.assert_not_called()
            loader.load_batch_report.assert_not_called()
            loader.update_checkpoint.assert_not_called()
            loader.repair_event_histories.assert_called_once_with(
                self.events, ["ProductionReport-test.xls"],
            )
            self.assertEqual(summary["reportsProcessed"], 0)

    def test_empty_date_exports_json_and_later_date_is_loaded(self):
        with TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            fetcher = Mock(source_path=Path(directory))
            reader = Mock()
            reader.read_sawc_events.side_effect = [
                {"alarms": [], "operations": [], "logins": []}, self.events,
            ]
            for date in ("2026-09-05", "2026-10-05"):
                (Path(directory) / date).mkdir()
                (Path(directory) / date / "ProductionReport-test.xls").touch()
                summary = ingest_date_run.ingest_single_date(
                    date, {"output_paths": {"extracted_json_dir": str(output)}},
                    fetcher, reader, self.loader, events_only=True,
                )
                if date == "2026-09-05":
                    self.assertEqual(summary["alarmsIngested"], 0)
                    self.assertFalse(self.collections)
                    for filename, key in (
                        ("AlarmHistory", "alarms"), ("OperatingHistory", "operations"),
                        ("LogInOutHistory", "loginHistory"),
                    ):
                        data = json.loads((output / date / f"{filename}-{date}.json").read_text())
                        self.assertEqual(data[key], [])
                else:
                    self.assertEqual(summary["alarmsIngested"], 1)
                    self.collections["iiot_alarm_events"].bulk_write.assert_called_once()

    def test_events_only_rejects_truncate_before_database_connection(self):
        with (
            patch.dict(os.environ, {"INGESTION_MODE": "TRUNCATE_AND_LOAD"}),
            patch.object(sys, "argv", ["ingest_date_run.py", "--all-dates", "--events-only"]),
            patch.object(ingest_date_run, "MongoCompressionLoader") as constructor,
        ):
            with self.assertRaises(SystemExit) as error:
                ingest_date_run.main()
            self.assertEqual(error.exception.code, 2)
            constructor.assert_not_called()


if __name__ == "__main__":
    unittest.main()
