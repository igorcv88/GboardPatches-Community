package dev.jason.gboardpatches.patches.gboard.features.clipboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

/** Reviewed 18.0.3 InputMethodService owner (same target as the editing lifecycle). */
private const val INPUT_METHOD_SERVICE_OWNER = "Loup;"

internal val gboardClipboardSearch1803Patch = bytecodePatch(
    description = "Clipboard search on the native alphabet keyboard with results above it.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardClipboardItemBindPatch)
    execute {
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
    }
}
