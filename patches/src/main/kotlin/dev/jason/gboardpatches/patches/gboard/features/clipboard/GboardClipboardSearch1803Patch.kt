package dev.jason.gboardpatches.patches.gboard.features.clipboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

/** Reviewed 18.0.3 InputMethodService owner (same target as the editing lifecycle). */
private const val INPUT_METHOD_SERVICE_OWNER = "Loup;"
// The receiver is LatinIME -> Leqy -> Loup. Calling the Loup implementation is not
// guaranteed if a more derived class overrides the callback on a compatible APK.
private val INSETS_DISPATCH_OWNERS = listOf(
    "Lcom/google/android/apps/inputmethod/latin/LatinIME;", "Leqy;", INPUT_METHOD_SERVICE_OWNER,
)

internal val gboardClipboardSearch1803Patch = bytecodePatch(
    description = "Clipboard search on the native alphabet keyboard with results above it.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardClipboardPinned1803Patch, gboardTermuxClipboardDiagnosticsPatch)
    execute {
        findMutableMethodOrThrow(INPUT_METHOD_SERVICE_OWNER, "onStartInputView", "V",
            listOf("Landroid/view/inputmethod/EditorInfo;", "Z"))
            .applyVoidExitLifecycleDelegate(RuntimeCallId.CLIPBOARD_NATIVE_SEARCH_INPUT_STARTED, "p0 .. p0")
        // InputView's Ljqy interceptor can consume the event before any SoftKeyboardView.
        // Handle the search gesture before that interceptor, in the input viewport coordinates.
        val touch = findMutableMethodOrThrow("Lcom/google/android/libraries/inputmethod/inputview/InputView;",
            "dispatchTouchEvent", "Z", listOf("Landroid/view/MotionEvent;"))
        val touchCall = RuntimeCallId.CLIPBOARD_SEARCH_TOUCH
        check(touch.implementation!!.registerCount == 7) { "Unreviewed keyboard touch registers" }
        if (touch.implementation!!.instructions.none { it.isMethodReference(touchCall.encodedAbi) }) {
            touch.addInstructions(0, """
                ${RuntimeCallEmitter.invoke(touchCall, "p0 .. p1")}
                move-result v0
                if-eqz v0, :gboard_clipboard_stock_touch
                return v0
                :gboard_clipboard_stock_touch
                nop
            """.trimIndent())
        }
        // M reuses p0 for G():mln before RETURN_VOID. Capture the mlh receiver at entry.
        val controller = findMutableMethodOrThrow("Lmlh;", "M", "V", listOf("Landroid/view/View;"))
        val capture = RuntimeCallId.CLIPBOARD_NATIVE_SEARCH_CONTROLLER
        if (controller.implementation!!.instructions.none { it.isMethodReference(capture.encodedAbi) }) {
            controller.addInstructions(0, RuntimeCallEmitter.invoke(capture, "p0 .. p0"))
        }
        check(controller.implementation!!.instructions.first().isMethodReference(capture.encodedAbi)) {
            "Clipboard controller must be captured before M overwrites its receiver"
        }
        findMutableMethodOrThrow(CLIPBOARD_KEYBOARD_CLASS, "e", "V",
            listOf("Landroid/view/inputmethod/EditorInfo;", "Ljava/lang/Object;"))
            .applyVoidExitLifecycleDelegate(RuntimeCallId.CLIPBOARD_SEARCH_OPENED, "p0 .. p0")
        val close = findMutableMethodOrThrow(CLIPBOARD_KEYBOARD_CLASS, "h", "V", emptyList())
        val call = RuntimeCallId.CLIPBOARD_SEARCH_CLOSED
        val count = close.implementation!!.instructions.count { it.isMethodReference(call.encodedAbi) }
        check(count <= 1) { "Duplicate clipboard search teardown" }
        if (count == 0) close.addInstructions(0, RuntimeCallEmitter.invoke(call, "p0 .. p0"))
        else check(close.implementation!!.instructions.first().isMethodReference(call.encodedAbi))
        listOf("M" to listOf("Z")).forEach { (name, parameters) ->
            findMutableMethodOrThrow(CLIPBOARD_ADAPTER_CLASS, name, "V", parameters)
                .applyVoidExitLifecycleDelegate(RuntimeCallId.CLIPBOARD_SEARCH_CHANGED, "p0 .. p0")
        }
        // The composite search outlives ClipboardKeyboard.h() (it replaces the panel with the
        // alphabet keyboard), so the input session lifecycle restores its provider and surface.
        val end = RuntimeCallId.CLIPBOARD_NATIVE_SEARCH_END
        listOf(
            "onFinishInputView" to listOf("Z"),
            "onFinishInput" to emptyList(),
            "onWindowHidden" to emptyList(),
            "onDestroy" to emptyList(),
        ).forEach { (name, parameters) ->
            val method = findMutableMethodOrThrow(INPUT_METHOD_SERVICE_OWNER, name, "V", parameters)
            val existing = method.implementation!!.instructions.count { it.isMethodReference(end.encodedAbi) }
            check(existing <= 1) { "Duplicate clipboard search session end in $name" }
            if (existing == 0) method.addInstructions(0, RuntimeCallEmitter.invoke(end, ""))
        }
        // Loup declares onKeyDown/onKeyUp (0x4db8a0 / 0x4dbb1c): Back first closes an open search.
        val back = RuntimeCallId.CLIPBOARD_NATIVE_SEARCH_BACK
        listOf("onKeyDown", "onKeyUp").forEach { name ->
            val method = findMutableMethodOrThrow(
                INPUT_METHOD_SERVICE_OWNER, name, "Z", listOf("I", "Landroid/view/KeyEvent;"),
            )
            val implementation = method.implementation!!
            val calls = implementation.instructions.count { it.isMethodReference(back.encodedAbi) }
            check(calls <= 1) { "Duplicate clipboard search Back delegate in $name" }
            if (calls == 0) {
                // p0..p2 are the three parameter words; v0 must be a local, free at entry.
                check(implementation.registerCount > 3) { "No scratch register in $name" }
                method.addInstructions(0, """
                    ${RuntimeCallEmitter.invoke(back, "p1 .. p2")}
                    move-result v0
                    if-eqz v0, :gboard_patches_stock_key
                    return v0
                    :gboard_patches_stock_key
                    nop
                """.trimIndent())
            } else {
                check(implementation.instructions.first().isMethodReference(back.encodedAbi)) {
                    "Misplaced clipboard search Back delegate in $name"
                }
            }
        }
        // Patch the *virtual dispatch winner*, not an assumed superclass. If a subclass
        // overrides onComputeInsets without calling Loup, the old hook never runs and the
        // search panel is drawn outside the touchable IME window (taps reach the editor).
        // On the exact 18.0.3 target Loup is known to declare the method; subclasses are
        // checked defensively and the most-derived declaration wins.
        val insetsParameters = listOf("Landroid/inputmethodservice/InputMethodService\$Insets;")
        val insets = INSETS_DISPATCH_OWNERS.firstNotNullOfOrNull { owner ->
            runCatching { mutableClass(owner) }.getOrNull()?.methods?.firstOrNull {
                it.name == "onComputeInsets" && it.returnType == "V" && it.parameterTypes == insetsParameters
                        && it.implementation != null
            }
        } ?: error("Missing 18.0.3 onComputeInsets dispatch target")
        val begin = RuntimeCallId.CLIPBOARD_NATIVE_SEARCH_INSETS_BEGIN
        val begins = insets.implementation!!.instructions.count { it.isMethodReference(begin.encodedAbi) }
        check(begins <= 1) { "Duplicate clipboard search insets capture" }
        // Original 18.0.3 onComputeInsets uses 21 registers and 2 parameter words:
        // p1 resolves to v20, outside invoke-static (35c)'s 4-bit register limit.
        // Force the /range encoding so the Morphe inline Smali compiler accepts it.
        if (begins == 0) insets.addInstructions(0, RuntimeCallEmitter.invoke(begin, "p1 .. p1"))
        else check(insets.implementation!!.instructions.first().isMethodReference(begin.encodedAbi))
        insets.applyVoidExitLifecycleDelegate(RuntimeCallId.CLIPBOARD_NATIVE_SEARCH_INSETS, "")
    }
}
