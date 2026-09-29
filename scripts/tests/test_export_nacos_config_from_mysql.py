import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import export_nacos_config_from_mysql as module


class ExportNacosConfigTest(unittest.TestCase):
    def test_runtime_selector_rejects_unsafe_sql_syntax(self):
        with tempfile.TemporaryDirectory() as temporary:
            runtime = Path(temporary) / "runtime.env"
            runtime.write_text("NACOS_NAMESPACE=tenant'unsafe\n", encoding="utf-8")
            with patch.object(module, "ENV_PATH", runtime):
                with self.assertRaisesRegex(ValueError, "unsupported runtime setting syntax"):
                    module.runtime_value("NACOS_NAMESPACE")

    def test_export_rejects_multiple_matching_rows_without_content_in_error(self):
        with patch.object(module, "mysql_hex", return_value=b"612D6B65793D78\n612D6B65793D79"):
            with self.assertRaisesRegex(ValueError, "exactly one nonempty config row") as error:
                module.exported_contents("jwt-config.properties", "DEFAULT_GROUP", "tenant")
            self.assertNotIn("612D", str(error.exception))


if __name__ == "__main__":
    unittest.main()
