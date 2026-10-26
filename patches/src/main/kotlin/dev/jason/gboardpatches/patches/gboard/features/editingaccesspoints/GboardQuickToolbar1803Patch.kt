package dev.jason.gboardpatches.patches.gboard.features.editingaccesspoints

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardQuickToolbar1803Patch = bytecodePatch(
    description = "Toggle the native favorites row while real candidates are active.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardEditingAccessPointsLifecyclePatch)
    execute {
        // C() requests pga.p(HEADER, candidates). Leave list binding/data intact while tools
        // own the header, instead of letting candidates preempt it and posting an M() restore.
        val request = findMutableMethodOrThrow("Leso;", "C", "V", emptyList())
        val keep = RuntimeCallId.QUICK_TOOLBAR_KEEP_TOOLS
        check(request.implementation!!.registerCount == 12) { "Unreviewed candidate request registers" }
        val keepCalls = request.implementation!!.instructions.count { it.isMethodReference(keep.encodedAbi) }
        check(keepCalls <= 1) { "Duplicate candidate surface gate" }
        if (keepCalls == 0) request.addInstructions(0, """
            ${RuntimeCallEmitter.invoke(keep, "p0 .. p0")}
            move-result v0
            if-eqz v0, :gboard_candidate_surface
            return-void
            :gboard_candidate_surface
            nop
        """.trimIndent())
        val candidates = findMutableMethodOrThrow("Leso;", "b", "V", listOf("Ljava/util/List;", "Lojt;", "Z"))
        val listCall = RuntimeCallId.QUICK_TOOLBAR_CANDIDATE_LIST
        val listCalls = candidates.implementation!!.instructions.count { it.isMethodReference(listCall.encodedAbi) }
        check(listCalls <= 1) { "Duplicate candidate-list observer" }
        if (listCalls == 0) candidates.addInstructions(0, RuntimeCallEmitter.invoke(listCall, "p0 .. p1"))
        else check(candidates.implementation!!.instructions.first().isMethodReference(listCall.encodedAbi)) {
            "Misplaced candidate-list observer"
        }
        findMutableMethodOrThrow("Lmlh;", "M", "V", listOf("Landroid/view/View;"))
            .applyVoidExitLifecycleDelegate(RuntimeCallId.QUICK_TOOLBAR_CONTROLLER_VIEW, "p0 .. p0")
        listOf("b" to listOf("Ljava/util/List;", "Lojt;", "Z"), "v" to emptyList())
            .forEach { (name, parameters) ->
                findMutableMethodOrThrow("Leso;", name, "V", parameters)
                    .applyVoidExitLifecycleDelegate(RuntimeCallId.QUICK_TOOLBAR_CANDIDATES, "p0 .. p0")
            }
        listOf("c", "close").forEach { name ->
            findMutableMethodOrThrow("Leso;", name, "V", emptyList())
                .applyVoidExitLifecycleDelegate(RuntimeCallId.QUICK_TOOLBAR_CLEARED, "p0 .. p0")
        }
        val event = findMutableMethodOrThrow("Lmln;", "m", "Z", listOf("Lnur;"))
        val call = RuntimeCallId.QUICK_TOOLBAR_EVENT
        val instructions = event.implementation!!.instructions
        check(event.implementation!!.registerCount == 14) { "Unreviewed AP event register shape" }
        val calls = instructions.count { it.isMethodReference(call.encodedAbi) }
        check(calls <= 1) { "Duplicate contextual toolbar delegate" }
        if (calls == 0) event.addInstructions(0, """
            ${RuntimeCallEmitter.invoke(call, "p0 .. p1")}
            move-result v0
            if-eqz v0, :gboard_patches_stock_ap_event
            return v0
            :gboard_patches_stock_ap_event
            nop
        """.trimIndent())
        else check(instructions.first().isMethodReference(call.encodedAbi)) { "Misplaced AP event delegate" }
    }
}
