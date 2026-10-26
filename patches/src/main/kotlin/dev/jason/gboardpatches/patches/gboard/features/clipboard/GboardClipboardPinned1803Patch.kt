package dev.jason.gboardpatches.patches.gboard.features.clipboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

/** Keep all native storage/mutation positions; translate only the reviewed presentation boundary. */
internal val gboardClipboardPinned1803Patch = bytecodePatch(
    description = "Display pinned clips before recent clips without reordering native storage.",
) {
    dependsOn(gboardClipboardItemBindPatch)
    execute {
        val bind = findMutableMethodOrThrow("Lfjk;", "p", "V", listOf("Lkr;", "I"))
        val bindCall = RuntimeCallId.CLIPBOARD_PINNED_BIND
        check(bind.implementation!!.registerCount == 19) { "Expected expanded clipboard bind" }
        if (bind.implementation!!.instructions.none { it.isMethodReference(bindCall.encodedAbi) }) {
            bind.addInstructions(0, """
                ${RuntimeCallEmitter.invoke(bindCall, "p0 .. p2")}
                move-result p2
            """.trimIndent())
        }
        val type = findMutableMethodOrThrow("Lfjk;", "gT", "I", listOf("I"))
        val position = RuntimeCallId.CLIPBOARD_PINNED_POSITION
        if (type.implementation!!.instructions.none { it.isMethodReference(position.encodedAbi) }) {
            type.addInstructions(0, """
                ${RuntimeCallEmitter.invoke(position, "p0 .. p1")}
                move-result p1
            """.trimIndent())
        }
        // All five holders are created/bound by fjk.p. kr.b() itself stays entirely stock.
        val holderMethods = listOf(
            Triple("Lfjk;", "p", listOf("Lkr;", "I")),
            Triple("Lfhz;", "onClick", listOf("Landroid/view/View;")),
            Triple("Lfjd;", "onLongClick", listOf("Landroid/view/View;")),
            Triple("Lfjf;", "m", listOf("Lkr;")),
        )
        holderMethods.forEach { (owner, name, parameters) ->
            val method = findMutableMethodOrThrow(owner, name, if (name == "onLongClick") "Z" else "V", parameters)
            val positions = method.implementation!!.instructions.mapIndexedNotNull { index, instruction ->
                index.takeIf { instruction.isMethodReference("Lkr;->b()I") }
            }
            val call = RuntimeCallId.CLIPBOARD_PINNED_HOLDER
            check(positions.size == (if (owner == "Lfhz;") 2 else 1) ||
                method.implementation!!.instructions.count { it.isMethodReference(call.encodedAbi) } ==
                (if (owner == "Lfhz;") 2 else 1)) { "Unreviewed clipboard holder positions: $owner" }
            positions.forEach { index ->
                val invoke = method.implementation!!.instructions[index] as FiveRegisterInstruction
                method.replaceInstruction(index, RuntimeCallEmitter.invoke(call, "v${invoke.registerC}"))
            }
        }
        // Replace native canonical granular notifications, including the single-clip undo paths.
        // Shared synthetic class fdo has other branches: the runtime delegates non-fjk receivers.
        val notifications = setOf("gA(I)V", "gC(I)V", "n(I)V", "gD(II)V", "gE(II)V")
        listOf("Lfjk;", CLIPBOARD_KEYBOARD_CLASS, "Lfdo;").forEach { owner ->
            mutableClass(owner).methods.forEach { method ->
                val instructions = method.implementation?.instructions ?: return@forEach
                instructions.toList().forEachIndexed { index, instruction ->
                    val entry = notifications.firstOrNull {
                        instruction.isMethodReference("Ljt;->$it")
                    } ?: return@forEachIndexed
                    val invoke = instruction as FiveRegisterInstruction
                    val registers = listOf(invoke.registerC, invoke.registerD, invoke.registerE).take(invoke.registerCount)
                    method.replaceInstruction(index, clipboardNotification(entry,
                        registers.joinToString(", ") { "v$it" }))
                }
            }
        }
        // Auto-scroll targets are canonical as well. Map only at the layout boundary.
        listOf("ao" to listOf("Landroid/util/SparseArray;", "Z"), "J" to listOf("I")).forEach { (name, args) ->
            val method = findMutableMethodOrThrow(CLIPBOARD_KEYBOARD_CLASS, name, "V", args)
            val call = RuntimeCallId.CLIPBOARD_PINNED_VISUAL
            if (method.implementation!!.instructions.none { it.isMethodReference(call.encodedAbi) }) {
                val index = method.implementation!!.instructions.indexOfFirst {
                    it.isMethodReference("Landroid/support/v7/widget/RecyclerView;->ak(I)V")
                }
                check(index >= 0) { "Missing clipboard scroll boundary" }
                val invoke = method.implementation!!.instructions[index] as FiveRegisterInstruction
                val positionRegister = invoke.registerD
                // Both reviewed methods retain adapter in v0 until this call.
                method.addInstructions(index, """
                    ${RuntimeCallEmitter.invoke(call, "v0, v$positionRegister")}
                    move-result v$positionRegister
                """.trimIndent())
            }
        }
        val insertion = findMutableMethodOrThrow("Lfjk;", "I", "V", listOf("Lfih;"))
        val visual = RuntimeCallId.CLIPBOARD_PINNED_VISUAL
        if (insertion.implementation!!.instructions.none { it.isMethodReference(visual.encodedAbi) }) {
            val index = insertion.implementation!!.instructions.indexOfFirst {
                (it as? ReferenceInstruction)?.reference?.toString() == "Lkn;->b:I"
            }
            check(index >= 0) { "Missing native smooth scroll target" }
            val put = insertion.implementation!!.instructions[index] as TwoRegisterInstruction
            check(put.registerA == 3) { "Unreviewed smooth scroll register" }
            insertion.addInstructions(index, """
                ${RuntimeCallEmitter.invoke(visual, "v0, v3")}
                move-result v3
            """.trimIndent())
        }
        // Sticky headers must use the displayed section rather than compare visual positions
        // against canonical boundaries. Data counts and expiry trimming remain native.
        listOf("N" to emptyList(), "L" to listOf("Landroid/support/v7/widget/RecyclerView;")).forEach { (name, args) ->
            val method = findMutableMethodOrThrow("Lfjk;", name, "V", args)
            val call = RuntimeCallId.CLIPBOARD_PINNED_STICKY
            if (method.implementation!!.instructions.none { it.isMethodReference(call.encodedAbi) }) {
                method.addInstructions(0, """
                    ${RuntimeCallEmitter.invoke(call, "p0 .. p0")}
                    move-result v0
                    if-eqz v0, :gboard_native_sticky
                    return-void
                    :gboard_native_sticky
                    nop
                """.trimIndent())
            }
        }
    }
}

private fun clipboardNotification(native: String, registers: String): String = when (native) {
    "gA(I)V" -> RuntimeCallEmitter.invoke(RuntimeCallId.CLIPBOARD_PINNED_NOTIFY_CHANGED, registers)
    "gC(I)V" -> RuntimeCallEmitter.invoke(RuntimeCallId.CLIPBOARD_PINNED_NOTIFY_INSERTED, registers)
    "n(I)V" -> RuntimeCallEmitter.invoke(RuntimeCallId.CLIPBOARD_PINNED_NOTIFY_REMOVED, registers)
    "gD(II)V" -> RuntimeCallEmitter.invoke(RuntimeCallId.CLIPBOARD_PINNED_NOTIFY_MOVED, registers)
    "gE(II)V" -> RuntimeCallEmitter.invoke(RuntimeCallId.CLIPBOARD_PINNED_NOTIFY_RANGE, registers)
    else -> error("Unreviewed clipboard notification")
}
