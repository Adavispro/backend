from datetime import datetime
from pathlib import Path
import sys
import unittest


BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from ingestion_services.file_ingestion_service.core.cleaner import clean_obj_values
from ingestion_services.file_ingestion_service.core.production_report_parser import (
    derive_lot_number,
    parse_production_report_xls,
)


class CompressionEnhancementsTest(unittest.TestCase):
    def test_derived_lot_is_deterministic_and_preserves_batch(self):
        timestamp = datetime(2026, 9, 30, 12, 2, 17)
        first = derive_lot_number("BATCH-001", "ProductionReport-2026-09-30-12-02-17.xls", timestamp)
        second = derive_lot_number("BATCH-001", "ProductionReport-2026-09-30-12-02-17.xls", timestamp)
        self.assertEqual(first, second)
        self.assertEqual("BATCH-001-LOT-20260930120217", first)

    def test_unavailable_values_are_retained_as_null(self):
        cleaned = clean_obj_values({"na": "NA", "none": None, "blank": "", "zero": 0})
        self.assertEqual({"na": None, "none": None, "blank": None, "zero": 0}, cleaned)

    def test_sample_report_retains_all_compression_sections(self):
        sample = next((BACKEND_DIR / "ingestion_services" / "compression_server").glob("2026-09-*/*.xls"))
        payload = parse_production_report_xls(str(sample))
        details = payload["compression_details"]
        self.assertTrue(payload["meta"]["lotNo"].startswith(payload["meta"]["batchNo"] + "-LOT-"))
        for section in (
            "batchInfo",
            "recipeSettings",
            "pressureData",
            "operationValues",
            "tightness",
            "tabletChecker",
            "tabletCounters",
        ):
            self.assertIn(section, details)


if __name__ == "__main__":
    unittest.main()
