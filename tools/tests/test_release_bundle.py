import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("release_bundle", Path(__file__).parents[1] / "release_bundle.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseBundleTest(unittest.TestCase):
    def test_auto_patch_uses_numeric_order_and_ignores_theme_tags(self):
        self.assertEqual("3.12.11", release.resolve_version(
            ["v3.12.9", "v3.12.10", "custom-theme-previews-v99", "junk"], "3.12.0"))
        self.assertEqual("3.12.1", release.resolve_version([], "3.12.0"))

    def test_explicit_version_and_injection_rejection(self):
        self.assertEqual("1.0.1", release.resolve_version([], "3.12.0", "v1.0.1"))
        for tag in ["v1.0.1;echo secret", "v01.0.1", "../../x", "1.0", "v1.0.1\nx"]:
            with self.assertRaises(ValueError):
                release.resolve_version([], "3.12.0", tag)

    def test_metadata_is_versioned_and_points_only_to_fork(self):
        actual = release.metadata("igorcv88/GboardPatches-Community", "3.12.1", "### Gboard\nEditing buttons")
        self.assertEqual("3.12.1", actual["version"])
        self.assertEqual("", actual["signature_download_url"])
        self.assertEqual("https://github.com/igorcv88/GboardPatches-Community/releases/download/v3.12.1/patches-3.12.1.mpp",
                         actual["download_url"])
        with self.assertRaises(ValueError):
            release.metadata("other/repo/../../x", "1.0.1", "")

    def test_bundle_requires_dex_extension_and_valid_archive(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / "patches.mpp"
            for entries, valid in [({"classes.dex": b"dex\n035\0", "extensions/gboard-patches.rve": b"dex\n035\0"}, True),
                                   ({"classes.dex": b"dex\n035\0"}, False),
                                   ({"classes.dex": b"bad", "extensions/gboard-patches.rve": b"dex\n"}, False)]:
                with zipfile.ZipFile(bundle, "w") as archive:
                    for name, value in entries.items():
                        archive.writestr(name, value)
                if valid:
                    release.validate_bundle(bundle)
                else:
                    with self.assertRaises(ValueError):
                        release.validate_bundle(bundle)


if __name__ == "__main__":
    unittest.main()
