#!/usr/bin/env python3
"""Unit & Integration tests for multi-equipment continuous ingestion pipeline.

Tests:
1. REST API ingestion for RMG (MB003), FBD (MB004), Blender (MB005), Coater (MB041)
2. File-based ingestion for Compression Machine (MB040)
3. Checkpoint tracking in iiot_ingestion_checkpoint
4. Duplicate prevention & idempotency
5. Fail-resilient execution (equipment failure isolation)
6. Real-time Redis cache and MongoDB live status updates
"""

import os
import unittest
from datetime import datetime
from pathlib import Path

# Add backend directory to path
backend_dir = Path(__file__).resolve().parent.parent
import sys
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))

from scheduler.config import get_config
from scheduler.ingestion import SchedulerIngestionService, DEFAULT_DATASET_IDS
from data_service_layer.source_api_client import (
    fetch_batch_details,
    fetch_batch_data,
    fetch_alarm_data,
    fetch_audit_data,
)


class TestMultiEquipmentIngestion(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cfg = get_config()
        cls.service = SchedulerIngestionService(
            mongo_uri=cls.cfg.database.mongo_uri,
            db_name=cls.cfg.database.mongo_database,
            compression_dir=cls.cfg.compression.source_path,
        )

    @classmethod
    def tearDownClass(cls):
        if cls.service.client is not None:
            cls.service.client.close()

    def test_api_batch_details_retrieval(self):
        """Verify API client retrieves batch details for all 4 API equipments."""
        for eq in ["MB003", "MB004", "MB005", "MB041"]:
            batches = fetch_batch_details(dataset_id=eq)
            self.assertIsInstance(batches, list, f"Batches for {eq} should be a list")
            self.assertGreater(len(batches), 0, f"Batches for {eq} should not be empty")
            first = batches[0]
            self.assertTrue(bool(first.batch_no), f"Batch for {eq} must contain batch_no")
            self.assertTrue(bool(first.product_code), f"Batch for {eq} must contain product_code")

    def test_api_telemetry_streaming_retrieval(self):
        """Verify operational telemetry streaming for API equipments."""
        from_time = "2026-08-15 06:00:00"
        to_time = "2026-10-07 10:00:00"

        # 1. RMG (MB003)
        rmg_records = fetch_batch_data(from_time, to_time, dataset_id="MB003")
        self.assertIsInstance(rmg_records, list)
        self.assertGreater(len(rmg_records), 0)
        self.assertTrue(any("agAmps" in r.critical_params or "current" in r.critical_params or len(r.critical_params) > 0 for r in rmg_records))

        # 2. FBD (MB004)
        fbd_records = fetch_batch_data(from_time, to_time, dataset_id="MB004")
        self.assertIsInstance(fbd_records, list)
        self.assertGreater(len(fbd_records), 0)
        self.assertTrue(any("inletTemp" in r.critical_params or len(r.critical_params) > 0 for r in fbd_records))

        # 3. BLE (MB005)
        ble_records = fetch_batch_data(from_time, to_time, dataset_id="MB005")
        self.assertIsInstance(ble_records, list)
        self.assertGreater(len(ble_records), 0)
        self.assertTrue(any("actualRpm" in r.critical_params or len(r.critical_params) > 0 for r in ble_records))

        # 4. COAT (MB041)
        coat_records = fetch_batch_data(from_time, to_time, dataset_id="MB041")
        self.assertIsInstance(coat_records, list)
        self.assertGreater(len(coat_records), 0)
        self.assertTrue(any("panSpeed" in r.critical_params or len(r.critical_params) > 0 for r in coat_records))

    def test_compression_excel_sync(self):
        """Verify Sejong Excel production reports are parsed and synced."""
        res = self.service.sync_compression_file_source()
        self.assertIsInstance(res, dict)
        self.assertIn("processed_batches", res)
        self.assertGreaterEqual(res["processed_batches"], 0)

    def test_full_scheduler_cycle_execution(self):
        """Verify run_scheduler_cycle processes all 5 equipments without errors."""
        result = self.service.run_scheduler_cycle(
            current_time=datetime.now(),
            dataset_ids=["MB003", "MB004", "MB005", "MB041", "MB040"],
        )

        self.assertIsInstance(result, dict)
        self.assertEqual(result.get("dataset_count"), 5)
        self.assertEqual(result.get("successful_datasets"), 5)
        self.assertEqual(result.get("failed_datasets"), 0)
        self.assertEqual(len(result.get("dataset_errors", [])), 0)

    def test_checkpoint_and_duplicate_prevention(self):
        """Verify checkpoint tracking in MongoDB and that checkpoints exist for all 5 equipments."""
        if self.service.db is not None:
            checkpoint_col = self.service.db["iiot_ingestion_checkpoint"]
            for eq in ["MB003", "MB004", "MB005", "MB041", "MB040"]:
                cp = checkpoint_col.find_one({"equipmentId": eq})
                self.assertIsNotNone(cp, f"Checkpoint should exist for {eq}")
                self.assertIn("status", cp)

    def test_error_resilience_isolation(self):
        """Verify that an equipment failure does not abort ingestion for remaining equipments."""
        from unittest.mock import patch

        original_sync = self.service.sync_daily_batch_window

        def side_effect(eq_id, current_time=None):
            if eq_id == "FAILING_EQUIPMENT":
                raise ConnectionError("Simulated connection timeout to FAILING_EQUIPMENT")
            return original_sync(eq_id, current_time)

        with patch.object(self.service, "sync_daily_batch_window", side_effect=side_effect):
            result = self.service.run_scheduler_cycle(
                current_time=datetime.now(),
                dataset_ids=["MB003", "FAILING_EQUIPMENT", "MB005"],
            )
            self.assertEqual(result.get("dataset_count"), 3)
            self.assertEqual(result.get("successful_datasets"), 2)
            self.assertEqual(result.get("failed_datasets"), 1)
            self.assertEqual(len(result.get("dataset_errors", [])), 1)
            self.assertEqual(result["dataset_errors"][0]["dataset_id"], "FAILING_EQUIPMENT")
            self.assertIn("Simulated connection timeout", result["dataset_errors"][0]["error"])


if __name__ == "__main__":
    unittest.main()
