package dev.jason.gboardpatches.patches.gboard.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

internal fun MutableMethod.applyVoidExitLifecycleDelegate(
    call: RuntimeCallId,
    registers: String,
) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN_VOID") }
    check(returns.isNotEmpty()) { "No RETURN_VOID in $definingClass->$name" }
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == returns.size && returns.all { returnIndex ->
            instructions.getOrNull(returnIndex - 1)?.isMethodReference(abi.reference) == true
        }) { "Malformed void-exit lifecycle delegate in $definingClass->$name" }
        return
    }

    val invoke = RuntimeCallEmitter.invoke(call, registers)
    returns.asReversed().forEach { returnIndex ->
        addInstructions(returnIndex, invoke)
    }
}

/**
 * Use when native code branches to RETURN_VOID itself. A normal pre-return insertion
 * is bypassed by these branches, because their labels remain on the old return.
 * Making that original branch target a NOP guarantees every path subsequently runs
 * the delegate and a replacement RETURN_VOID.
 */
internal fun MutableMethod.applyVoidExitTargetSafeDelegate(
    call: RuntimeCallId,
    registers: String,
    expectedReturns: Int,
) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val exits = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN_VOID") }
    check(exits.size == expectedReturns) {
        "Unexpected $definingClass->$name RETURN_VOID count: ${exits.size}"
    }
    check(instructions.none { it.isMethodReference(abi.reference) }) {
        "Already injected target-safe exit delegate in $definingClass->$name"
    }
    val invoke = RuntimeCallEmitter.invoke(call, registers)
    exits.asReversed().forEach { exit ->
        replaceInstruction(exit, "nop")
        addInstructions(exit + 1, "$invoke\nreturn-void")
    }
}
