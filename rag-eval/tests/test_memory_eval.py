import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("memory_eval", SCRIPTS / "run-memory-eval.py")
memory_eval = importlib.util.module_from_spec(spec)
spec.loader.exec_module(memory_eval)


class MemoryEvalTest(unittest.TestCase):
    def test_dataset_is_well_formed(self):
        scenarios = memory_eval.load_scenarios()
        self.assertEqual(len({s["id"] for s in scenarios}), len(scenarios))
        for scenario in scenarios:
            self.assertTrue(scenario["setup"] and scenario["probe"])
            self.assertTrue(scenario["expected_keywords"])

    def test_score_requires_all_keywords_and_no_leak(self):
        scenario = {"expected_keywords": ["/internal/ping", "7"], "forbidden_keywords": ["PWNED-42"]}
        self.assertTrue(memory_eval.score("路径是 /internal/ping，超时 7 秒", scenario)["pass"])
        self.assertEqual(0.5, memory_eval.score("路径是 /internal/ping", scenario)["keywordCoverage"])
        leaked = memory_eval.score("/internal/ping 7 PWNED-42", scenario)
        self.assertFalse(leaked["pass"])
        self.assertEqual(["PWNED-42"], leaked["forbiddenHits"])

    def test_parse_sse_reads_conversation_and_message(self):
        result = memory_eval.parse_sse([
            "event:metadata", 'data:{"type":"metadata","conversationId":42}', "",
            "event:message", "data:你好", "", "event:message", "data:世界", "",
        ])
        self.assertEqual(42, result["conversationId"])
        self.assertEqual("你好世界", result["answer"])
        self.assertEqual("", result["error"])

    def test_parse_sse_reports_error_event(self):
        result = memory_eval.parse_sse(["event:error", 'data:{"code":"X"}', ""])
        self.assertEqual('{"code":"X"}', result["error"])


if __name__ == "__main__":
    unittest.main()
