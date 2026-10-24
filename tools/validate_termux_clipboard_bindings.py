#!/usr/bin/env python3
"""Optional exact-APK ABI check. Usage: python ...py original.apk (requires androguard)."""
import hashlib
import sys
import zipfile

from loguru import logger
logger.remove()
from androguard.core.dex import DEX

EXPECTED_SHA256 = "3099262121a201dcb2ae7e185bee6b2057287a5aeffae4be0e30f537e505e5b3"
# Every obfuscated method called by the production adapter, plus the patched entry/exit.
METHODS = {
    "Lfif;": {
        ("x", "()Z"), ("l", "()V"),
        ("y", "(Landroid/content/Context;Lfih;ZIZLppe;)Landroid/view/View;"),
    },
    "Lfih;": {("i", "()Ljava/lang/String;"), ("m", "()Z")},
    "Lqju;": {("<init>", "()V"), ("a", "()Lqjv;"), ("b", "(Lqjw;)V"),
              ("d", "(Ljava/lang/String;)V"), ("e", "(Ljava/util/List;)V")},
    "Lqjr;": {("a", "(Lqjv;Lpfz;)V")},
    "Lqjq;": {("c", "(Lqjw;Z)V")},
    "Lqqw;": {("e", "()Z")},
    "Lnpc;": {("u", "(Ljava/lang/String;Ljava/lang/String;Landroid/view/inputmethod/EditorInfo;)Z")},
}
FIELDS = {
    "Lfif;": {("m", "Landroid/view/inputmethod/EditorInfo;"), ("k", "Lvvw;"), ("o", "Z"), ("l", "Loru;")},
    "Lfih;": {("e", "Lj$/time/Instant;")},
    "Lppe;": {("a", "Lppe;")}, "Lpfz;": {("c", "Lpfz;")}, "Lqjw;": {("c", "Lqjw;")},
}


def main(path):
    with open(path, "rb") as source:
        assert hashlib.file_digest(source, "sha256").hexdigest() == EXPECTED_SHA256, "Unexpected APK"
    found = {}
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if name.endswith(".dex"):
                for cls in DEX(archive.read(name)).get_classes():
                    if cls.get_name() in METHODS.keys() | FIELDS.keys():
                        found[cls.get_name()] = cls
    for name, expected in METHODS.items():
        actual = {(m.get_name(), m.get_descriptor().replace(" ", "")) for m in found[name].get_methods()}
        assert expected <= actual, (name, expected - actual)
    for name, expected in FIELDS.items():
        actual = {(f.get_name(), f.get_descriptor()) for f in found[name].get_fields()}
        assert expected <= actual, (name, expected - actual)
    creation = next(m for m in found["Lfif;"].get_methods() if m.get_name() == "x")
    assert creation.get_code().get_registers_size() == 13
    assert next(creation.get_instructions()).get_name() == "iget-object"
    print("PASS: exact APK hash, all Termux clipboard native bindings, and x() register shape")


if __name__ == "__main__":
    main(sys.argv[1])
