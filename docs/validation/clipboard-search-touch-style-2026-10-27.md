# Search touch region, card corners and header spacing

Base: main f90d3f4, after merged PR #28. Target: Gboard 18.0.3.954559732,
Galaxy S25 Ultra / Android 17. PR #29.

## Diagnosis before implementation

Confirmed by source inspection: apply() only unions the panel in REGION mode.
For CONTENT it changes visibleTopInsets, which does not change the touchable area;
a panel above contentTopInsets remains outside that area. VISIBLE previously widens
the full window width above the keyboard, including space beside a narrow panel.
FRAME already includes the full native frame. The slot also did not explicitly
schedule a new insets traversal after the transparent surface became anchored.

Not confirmed on hardware: which native mode Gboard uses during search, and whether
an ancestor intercepts touches after they enter the window. A screen recording cannot
supply those values. We do not claim a measured CONTENT mode from the video.

## Changes

Reconstruct FRAME/CONTENT/VISIBLE from window dimensions and the corresponding
original top inset, or copy REGION exactly (including holes). Union only the panel,
clipped to the window, and select REGION. Leave contentTopInsets unchanged. Request
layout and insets after anchoring. No replacement of search, filtering, typing
routing, input providers, native keyboard children or paste handling.

Corner coverage uses half of the rendered interior alpha instead of absolute 0x80.
A translucent alpha 0x66 rounded card therefore retains its radius. Search remains
one column. Header positioning copies the physical edge gap between the first two
visible native controls; with only one peer, it uses its native layout margins.
The existing disc/lens renderer and native touch bridge are unchanged.

## Temporary device probe

Enable before opening search:

```sh
su -c 'setprop log.tag.ClipboardSearchTouch DEBUG'
```

Open search, type, tap a result; reopen, scroll, tap back. Collect:

```sh
logcat -d -v time -s ClipboardSearchTouch:D
```

Disable afterward:

```sh
su -c 'setprop log.tag.ClipboardSearchTouch WARN'
```

The probe records native and final touch mode, content/visible insets, region,
anchored panel bounds, panel/list/back DOWN/MOVE/UP/CANCEL, result/back clicks and
list scroll callbacks. It never logs query text, clip contents, clip IDs or QWERTY
key events. Default builds leave the probe disabled.

If native/union geometry is correct but no panel DOWN appears, inspect Samsung
window dispatch and sibling drawing order. If panel DOWN arrives without list/back
DOWN, inspect parent interception. If list/back events arrive without callbacks,
inspect cancellation and gesture coordinates before changing routing.

## Validation boundaries

New Robolectric tests cover all four native modes, exact region preservation,
nonrectangular regions and outside-panel exclusions; actual dispatched panel pick,
vertical drag, back and outside touches; arrow callback through session cleanup and
native return; traversal request after anchoring; translucent wide-card radius;
scaled native gap and independent search hit handling. Existing provider, filtering,
Unicode, lifecycle and native-return regressions remain in the CI suite.

21 Python tooling tests pass locally. Local Gradle is blocked before compilation by
unavailable network access to services.gradle.org. The existing GitHub workflow runs
extension/calculator tests, patches:check, buildAndroid and verifyReleaseBundleImport.
Its final result must be checked separately; adding a test does not prove it passes.

On-device acceptance remains required: measured native mode; physical tap/paste;
finger scrolling; arrow restoring native clipboard; outside-panel app interaction;
QWERTY typing exclusively in the internal field; repeated open/close; gray disc and
native spacing on the actual theme. Robolectric does not emulate the Samsung IME
window manager or the proprietary Gboard touch graph.
