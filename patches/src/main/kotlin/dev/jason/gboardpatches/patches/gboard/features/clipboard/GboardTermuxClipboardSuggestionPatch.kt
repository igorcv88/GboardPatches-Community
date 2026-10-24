package dev.jason.gboardpatches.patches.gboard.features.clipboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

/** Exact 18.0.3 helper-local fallback, no EditorInfo or terminal input-policy override. */
internal val gboardTermuxClipboardSuggestionPatch = bytecodePatch(
    description = "Show the latest native clipboard item in Termux's existing suggestion holder.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        val creation = findMutableMethodOrThrow("Lfif;", "x", "Z", emptyList())
        val call = RuntimeCallId.TERMUX_CLIPBOARD_SUGGESTION
        val instructions = creation.implementation!!.instructions
        // Original x has 13 registers; v0 is initialized immediately and p0 is v12.
        check(creation.implementation!!.registerCount == 13) { "Unreviewed clipboard creation registers" }
        val calls = instructions.count { it.isMethodReference(call.encodedAbi) }
        check(calls <= 1) { "Duplicate Termux clipboard fallback" }
        if (calls == 0) creation.addInstructions(0, """
            ${RuntimeCallEmitter.invoke(call, "p0 .. p0")}
            move-result v0
            if-eqz v0, :gboard_termux_clipboard_stock
            return v0
            :gboard_termux_clipboard_stock
            nop
        """.trimIndent())
        val close = findMutableMethodOrThrow("Lfif;", "l", "V", emptyList())
        val closeCall = RuntimeCallId.TERMUX_CLIPBOARD_CLOSED
        val closes = close.implementation!!.instructions.count { it.isMethodReference(closeCall.encodedAbi) }
        check(closes <= 1) { "Duplicate Termux clipboard cleanup" }
        if (closes == 0) close.addInstructions(0, RuntimeCallEmitter.invoke(closeCall, "p0 .. p0"))
    }
}
