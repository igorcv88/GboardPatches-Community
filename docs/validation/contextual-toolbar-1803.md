# Contextual toolbar 18.0.3

Baseline: 8d8c6a52 / 3.12.1. Original APK SHA-256:
3099262121a201dcb2ae7e185bee6b2057287a5aeffae4be0e30f537e505e5b3.

The existing left-button lookup was scoped to Leso.r (candidate container), while
0x7f0b053e is a sibling in the actual header. Resolve it from the common root, verify
controller/candidate roots match, and look up inherited SoftKeyView methods. Cache
reflection handles and the editing toggle, and avoid redrawing an unchanged mode.
An old Leso clear callback cannot remove the current owner's arrow. Native suppression
of candidates while favorites are visible does not clear the contextual mode.

Architecture: retain native same-row favorites/candidates toggle for this candidate.
The native AccessPointsBar declares suppressedSiblingId=0x7f0b158d; its manager owns
visibility and layout transitions. Moving it into a second row would break that
contract without additional version-specific resource/manager changes. This change
uses Lmln.M to expose native favorites and retains native order/overflow. It never
calls Lmlh.D during active candidates. It DOES NOT implement simultaneous rows.

Robolectric covers common-root binding, inherited methods and out-of-order owner
clear callbacks. Physical comparison of same-row versus a second row, native favorite
clicks, languages, editor switches, floating/paisage and accessibility is still a gate.
No device responsiveness claim is supported by the host test.
