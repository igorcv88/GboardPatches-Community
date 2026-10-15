#!/usr/bin/env python3
"""Update only the default branch's Morphe pointer, after a stable release is public."""
import argparse
import base64
import json
import subprocess
from pathlib import Path


def gh_json(*args, payload=None):
    command = ["gh", "api", *args]
    if payload is not None:
        command += ["--input", "-"]
    result = subprocess.run(command, input=None if payload is None else json.dumps(payload),
                            text=True, capture_output=True, check=True)
    return json.loads(result.stdout)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository")
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    # CAS against the file blob preserves unrelated main-branch changes.
    repo = gh_json(f"repos/{args.repository}")
    branch = repo["default_branch"]
    endpoint = f"repos/{args.repository}/contents/patches-bundle.json"
    current = gh_json(endpoint, "--method", "GET", "-f", f"ref={branch}")
    content = args.manifest.read_bytes()
    metadata = json.loads(content)
    payload = {"branch": branch, "sha": current["sha"],
               "message": f"chore: update Morphe source to {metadata['version']}",
               "content": base64.b64encode(content).decode("ascii")}
    gh_json(endpoint, "--method", "PUT", payload=payload)
    print(f"Morphe source updated to {metadata['version']}")


if __name__ == "__main__":
    main()
