import unittest
from typing import Any

from core.mongo_loader import MongoCompressionLoader


class FakeCollection:
    def __init__(self) -> None:
        self.documents: list[dict[str, Any]] = []

    def find_one(self, query: dict[str, Any]) -> dict[str, Any] | None:
        return next(
            (document for document in self.documents if self._matches(document, query)),
            None,
        )

    def update_one(
        self,
        query: dict[str, Any],
        update: dict[str, Any],
        upsert: bool = False,
    ) -> None:
        document = self.find_one(query)
        if document is None:
            if not upsert:
                return
            document = {}
            self.documents.append(document)
            for key, value in query.items():
                if not isinstance(value, dict):
                    document[key] = value
            for key, value in update.get("$setOnInsert", {}).items():
                document[key] = value

        document.update(update.get("$set", {}))
        for key in update.get("$unset", {}):
            document.pop(key, None)

    def insert_one(self, document: dict[str, Any]) -> None:
        self.documents.append(document)

    @classmethod
    def _matches(cls, document: dict[str, Any], query: dict[str, Any]) -> bool:
        for key, value in query.items():
            if key == "$or":
                if not any(cls._matches(document, branch) for branch in value):
                    return False
            elif document.get(key) != value:
                return False
        return True


class FakeDatabase:
    def __init__(self) -> None:
        self.collections: dict[str, FakeCollection] = {}

    def __getitem__(self, name: str) -> FakeCollection:
        return self.collections.setdefault(name, FakeCollection())


class MongoCompressionLoaderSummaryTests(unittest.TestCase):
    def setUp(self) -> None:
        self.loader = MongoCompressionLoader.__new__(MongoCompressionLoader)
        self.loader.db = FakeDatabase()
        self.loader.master_sync = None

    def load_report(self, lot_no: str, source_file: str) -> None:
        self.loader.load_batch_report(
            {
                "observedAt": "2026-09-01T08:00:00+00:00",
                "meta": {
                    "batchNo": "AGOA26018",
                    "lotNo": lot_no,
                    "derivedLotNo": lot_no,
                    "productName": "Test Product",
                    "productCode": "TEST",
                },
                "metrics": {},
                "compression_details": {
                    "metadata": {"sourceFile": source_file},
                    "recipeSettings": {},
                    "tabletCounters": {},
                    "pressureData": {},
                    "operationValues": {},
                },
            }
        )

    def test_repeated_batch_reports_create_one_summary_per_lot(self) -> None:
        self.load_report("Lot-01", "report-1.xls")
        self.load_report("Lot-02", "report-2.xls")

        summaries = self.loader.db["iiot_batch_summary"].documents

        self.assertEqual(2, len(summaries))
        self.assertEqual(
            {("AGOA26018", "Lot-01"), ("AGOA26018", "Lot-02")},
            {(summary["batchNo"], summary["lotNo"]) for summary in summaries},
        )
        self.assertTrue(
            all(
                summary["stages"][0]["lotNo"] == summary["lotNo"]
                for summary in summaries
            )
        )

    def test_reingesting_same_lot_updates_its_summary(self) -> None:
        self.load_report("Lot-01", "report-1.xls")
        self.load_report("Lot-02", "report-2.xls")
        self.load_report("Lot-01", "report-1.xls")

        summaries = self.loader.db["iiot_batch_summary"].documents

        self.assertEqual(2, len(summaries))
        self.assertEqual(
            {"Lot-01", "Lot-02"},
            {summary["lotNo"] for summary in summaries},
        )


if __name__ == "__main__":
    unittest.main()
