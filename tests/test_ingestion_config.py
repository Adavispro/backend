#!/usr/bin/env python3
"""Unit tests for IIoT multi-equipment ingestion configuration loader."""

import os
import unittest
from pathlib import Path

# Add backend directory to path
backend_dir = Path(__file__).resolve().parent.parent
import sys
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))

from scheduler.config import (
    load_config,
    get_config,
    interpolate_env_vars,
    IngestionSettings,
)


class TestIngestionConfig(unittest.TestCase):
    def test_interpolate_env_vars(self):
        os.environ["TEST_FOO"] = "hello"
        text = "val is ${TEST_FOO} and fallback is ${TEST_BAR:-world}"
        interpolated = interpolate_env_vars(text)
        self.assertEqual(interpolated, "val is hello and fallback is world")

    def test_load_config_defaults(self):
        cfg = load_config()
        self.assertIsInstance(cfg, IngestionSettings)
        self.assertIn("MB003", cfg.get_active_equipment_ids())
        self.assertIn("MB004", cfg.get_active_equipment_ids())
        self.assertIn("MB005", cfg.get_active_equipment_ids())
        self.assertIn("MB041", cfg.get_active_equipment_ids())
        self.assertIn("MB040", cfg.get_active_equipment_ids())
        self.assertEqual(len(cfg.equipments), 5)

    def test_equipment_details(self):
        cfg = load_config()
        # 1. RMG
        rmg = cfg.get_equipment("MB003")
        self.assertNotNull = self.assertIsNotNone
        self.assertIsNotNone(rmg)
        self.assertEqual(rmg.asset_id, "10094")
        self.assertEqual(rmg.equipment_type, "RMG")
        self.assertEqual(rmg.source_type, "API")

        # 2. FBD
        fbd = cfg.get_equipment("MB004")
        self.assertIsNotNone(fbd)
        self.assertEqual(fbd.asset_id, "10110")
        self.assertEqual(fbd.equipment_type, "FBD")

        # 3. Blender
        ble = cfg.get_equipment("MB005")
        self.assertIsNotNone(ble)
        self.assertEqual(ble.asset_id, "10095")
        self.assertEqual(ble.equipment_type, "BLE")

        # 4. Coater
        coat = cfg.get_equipment("MB041")
        self.assertIsNotNone(coat)
        self.assertEqual(coat.asset_id, "10141")
        self.assertEqual(coat.equipment_type, "COAT")

        # 5. Compression
        comp = cfg.get_equipment("MB040")
        self.assertIsNotNone(comp)
        self.assertEqual(comp.asset_id, "10040")
        self.assertEqual(comp.source_type, "EXCEL")

    def test_mdb_decision_gate(self):
        cfg = load_config()
        # MDB_REQUIRED must be False because Excel contains 100% of telemetry
        self.assertFalse(cfg.compression.mdb_required)
        self.assertEqual(cfg.compression.source_type, "EXCEL")

    def test_schedule_settings(self):
        cfg = load_config()
        self.assertEqual(cfg.schedule.interval_minutes, 15)
        self.assertIn(cfg.execution_mode, ["ONCE", "CONTINUOUS"])

    def test_api_full_url(self):
        cfg = load_config()
        full_url = cfg.api.full_url
        self.assertTrue(full_url.startswith("http"))
        self.assertTrue(full_url.endswith("/Dataset"))


if __name__ == "__main__":
    unittest.main()
