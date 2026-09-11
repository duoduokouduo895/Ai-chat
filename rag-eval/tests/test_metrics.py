import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("retrieval", SCRIPTS / "run-retrieval-eval.py")
retrieval = importlib.util.module_from_spec(spec)
spec.loader.exec_module(retrieval)


class RetrievalMetricsTest(unittest.TestCase):
    def evaluate(self, contents, markers=("A", "B"), error=""):
        return retrieval.evaluate({"id": "test", "type": "multi", "answerable": bool(markers),
                                   "required_evidence_markers": list(markers)},
                                  retrieval.CONFIGS[0], [{"content": text} for text in contents], 1, 2, error)

    def test_multiple_markers_in_one_chunk(self):
        row = self.evaluate(["A B"])
        self.assertEqual(1, row["precisionAtK"])
        self.assertEqual(1, row["evidenceCoverage"])

    def test_repeated_evidence_does_not_inflate_coverage(self):
        row = self.evaluate(["A", "A", "irrelevant"])
        self.assertEqual(0.6667, row["precisionAtK"])
        self.assertEqual(0.5, row["evidenceCoverage"])

    def test_duplicate_required_markers_are_deduplicated(self):
        self.assertEqual(1, self.evaluate(["A"], ("A", "A"))["evidenceCoverage"])

    def test_empty_and_failed_results_are_distinct(self):
        self.assertTrue(self.evaluate([], ())["hitAtK"])
        self.assertIsNone(self.evaluate([], (), "unavailable")["hitAtK"])
        self.assertIsNone(self.evaluate([], (), "unavailable")["precisionAtK"])

    def test_failures_excluded_from_effectiveness(self):
        rows = [self.evaluate(["A B"]), self.evaluate([], error="unavailable")]
        summary = retrieval.summarize(rows)[0]
        self.assertEqual(1, summary["errors"])
        self.assertEqual(1, summary["precisionAtK"])

    def test_all_effectiveness_ratios_remain_bounded(self):
        rows = [
            self.evaluate(["A B"]),
            self.evaluate(["A", "A", "irrelevant"]),
            self.evaluate([], ()),
            self.evaluate([], error="unavailable"),
        ]
        ratio_fields = ("precisionAtK", "recallAtK", "mrr", "evidenceCoverage")
        for row in rows:
            for field in ratio_fields:
                if row[field] is not None:
                    self.assertGreaterEqual(row[field], 0)
                    self.assertLessEqual(row[field], 1)


if __name__ == "__main__":
    unittest.main()
