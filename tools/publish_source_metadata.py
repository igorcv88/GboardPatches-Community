#!/usr/bin/env python3
"""Check/publish/repair the stable Morphe pointer without rebuilding a release."""
import argparse
import base64
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path
from release_bundle import SEMVER, validate_bundle, validate_metadata, version_tuple


def gh_json(*args, payload=None, missing_ok=False):
    command = ["gh", "api", *args]
    if payload is not None:
        command += ["--input", "-"]
    result = subprocess.run(command, input=None if payload is None else json.dumps(payload),
                            text=True, capture_output=True)
    if result.returncode:
        if missing_ok and re.search(r"\(HTTP 404\)", result.stderr):
            return None
        raise RuntimeError(f"GitHub API operation failed ({result.returncode}): {result.stderr.strip()}")
    return json.loads(result.stdout)


def pointer(repository):
    repo = gh_json(f"repos/{repository}")
    branch = repo["default_branch"]
    endpoint = f"repos/{repository}/contents/patches-bundle.json"
    current = gh_json(endpoint, "--method", "GET", "-f", f"ref={branch}", missing_ok=True)
    content = None if current is None else base64.b64decode(current["content"])
    return branch, endpoint, current, content


def stable_versions(repository, content=None):
    result = []
    if content is not None:
        result.append(version_tuple(json.loads(content)["version"]))
    latest = gh_json(f"repos/{repository}/releases/latest", missing_ok=True)
    if latest and SEMVER.fullmatch(latest["tag_name"]) and not latest.get("prerelease"):
        result.append(version_tuple(latest["tag_name"]))
    return result


def preflight(repository, version, allow_equal=False):
    _, _, _, content = pointer(repository)
    candidate = version_tuple(version)
    for stable in stable_versions(repository, content):
        if candidate < stable or candidate == stable and not allow_equal:
            raise ValueError("Refusing a stable version that would replace an equal/newer release")


def publish(repository, manifest):
    content = manifest.read_bytes()
    metadata = validate_metadata(json.loads(content), repository)
    branch, endpoint, current, previous = pointer(repository)
    candidate = version_tuple(metadata["version"])
    for stable in stable_versions(repository, previous):
        if candidate < stable:
            raise ValueError("Stable source must not regress")
    if previous is not None:
        previous_value = json.loads(previous)
        if version_tuple(previous_value["version"]) == candidate:
            if previous_value == metadata:
                print(f"Morphe source already points to {metadata['version']}")
                return False
            raise ValueError("An existing version has different immutable metadata")
    payload = {"branch": branch, "message": f"chore: update Morphe source to {metadata['version']}",
               "content": base64.b64encode(content).decode("ascii")}
    if current is not None:
        payload["sha"] = current["sha"]
    # GitHub's blob SHA compare-and-swap rejects concurrent pointer changes. Never force/retry blindly.
    gh_json(endpoint, "--method", "PUT", payload=payload)
    print(f"Morphe source updated to {metadata['version']}")
    return True


def repair(repository, tag):
    version = ".".join(map(str, version_tuple(tag)))
    if tag != f"v{version}":
        raise ValueError("Repair requires an exact vMAJOR.MINOR.PATCH tag")
    release = gh_json(f"repos/{repository}/releases/tags/{tag}")
    if release["draft"] or release["prerelease"] or release["tag_name"] != tag:
        raise ValueError("Only a published stable release can repair the stable source")
    required = {f"patches-{version}.mpp", "patches-bundle.json", "SHA256SUMS.txt"}
    if not required.issubset({asset["name"] for asset in release["assets"]}):
        raise ValueError("Release is missing the bundle, manifest or checksums")
    # Resolve the remote tag through the local checkout fetched by actions/checkout.
    local_commit = subprocess.check_output(["git", "rev-parse", f"{tag}^{{commit}}"], text=True).strip()
    if not re.fullmatch(r"[0-9a-f]{40}", local_commit):
        raise ValueError("Release tag does not resolve to a commit")
    target = release.get("target_commitish", "")
    if not re.fullmatch(r"[0-9a-f]{40}", target) or target != local_commit:
        raise ValueError("Release target does not match its tag")
    preflight(repository, version, allow_equal=True)
    with tempfile.TemporaryDirectory(prefix="morphe-metadata-repair-") as temp:
        root = Path(temp)
        subprocess.run(["gh", "release", "download", tag, "--repo", repository,
                        "--dir", temp, "--pattern", f"patches-{version}.mpp",
                        "--pattern", "patches-bundle.json", "--pattern", "SHA256SUMS.txt"], check=True)
        verify_checksums(root, version)
        validate_metadata(json.loads((root/"patches-bundle.json").read_bytes()),repository,version)
        validate_bundle(root/f"patches-{version}.mpp",version,repository)
        return publish(repository,root/"patches-bundle.json")


def verify_checksums(root, version):
    expected = {f"patches-{version}.mpp", "patches-bundle.json"}
    observed = {}
    for line in (root/"SHA256SUMS.txt").read_text().splitlines():
        match = re.fullmatch(r"([a-f0-9]{64})  ([^/\\]+)", line)
        if not match or match[2] not in expected or match[2] in observed:
            raise ValueError("Unexpected or duplicate checksum entry")
        observed[match[2]] = match[1]
    if observed.keys() != expected:
        raise ValueError("Release checksum catalog is incomplete")
    for name, checksum in observed.items():
        if hashlib.sha256((root/name).read_bytes()).hexdigest() != checksum:
            raise ValueError("Release asset checksum mismatch")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("preflight", "publish", "repair"):
        child = sub.add_parser(name)
        child.add_argument("repository")
        if name == "preflight":
            child.add_argument("version")
            child.add_argument("--allow-equal", action="store_true")
        elif name == "publish": child.add_argument("manifest", type=Path)
        else: child.add_argument("tag")
    args = parser.parse_args()
    if args.command == "preflight": preflight(args.repository,args.version,args.allow_equal)
    elif args.command == "publish": publish(args.repository,args.manifest)
    else: repair(args.repository,args.tag)


if __name__ == "__main__": main()
