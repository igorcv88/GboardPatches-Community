import base64
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).parents[1]))
import publish_source_metadata as source
from release_bundle import REPOSITORY,metadata


class SourceMetadataTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.file=Path(self.temp.name)/"manifest.json"
        self.new=metadata(REPOSITORY,"3.12.1","### Gboard\nRelease")
        self.file.write_text(json.dumps(self.new))
        self.current=metadata(REPOSITORY,"3.12.0","Old")
        self.latest="v3.12.0";self.puts=[]
        self.release={"draft":False,"prerelease":False,"tag_name":"v3.12.1",
                      "target_commitish":"a"*40,"assets":[{"name":x} for x in
                        ["patches-3.12.1.mpp","patches-bundle.json","SHA256SUMS.txt"]]}
        self.api=patch.object(source,"gh_json",side_effect=self.call);self.api.start();self.addCleanup(self.api.stop)

    def call(self,*args,payload=None,missing_ok=False):
        if args[0]==f"repos/{REPOSITORY}":return {"default_branch":"main"}
        if args[0].endswith("/releases/latest"):return None if self.latest is None else {"tag_name":self.latest,"prerelease":False}
        if "/releases/tags/" in args[0]:return self.release
        if payload is not None:
            self.puts.append(payload);self.current=json.loads(base64.b64decode(payload["content"]))
            return {"content":{"sha":"next"}}
        if self.current is None:return None
        return {"sha":"current-blob","content":base64.b64encode(json.dumps(self.current).encode()).decode()}

    def test_newer_pointer_uses_blob_lease_and_default_branch(self):
        self.assertTrue(source.publish(REPOSITORY,self.file))
        self.assertEqual("current-blob",self.puts[0]["sha"]);self.assertEqual("main",self.puts[0]["branch"])

    def test_missing_initial_manifest_is_created_without_fake_release_pointer(self):
        self.current=None;self.latest=None
        source.publish(REPOSITORY,self.file);self.assertNotIn("sha",self.puts[0])

    def test_repair_retry_of_identical_metadata_is_idempotent(self):
        self.current=self.new;self.latest="v3.12.1"
        self.assertFalse(source.publish(REPOSITORY,self.file));self.assertEqual([],self.puts)

    def test_out_of_order_publish_cannot_regress_pointer_or_latest(self):
        for current,latest in [("3.12.2","v3.12.1"),("3.12.0","v3.12.2")]:
            self.current=metadata(REPOSITORY,current,"existing");self.latest=latest
            with self.assertRaises(ValueError):source.publish(REPOSITORY,self.file)
        self.assertEqual([],self.puts)

    def test_equal_version_with_changed_metadata_is_not_silently_overwritten(self):
        self.current={**self.new,"description":"different"}
        with self.assertRaises(ValueError):source.publish(REPOSITORY,self.file)
        self.assertEqual([],self.puts)

    def test_preflight_blocks_stable_duplicate_and_out_of_order_versions(self):
        for version in ["1.0.1","3.12.0"]:
            with self.assertRaises(ValueError):source.preflight(REPOSITORY,version)
        source.preflight(REPOSITORY,"3.12.1")
        source.preflight(REPOSITORY,"3.12.0",allow_equal=True)

    def test_checksum_verification_rejects_modified_bytes_and_path_traversal(self):
        root=Path(self.temp.name)
        (root/"patches-3.12.1.mpp").write_bytes(b"bundle")
        (root/"patches-bundle.json").write_bytes(b"manifest")
        catalog="\n".join(hashlib.sha256((root/name).read_bytes()).hexdigest()+"  "+name
                    for name in ["patches-3.12.1.mpp","patches-bundle.json"])+"\n"
        (root/"SHA256SUMS.txt").write_text(catalog);source.verify_checksums(root,"3.12.1")
        (root/"patches-bundle.json").write_bytes(b"modified")
        with self.assertRaises(ValueError):source.verify_checksums(root,"3.12.1")
        (root/"SHA256SUMS.txt").write_text("0"*64+"  ../file\n")
        with self.assertRaises(ValueError):source.verify_checksums(root,"3.12.1")

    def test_metadata_only_repair_downloads_and_validates_without_compiling(self):
        self.latest="v3.12.1"
        def download(command,check):
            self.assertEqual(["gh","release","download"],command[:3])
            root=Path(command[command.index("--dir")+1])
            files={"patches-3.12.1.mpp":b"bundle", "patches-bundle.json":json.dumps(self.new).encode()}
            for name,value in files.items():(root/name).write_bytes(value)
            (root/"SHA256SUMS.txt").write_text("".join(hashlib.sha256(data).hexdigest()+"  "+name+"\n" for name,data in files.items()))
        with patch.object(source.subprocess,"check_output",return_value="a"*40), \
             patch.object(source.subprocess,"run",side_effect=download), \
             patch.object(source,"validate_bundle") as validator:
            self.assertTrue(source.repair(REPOSITORY,"v3.12.1"));validator.assert_called_once()
        self.assertEqual(1,len(self.puts))

    def test_repair_requires_the_pinned_release_commit_to_match_the_tag(self):
        for target in ["main","b"*40]:
            self.release["target_commitish"]=target
            with patch.object(source.subprocess,"check_output",return_value="a"*40):
                with self.assertRaises(ValueError):source.repair(REPOSITORY,"v3.12.1")
        self.assertEqual([],self.puts)

    def test_draft_prerelease_missing_asset_and_mismatched_tag_are_rejected(self):
        for change in [{"draft":True},{"prerelease":True},{"assets":[]},{"tag_name":"v1.0.1"}]:
            old=self.release;self.release={**old,**change}
            with self.assertRaises(ValueError):source.repair(REPOSITORY,"v3.12.1")
            self.release=old
        self.assertEqual([],self.puts)


if __name__=="__main__":unittest.main()
