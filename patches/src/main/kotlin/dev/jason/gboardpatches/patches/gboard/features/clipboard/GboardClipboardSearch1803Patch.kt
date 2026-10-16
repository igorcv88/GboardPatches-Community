package dev.jason.gboardpatches.patches.gboard.features.clipboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardClipboardSearch1803Patch = bytecodePatch(
    description = "Attach isolated search input to the native clipboard header and observe native list refreshes.",
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
    }
}
