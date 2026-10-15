#!/usr/bin/env python3
"""Offline version, Morphe metadata and structural bundle validation rules."""
import argparse
import datetime
import hashlib
import json
import re
import struct
import zipfile
import zlib
from pathlib import Path

SEMVER = re.compile(r"v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\Z")
REPOSITORY = "igorcv88/GboardPatches-Community"


def version_tuple(value):
    match = SEMVER.fullmatch(value.strip())
    if not match:
        raise ValueError(f"Expected a stable semantic version: {value!r}")
    return tuple(map(int, match.groups()))


def resolve_version(tags, baseline, explicit="", stable=None, prerelease=False):
    floor = version_tuple(stable or baseline)
    if explicit:
        candidate = version_tuple(explicit)
        if not prerelease and candidate <= floor:
            raise ValueError("A stable release must be newer than the current stable version")
        return ".".join(map(str, candidate))
    versions = [version_tuple(baseline), floor]
    versions.extend(version_tuple(tag) for tag in tags if SEMVER.fullmatch(tag.strip()))
    major, minor, patch = max(versions)
    return f"{major}.{minor}.{patch + 1}"


def metadata(repository, version, description):
    version_tuple(version)
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Expected owner/repository")
    return {
        # UTC wall-clock fields, without an offset: MorpheAsset uses kotlinx.datetime.LocalDateTime.
        "created_at": datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None).isoformat(timespec="seconds"),
        "description": description,
        "download_url": f"https://github.com/{repository}/releases/download/v{version}/patches-{version}.mpp",
        "signature_download_url": "",
        "version": version,
    }


def validate_metadata(value, repository, expected_version=None):
    version = value["version"]
    canonical_version = ".".join(map(str, version_tuple(version)))
    if version != canonical_version:
        raise ValueError("Manifest version must not have a v prefix")
    if expected_version is not None and version != expected_version:
        raise ValueError("Manifest version does not match release tag")
    canonical = metadata(repository, version, value["description"])
    if value["download_url"] != canonical["download_url"]:
        raise ValueError("Manifest download must name this repository's versioned release asset")
    created = value["created_at"]
    if not isinstance(created, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}", created):
        raise ValueError("created_at must be a timezone-free Morphe LocalDateTime")
    datetime.datetime.fromisoformat(created)
    if not isinstance(value["description"], str) or not value["description"].strip():
        raise ValueError("Manifest description is missing")
    if value.get("signature_download_url", ""):
        raise ValueError("No detached bundle-signature scheme is configured")
    return value


def validate_dex(data):
    if len(data) < 112 or not re.fullmatch(b"dex\\n0(?:3[5-9]|40)\x00", data[:8]):
        raise ValueError("Invalid or unsupported DEX header")
    if struct.unpack_from("<III", data, 32) != (len(data), 112, 0x12345678):
        raise ValueError("DEX file size/header size/endian tag mismatch")
    if data[12:32] != hashlib.sha1(data[32:]).digest():
        raise ValueError("DEX SHA-1 mismatch")
    if struct.unpack_from("<I", data, 8)[0] != zlib.adler32(data[12:]) & 0xffffffff:
        raise ValueError("DEX Adler32 mismatch")
    for position, width in [(56,4),(64,4),(72,12),(80,8),(88,8),(96,32)]:
        count, offset = struct.unpack_from("<II", data, position)
        if count and (offset < 112 or offset % 4 or offset + count * width > len(data)):
            raise ValueError("DEX table is out of bounds")
    if struct.unpack_from("<I", data, 96)[0] == 0:
        raise ValueError("DEX class catalog is empty")
    size, offset = struct.unpack_from("<II", data, 104)
    if offset < 112 or offset + size != len(data):
        raise ValueError("DEX data section is invalid")
    map_offset = struct.unpack_from("<I", data, 52)[0]
    if map_offset < offset or map_offset + 4 > len(data):
        raise ValueError("DEX map is missing")
    count = struct.unpack_from("<I", data, map_offset)[0]
    if count == 0 or map_offset + 4 + count * 12 > len(data):
        raise ValueError("DEX map is invalid")


def jar_attributes(raw):
    lines = []
    for line in raw.decode("utf-8").splitlines():
        if not line:
            break
        if line.startswith(" ") and lines:
            lines[-1] += line[1:]
        else:
            lines.append(line)
    return dict(line.split(": ", 1) for line in lines)


def validate_bundle(path, expected_version=None, repository=REPOSITORY):
    """Structural prerequisite, not a replacement for the real Morphe import smoke task."""
    with zipfile.ZipFile(path) as archive:
        bad = archive.testzip()
        if bad:
            raise ValueError(f"Corrupt bundle entry: {bad}")
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Bundle contains duplicate entries")
        for entry in ("classes.dex", "extensions/gboard-patches.rve"):
            if entry not in names:
                raise ValueError(f"Bundle missing {entry}")
            validate_dex(archive.read(entry))
        attributes = jar_attributes(archive.read("META-INF/MANIFEST.MF"))
        version_tuple(attributes["Version"])
        if expected_version is not None and attributes["Version"] != expected_version:
            raise ValueError("Bundle version does not match the release version")
        url = f"https://github.com/{repository}"
        for key, value in {"Source":url,"Website":url,"Contact":url+"/issues"}.items():
            if attributes.get(key) != value:
                raise ValueError(f"Bundle {key} does not identify the community source")
        if not attributes.get("Patcher-Version"):
            raise ValueError("Bundle patcher compatibility version is missing")
        if "dev/jason/gboardpatches/patches/gboard/registry/GboardPatchRegistryKt.class" not in names:
            raise ValueError("Bundle patch catalog is missing")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    resolve = sub.add_parser("resolve")
    resolve.add_argument("--tags", type=Path, required=True)
    resolve.add_argument("--baseline", required=True)
    resolve.add_argument("--stable")
    resolve.add_argument("--tag", default="")
    resolve.add_argument("--prerelease", action="store_true")
    make = sub.add_parser("metadata")
    make.add_argument("--repository", required=True)
    make.add_argument("--version", required=True)
    make.add_argument("--notes", type=Path, required=True)
    make.add_argument("--output", type=Path, required=True)
    check = sub.add_parser("validate")
    check.add_argument("bundle", type=Path)
    check.add_argument("--version")
    check.add_argument("--repository", default=REPOSITORY)
    args = parser.parse_args()
    if args.command == "resolve":
        version = resolve_version(args.tags.read_text().splitlines(), args.baseline,
                                  args.tag, args.stable, args.prerelease)
        print(f"VERSION={version}\nTAG=v{version}")
    elif args.command == "metadata":
        result = metadata(args.repository, args.version, args.notes.read_text())
        validate_metadata(result, args.repository, args.version)
        args.output.write_text(json.dumps(result, indent=2) + "\n")
    else:
        validate_bundle(args.bundle, args.version, args.repository)


if __name__ == "__main__":
    main()
