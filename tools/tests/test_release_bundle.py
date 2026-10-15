import importlib.util
import sys
from pathlib import Path
import tempfile
import unittest
import zipfile

sys.path.insert(0,str(Path(__file__).parents[1]))
import release_bundle as release


class ReleaseBundleTest(unittest.TestCase):
    def test_auto_patch_uses_numeric_order_and_ignores_theme_tags(self):
        self.assertEqual("3.12.11",release.resolve_version(
            ["v3.12.9","v3.12.10","custom-theme-previews-v99","junk"],"3.12.0"))
        self.assertEqual("3.12.1",release.resolve_version([],"3.12.0"))
        self.assertEqual("4.2.1",release.resolve_version([],"3.12.0",stable="4.2.0"))

    def test_stable_explicit_version_must_move_forward(self):
        self.assertEqual("3.12.1",release.resolve_version([],"3.12.0","v3.12.1"))
        for old in ["v3.12.0","v1.0.1","3.11.99"]:
            with self.assertRaises(ValueError):release.resolve_version([],"3.12.0",old)
        self.assertEqual("1.0.1",release.resolve_version([],"3.12.0","v1.0.1",prerelease=True))

    def test_invalid_tag_cannot_inject_shell_output(self):
        for tag in ["v1.0.1;echo secret","v01.0.1","../../x","1.0","v1.0.1\nx"]:
            with self.assertRaises(ValueError):release.resolve_version([],"3.12.0",tag)

    def test_actual_metadata_contract_is_versioned_and_timezone_free(self):
        actual=release.metadata(release.REPOSITORY,"3.12.1","### Gboard\nEditing buttons")
        self.assertEqual("3.12.1",actual["version"])
        self.assertEqual("",actual["signature_download_url"])
        self.assertRegex(actual["created_at"],r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d$")
        self.assertEqual(actual,release.validate_metadata(actual,release.REPOSITORY,"3.12.1"))
        for field,invalid in [("created_at","2026-10-15T20:39:00+00:00"),
                              ("download_url","https://github.com/upstream/other/releases/download/v3.12.1/p.mpp"),
                              ("version","3.12.0")]:
            copy={**actual,field:invalid}
            with self.assertRaises(ValueError):release.validate_metadata(copy,release.REPOSITORY,"3.12.1")
        with self.assertRaises(ValueError):release.metadata("other/repo/../../x","1.0.1","")

    def test_eight_byte_fake_dex_is_never_a_valid_bundle(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle=Path(tmp)/"patches.mpp"
            for entries in [{"classes.dex":b"dex\n035\0","extensions/gboard-patches.rve":b"dex\n035\0"},
                            {"classes.dex":b"dex\n035\0"},{"classes.dex":b"bad","extensions/gboard-patches.rve":b"dex\n"}]:
                with zipfile.ZipFile(bundle,"w") as archive:
                    for name,value in entries.items():archive.writestr(name,value)
                with self.assertRaises(ValueError):release.validate_bundle(bundle)

    def test_dex_checksum_corruption_and_truncation_are_rejected(self):
        for data in [b"",b"dex\n035\0"+b"\0"*104,b"not dex"*100]:
            with self.assertRaises(ValueError):release.validate_dex(data)

    def test_jar_manifest_continuation_lines_are_parsed(self):
        self.assertEqual({"Manifest-Version":"1.0","Source":"https://github.com/igorcv88/GboardPatches-Community"},
            release.jar_attributes(b"Manifest-Version: 1.0\r\nSource: https://github.com/igorcv88/\r\n GboardPatches-Community\r\n\r\n"))


if __name__=="__main__":unittest.main()
