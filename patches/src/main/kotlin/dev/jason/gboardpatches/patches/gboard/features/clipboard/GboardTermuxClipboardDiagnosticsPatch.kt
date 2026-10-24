package dev.jason.gboardpatches.patches.gboard.features.clipboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

/** Read-only hooks into the reviewed AutoPasteSuggestionHelper, opt-in at runtime. */
internal val gboardTermuxClipboardDiagnosticsPatch = bytecodePatch(
    description = "Opt-in, content-free tracing of the native clipboard chip in Termux.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        // Original 18.0.3 retains p0 on every normal exit of these three methods.
        findMutableMethodOrThrow("Lfif;", "t", "V", listOf(
            "Lnvq;", "Loru;", "Landroid/view/inputmethod/EditorInfo;", "Z",
        )).applyVoidExitLifecycleDelegate(RuntimeCallId.TERMUX_CLIPBOARD_PREPARED, "p0 .. p0")
        findMutableMethodOrThrow("Lfif;", "k", "V", emptyList())
            .applyVoidExitLifecycleDelegate(RuntimeCallId.TERMUX_CLIPBOARD_REFRESHED, "p0 .. p0")
        findMutableMethodOrThrow("Lfif;", "j", "V", listOf("Z"))
            .applyVoidExitLifecycleDelegate(RuntimeCallId.TERMUX_CLIPBOARD_REQUESTED, "p0 .. p0")

        // A populated source at session prepare does not establish whether the native
        // candidate-construction gate x() is even reached. Inspect its entry and each
        // normal boolean return without changing the return register or control flow.
        val creation = findMutableMethodOrThrow("Lfif;", "x", "Z", emptyList())
        val instructions = creation.implementation?.instructions?.toList()
            ?: error("Missing native clipboard creation gate")
        val enters = RuntimeCallId.TERMUX_CLIPBOARD_CREATION_ENTERED
        val exits = RuntimeCallId.TERMUX_CLIPBOARD_CREATION_EXITED
        val entryCount = instructions.count { it.isMethodReference(enters.encodedAbi) }
        val exitCount = instructions.count { it.isMethodReference(exits.encodedAbi) }
        val returns = instructions.indices.filter { instructions[it].isOpcode("RETURN") }
        check(returns.isNotEmpty()) { "Missing boolean returns in native clipboard creation gate" }
        if (entryCount == 0 && exitCount == 0) {
            returns.asReversed().forEach { index ->
                creation.addInstructions(index, RuntimeCallEmitter.invoke(exits, "p0 .. p0"))
            }
            creation.addInstructions(0, RuntimeCallEmitter.invoke(enters, "p0 .. p0"))
        } else {
            check(entryCount == 1 && exitCount == returns.size &&
                instructions.first().isMethodReference(enters.encodedAbi) &&
                returns.all { index ->
                    instructions.getOrNull(index - 1)?.isMethodReference(exits.encodedAbi) == true
                }) { "Partial native clipboard creation diagnostics" }
        }
    }
}
