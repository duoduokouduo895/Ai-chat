import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "prepare_external_config.py"
spec = importlib.util.spec_from_file_location("prepare_external_config", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PrepareExternalConfigTest(unittest.TestCase):
    def test_ordered_exports_keep_last_value_without_relying_on_trailing_newlines(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary)
            for name in module.SOURCE_FILES:
                (source / name).write_text("# empty\n", encoding="utf-8")
            (source / module.SOURCE_FILES[0]).write_text("shared=first", encoding="utf-8")
            (source / module.SOURCE_FILES[1]).write_text("shared=second\nnext=ok\n", encoding="utf-8")

            values, duplicates = module.read_exports(source)

            self.assertEqual("second", values["shared"])
            self.assertEqual("ok", values["next"])
            self.assertEqual({"shared"}, duplicates)

    def test_properties_escapes_and_unicode_round_trip(self):
        parsed = module.parse_line("message=\\u4e2d\\u6587\\:\\ value\\nnext")
        self.assertEqual(("message", "中文: value\nnext"), parsed)
        self.assertEqual(parsed[1], module.unescape(module.escape_property(parsed[1])))

    def test_camel_case_secret_is_flagged(self):
        for key in ("aliyun.oss.accessKeySecret", "aliyun.oss.access-key-id",
                    "langchain4j.open-ai.api-key", "provider.api_key"):
            with self.subTest(key=key):
                self.assertIsNotNone(module.SENSITIVE_PARTS.search(key))

    def test_unicode_surrogate_pair_is_decoded_for_utf8_secret_file(self):
        self.assertEqual("密钥😀", module.unescape(r"\u5bc6\u94a5\ud83d\ude00"))

    def test_multiline_secret_is_rejected_before_output_exists(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "next-config"
            with self.assertRaisesRegex(ValueError, "multiline secret"):
                module.write_config(output, {"jwt.secret": "one\ntwo"}, {"jwt.secret"}, None, None)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
