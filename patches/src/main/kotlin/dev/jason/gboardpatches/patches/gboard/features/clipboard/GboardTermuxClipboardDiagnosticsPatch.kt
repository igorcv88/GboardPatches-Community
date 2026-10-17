package dev.jason.gboardpatches.patches.gboard.features.clipboard

import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
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
    }
}
