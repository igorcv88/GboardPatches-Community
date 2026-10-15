#!/usr/bin/env python3
"""Version and metadata rules for the fork's one release workflow (no network)."""
import argparse
import datetime
import json
import re
import zipfile
from pathlib import Path

SEMVER = re.compile(r"v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\Z")


def version_tuple(value):
    match = SEMVER.fullmatch(value.strip())
    if not match:
        raise ValueError(f"Expected a stable semantic version: {value!r}")
    return tuple(map(int, match.groups()))


def resolve_version(tags, baseline, explicit=""):
    if explicit:
        return ".".join(map(str, version_tuple(explicit)))
    versions = [version_tuple(baseline)]
    versions.extend(version_tuple(tag) for tag in tags if SEMVER.fullmatch(tag.strip()))
    major, minor, patch = max(versions)
    return f"{major}.{minor}.{patch + 1}"


def metadata(repository, version, description):
    version_tuple(version)
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Expected owner/repository")
    return {
        "created_at": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "description": description,
        "download_url": f"https://github.com/{repository}/releases/download/v{version}/patches-{version}.mpp",
        "signature_download_url": "",
        "version": version,
    }


def validate_bundle(path):
    with zipfile.ZipFile(path) as archive:
        bad = archive.testzip()
        if bad:
            raise ValueError(f"Corrupt bundle entry: {bad}")
        names = archive.namelist()
        if "classes.dex" not in names:
            raise ValueError("Bundle has no Android DEX")
        if "extensions/gboard-patches.rve" not in names:
            raise ValueError("Bundle has no runtime extension")
        if archive.read("classes.dex")[:4] != b"dex\n":
            raise ValueError("Bundle DEX header is invalid")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    resolve = sub.add_parser("resolve")
    resolve.add_argument("--tags", type=Path, required=True)
    resolve.add_argument("--baseline", required=True)
    resolve.add_argument("--tag", default="")
    make = sub.add_parser("metadata")
    make.add_argument("--repository", required=True)
    make.add_argument("--version", required=True)
    make.add_argument("--notes", type=Path, required=True)
    make.add_argument("--output", type=Path, required=True)
    check = sub.add_parser("validate")
    check.add_argument("bundle", type=Path)
    args = parser.parse_args()
    if args.command == "resolve":
        version = resolve_version(args.tags.read_text().splitlines(), args.baseline, args.tag)
        print(f"VERSION={version}\nTAG=v{version}")
    elif args.command == "metadata":
        args.output.write_text(json.dumps(metadata(args.repository, args.version,
                                                   args.notes.read_text()), indent=2) + "\n")
    else:
        validate_bundle(args.bundle)


if __name__ == "__main__":
    main()
