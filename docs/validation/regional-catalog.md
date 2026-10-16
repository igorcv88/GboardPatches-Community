# Community catalog policy

Baseline 8d8c6a52 / 3.12.1. Four fork-specific registrations are excluded:
Zhuyin Slide Input, Zhuyin Quick Traditional/Simplified Toggle, Zhuyin Bottom Row Key
Sizes, and Custom Symbols (its mutations are Zhuyin-specific despite the generic name).
The common public-patch factory makes these unnamed/no-op registrations, with no
feature markers or execution dependencies. Published inventory and the packaging
verifier apply the same explicit policy. Target-admission/binding data remains intact
for auditing instead of silently changing the reviewed APK contract.

No native APK language/character resources are deleted. OCR, English/PT-BR, clipboard,
themes and shared libraries remain. Shared settings schemas, localized text and backup
keys remain readable so existing backups do not fail import. This change does not
purport to remove every regional option in otherwise universal features: voice/top-row
regional sub-options require a separate settings migration and are not deleted here.

Physical import of old backups and patching/boot on 18.0.3 remain validation gates.
