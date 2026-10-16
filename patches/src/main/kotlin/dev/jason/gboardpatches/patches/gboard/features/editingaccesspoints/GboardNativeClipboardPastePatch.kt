package dev.jason.gboardpatches.patches.gboard.features.editingaccesspoints

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

/** Original 18.0.3 SHA-256: 3099262121a201dcb2ae7e185bee6b2057287a5aeffae4be0e30f537e505e5b3. */
internal val gboardNativeClipboardPastePatch = bytecodePatch(
    description = "Capture verified native clipboard text replacements for Undo/Redo.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        val method = findMutableMethodOrThrow(
            classType = "Lcom/google/android/apps/inputmethod/libs/clipboard/ClipboardKeyboard;",
            name = "D", returnType = "V", parameterTypes = listOf("Lfih;", "Z"),
        )
        val before = RuntimeCallId.EDITING_NATIVE_CLIPBOARD_BEFORE_TEXT
        val after = RuntimeCallId.EDITING_NATIVE_CLIPBOARD_AFTER_TEXT
        val attempt = RuntimeCallId.EDITING_NATIVE_CLIPBOARD_ATTEMPT
        val instructions = method.implementation?.instructions?.toList()
            ?: error("Missing clipboard paste implementation")
        val beforeCount = instructions.count { it.isMethodReference(before.encodedAbi) }
        val afterCount = instructions.count { it.isMethodReference(after.encodedAbi) }
        val attemptCount = instructions.count { it.isMethodReference(attempt.encodedAbi) }
        check(beforeCount == afterCount && beforeCount <= 1) { "Partial clipboard text capture" }
        check(attemptCount == beforeCount) { "Partial clipboard attempt capture" }
        val dispatcher = "Lfkl;->d(Landroid/content/Context;Ljava/util/function/Consumer;" +
            "Landroid/view/inputmethod/EditorInfo;Ljava/lang/String;Ljava/lang/String;Lpqu;)V"
        val matches = instructions.indices.filter { instructions[it].isMethodReference(dispatcher) }
        check(matches.size == 1) { "Expected one stock clipboard text dispatcher" }
        val index = matches.single()
        val invoke = instructions[index] as? RegisterRangeInstruction
            ?: error("Clipboard text dispatcher must use range invocation")
        check(instructions[index].opcode == Opcode.INVOKE_STATIC_RANGE &&
            invoke.registerCount == 6 && invoke.startRegister == 4 &&
            method.implementation!!.registerCount == 24) { "Unreviewed clipboard paste register shape" }
        if (beforeCount == 1) {
            check(instructions.first().isMethodReference(attempt.encodedAbi) &&
                instructions.getOrNull(index - 1)?.isMethodReference(before.encodedAbi) == true &&
                instructions.getOrNull(index + 1)?.isMethodReference(after.encodedAbi) == true) {
                "Misplaced clipboard capture calls"
            }
        } else {
            // Text is the fourth dispatcher argument. No scratch register or native insertion change.
            val textRegister = invoke.startRegister + 3
            method.addInstructions(index + 1, RuntimeCallEmitter.invoke(after, ""))
            method.addInstructions(index, RuntimeCallEmitter.invoke(before, "v$textRegister .. v$textRegister"))
            method.addInstructions(0, RuntimeCallEmitter.invoke(attempt, ""))
        }
    }
}
