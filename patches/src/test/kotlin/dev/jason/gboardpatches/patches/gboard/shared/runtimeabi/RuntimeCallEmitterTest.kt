package dev.jason.gboardpatches.patches.gboard.shared.runtimeabi

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeCallEmitterTest {
    @Test
    fun `clipboard insets injection compiles p1 above the non-range register limit`() {
        // Original Loup.onComputeInsets: registers_size=21, ins_size=2, p1=v20.
        val method = ImmutableMethod(
            "Loup;",
            "onComputeInsets",
            listOf("Landroid/inputmethodservice/InputMethodService\$Insets;"),
            "V",
            AccessFlags.PUBLIC.value,
            emptySet(),
            emptySet(),
            MutableMethodImplementation(21),
        ).toMutable()
        method.addInstructions(
            0,
            RuntimeCallEmitter.invoke(RuntimeCallId.CLIPBOARD_NATIVE_SEARCH_INSETS_BEGIN, "p1 .. p1"),
        )
        val call = method.implementation!!.instructions.single()
        assertEquals(Opcode.INVOKE_STATIC_RANGE, call.opcode)
        val range = call as RegisterRangeInstruction
        assertEquals(20, range.startRegister)
        assertEquals(1, range.registerCount)
    }

    @Test
    fun `emits an exact static invocation from a semantic call id`() {
        assertEquals(
            "invoke-static {}, " +
                "Ldev/jason/gboardpatches/extension/rambler/" +
                "GboardRambler1803OfficialSelectionRuntime;->enterVoiceSettingsScope()V",
            RuntimeCallEmitter.invoke(
                RuntimeCallId.RAMBLER_RUNTIME_ENTER_VOICE_SETTINGS_SCOPE,
                registers = "",
            ),
        )
    }

    @Test
    fun `emits a static range invocation without exposing an opcode string`() {
        assertEquals(
            "invoke-static/range {v4 .. v4}, " +
                "Ldev/jason/gboardpatches/extension/webclipboard/" +
                "GboardWebClipboardCaptureBootstrap;->afterLatinImeOnCreate(Ljava/lang/Object;)V",
            RuntimeCallEmitter.invoke(
                RuntimeCallId.WEB_CLIPBOARD_CAPTURE_BOOTSTRAP_AFTER_LATIN_IME_ON_CREATE,
                registers = "v4 .. v4",
            ),
        )
    }

    @Test
    fun `selects range encoding when a contiguous expression cannot fit 35c`() {
        val abi = RuntimeAbi.decode("Lexample/Runtime;->call(IIIIII)V")

        assertEquals(
            "invoke-static/range {v12 .. v17}, Lexample/Runtime;->call(IIIIII)V",
            RuntimeCallEmitter.invoke(abi, "v12, v13, v14, v15, v16, v17"),
        )
    }

    @Test
    fun `rejects a register expression with the wrong argument word count`() {
        val abi = RuntimeAbi.decode("Lexample/Runtime;->call(JI)V")

        assertThrows(IllegalArgumentException::class.java) {
            RuntimeCallEmitter.invoke(abi, "v0, v1")
        }
    }

    @Test
    fun `rejects noncontiguous words for a wide argument`() {
        val abi = RuntimeAbi.decode("Lexample/Runtime;->call(JI)V")

        assertThrows(IllegalArgumentException::class.java) {
            RuntimeCallEmitter.invoke(abi, "v0, v2, v3")
        }
    }

    @Test
    fun `rejects a noncontiguous expression outside the 35c register limit`() {
        val abi = RuntimeAbi.decode("Lexample/Runtime;->call(II)V")

        assertThrows(IllegalArgumentException::class.java) {
            RuntimeCallEmitter.invoke(abi, "v16, v18")
        }
    }

    @Test
    fun `accepts explicit contiguous words for wide parameters`() {
        val abi = RuntimeAbi.decode("Lexample/Runtime;->call(JD)V")

        assertEquals(
            "invoke-static {v0, v1, v2, v3}, Lexample/Runtime;->call(JD)V",
            RuntimeCallEmitter.invoke(abi, "v0, v1, v2, v3"),
        )
    }
}
