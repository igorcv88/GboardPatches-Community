# Device follow-up after PR #30 merge (Galaxy S25 Ultra / Android 17)

The installed 18.0.3 APK confirms that `Loup.onComputeInsets(Insets)` has a
patched entry and both patched exits. However, after the results panel becomes
visible, the previous log only shows `insets-enter ... shown=true` and
`touch-region-not-computed-after-retries`; it never shows the native/union
region or any panel touch. The entry/exit object was transferred in a static
slot, which is vulnerable to a nested calculation overwriting the value.

The follow-up sends the untouched `p1` object directly to each exit hook,
keeping the framework's native region and adding only the bounded visible
search panel. A deduplicated `insets-exit` diagnostic distinguishes failure of
the guard from failure of the invocation. Nothing logs clipboard contents.

The stock Gboard Undo successfully reverses a paste but the custom Access Point
does not. As a compatibility fallback after bounded journal/transaction paths
fail, the custom button now sends Ctrl+Z to the host editor (and Ctrl+Shift+Z
for Redo). This uses the editor's keyboard history handler where available,
not an unsupported `performContextMenuAction(undo)` context action. Because
`sendKeyEvent` success only acknowledges delivery, **device validation is
required** to prove an actual text change.

## One-shot Termux capture — waits for Enter

Paste the entire single command, wait at its prompt, test search and Undo,
return to Termux and only then press Enter. It never clears system logs.

```sh
bash -c 'su -c "setprop log.tag.ClipboardSearchTouch DEBUG"; printf "\nAGORA TESTE:\n1. Busque clips, arraste, toque num resultado e na seta.\n2. Cole texto e toque no Undo CUSTOMIZADO; teste Redo.\n3. Volte ao Termux e pressione Enter.\n\n"; read -r -p "Pressione Enter SOMENTE apos testar: " _ < /dev/tty; printf "\n=== LOGS DE BUSCA E UNDO ===\n"; su -c "logcat -d -b main -v time -s ClipboardSearchTouch:D GboardHistory:I GboardPatches:W" | grep -E "ClipboardSearchTouch|GboardHistory|Clipboard native search" | tail -160; su -c "setprop log.tag.ClipboardSearchTouch WARN"'
```

Expected when search is shown: `insets-exit open=true valid=true visible=true`,
`native mode=`, `union mode=`, `touch-region-computed-for-panel`, and genuine
`panel/list/back action=0` in response to touch. If insets-exit succeeds but
the UI remains click-through, investigate SurfaceFlinger/InputDispatcher and
the window's applied touchable region rather than widening the target blindly.

For Undo, `GboardHistory action=UNDO route=host-shortcut accepted=true` only
confirms `InputConnection` accepted a key event; verify that the pasted text
actually disappeared and Redo restored it. If not, collect a device-level
event trace of the stock Undo dispatcher: a custom button must use Gboard's
real native Undo path, not repeat unsupported editor commands.
