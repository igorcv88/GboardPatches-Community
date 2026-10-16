package dev.jason.gboardpatches.patches.gboard.features.editingaccesspoints

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardEditingAccessPointsLifecyclePatch = bytecodePatch(
    description = "接入 Toolbar Editing Buttons input session lifecycle。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        GboardEditingAccessPoints1803Targets.validate(this)
        findMutableMethodOrThrow(GboardEditingAccessPoints1803Targets.selectionUpdated)
            .applyEditingSelectionObserver()
        GboardEditingAccessPoints1803Targets.resetMethods.forEach { target ->
            findMutableMethodOrThrow(target).applyEditingSessionReset()
        }
        findMutableMethodOrThrow(GboardEditingAccessPoints1803Targets.startView)
            .applyVoidExitLifecycleDelegate(
                RuntimeCallId.EDITING_ACCESS_POINTS_START, "p0 .. p0",
            )
        mutableClass(GboardEditingAccessPoints1803Targets.serviceOwner).applyInputConnectionJournal()
    }
}

private const val INPUT_CONNECTION = "Landroid/view/inputmethod/InputConnection;"

/**
 * Overrides getCurrentInputConnection() on the reviewed service owner. Every Gboard call,
 * including calls typed against InputMethodService, dispatches virtually to this override,
 * so keyboard-originated edits pass through the journaling wrapper without binding any
 * obfuscated editing method. A pre-existing override is not rewritten: the runtime then
 * reports that the journal is inactive and Undo/Redo keep their previous behavior.
 */
internal fun MutableClass.applyInputConnectionJournal() {
    val call = RuntimeCallId.EDITING_ACCESS_POINTS_WRAP_INPUT_CONNECTION
    val reference = RuntimeAbiCatalog.abi(call).reference
    val existing = methods.firstOrNull {
        it.name == "getCurrentInputConnection" && it.parameterTypes.isEmpty() && it.returnType == INPUT_CONNECTION
    }
    if (existing != null) {
        val calls = existing.implementation?.instructions?.count { it.isMethodReference(reference) } ?: 0
        check(calls <= 1) { "Duplicate input connection journal" }
        return
    }
    val parent = superclass ?: error("Service owner without superclass")
    addHelperMethodIfMissing(
        name = "getCurrentInputConnection",
        parameterTypes = emptyList(),
        returnType = INPUT_CONNECTION,
        accessFlags = AccessFlags.PUBLIC.value,
        registerCount = 2,
        body = """
            invoke-super {p0}, $parent->getCurrentInputConnection()$INPUT_CONNECTION
            move-result-object v0
            ${RuntimeCallEmitter.invoke(call, "v0")}
            move-result-object v0
            return-object v0
        """,
    )
}

internal fun MutableMethod.applyEditingSessionReset() {
    val call = RuntimeCallId.EDITING_ACCESS_POINTS_END
    val reference = RuntimeAbiCatalog.abi(call).reference
    val instructions = implementation?.instructions ?: error("Missing lifecycle implementation")
    val existing = instructions.count { it.isMethodReference(reference) }
    if (existing > 0) {
        check(existing == 1) { "Duplicate editing session reset" }
        return
    }
    addInstructions(0, RuntimeCallEmitter.invoke(call, ""))
}

/** Range invocation consumes only the four Android callback parameters, without scratch locals. */
internal fun MutableMethod.applyEditingSelectionObserver() {
    val call = RuntimeCallId.EDITING_ACCESS_POINTS_SELECTION_UPDATED
    val reference = RuntimeAbiCatalog.abi(call).reference
    val instructions = implementation?.instructions ?: error("Missing selection implementation")
    val existing = instructions.count { it.isMethodReference(reference) }
    check(existing <= 1) { "Duplicate editing selection observer" }
    if (existing == 0) addInstructions(0, RuntimeCallEmitter.invoke(call, "p3 .. p6"))
}
