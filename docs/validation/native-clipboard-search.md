# Clipboard search candidate — original 18.0.3

Baseline main 8d8c6a52 / 3.12.1. ClipboardKeyboard.e/h and Lfjk.M lifecycle hooks
attach/detach a search button and EditText to the native header. The existing worker,
matcher and presentation model filter full text, skipping sensitive items. Native
adapter indices, model identities, header objects, database and callbacks are retained;
nonmatching cards are visually suppressed. Grid columns restore when query closes.
The original RecyclerView instance/id/listeners/layout parameters are restored.
The two original body layouts were inspected: their RecyclerView constraints point
to the original header; no sibling references the RecyclerView id.

Input is isolated by an internal panel keyboard editing only the query Editable.
The implementation never calls the host InputConnection or replaces its provider.
It includes underscore/punctuation for the Termux regression string. This is not a
binding to the Gboard native alphabet keyboard: full keyboard/language/accessibility
parity and the suggested Loqa/provider route remain open. Back handling on the input
is tested; system Back dispatch and floating/landscape need physical verification.

Robolectric component tests cover filtering beyond preview length, sensitive exclusion,
live removal, original model/order/count retention, close restoration and stale owners.
They do not prove the host editor receives no characters on an actual S25 Ultra.
Use Termux and type `segredo_de_teste`, select one result, verify exactly the requested
paste, then Back and app/rotation transitions. Exercise native pin/delete/selection and
empty-state spacing on the real RecyclerView; hidden rows are a projection, not a new
adapter, and native item decorations still need visual validation.

A host-only 500 × 200,000 UTF-16 negative-query scan (same JDK 21, heap 256–512 MiB,
three warmups and five measured scans) compared actual baseline/candidate matchers:

| Metric | Baseline | ASCII fast path |
|---|---:|---:|
| p50 | 445.3 ms | 217.7 ms |
| max of five (reported p95) | 475.5 ms | 251.8 ms |

The fast path bypasses Unicode decomposition for ASCII chunks while preserving the
Unicode fallback. This small host sample is not Android latency, a robust p95 estimate,
or evidence of smooth scrolling. Cancellation/projection and memory need phone traces.
