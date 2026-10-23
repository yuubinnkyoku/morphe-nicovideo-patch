package app.yuubinnkyoku.patches.nicovideo

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.yuubinnkyoku.patches.shared.NICOVIDEO_LEGACY_855_COMPATIBILITY
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val LEGACY_DIAGNOSTICS =
    "Lapp/yuubinnkyoku/extension/nicovideo/LegacyLoginDiagnostics;"

/**
 * niconico 8.55.0's nm/g0.c converts AccountPassportErrorType.getErrorCode()
 * to a user-visible WL-Exx or WL-EU code. WL-EU is the unmapped fallback.
 */
private object LegacyAccountPassportErrorMapper : Fingerprint(
    definingClass = "Lnm/g0;",
    name = "c",
    returnType = "Ljava/lang/String;",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljp/co/dwango/android/nicoca/account/model/AccountPassportErrorType;"
    ),
    filters = listOf(fieldAccess(smali = "Lds/l;->Ni:Lds/l;"))
)

/** niconico 8.55.0 uses nm/d0.e to display login errors in a Snackbar. */
private object LegacyLoginSnackbar : Fingerprint(
    definingClass = "Lnm/d0;",
    name = "e",
    returnType = "V",
    parameters = listOf(
        "Landroid/view/View;",
        "Ljava/lang/String;",
        "Lnm/d0\$b;"
    )
)

/**
 * Opt-in, read-only diagnostic for the legacy 8.55.0 login path.
 *
 * Logs only an allowlisted *error-code* string and error type class.
 * It does NOT collect tokens, redirect query parameters, PKCE data,
 * credentials, raw response bodies, URLs or request headers.
 */
@Suppress("unused")
val legacy855LoginDiagnosticsPatch = bytecodePatch(
    name = "niconico 8.55.0: copy legacy login error",
    description = "Display a copyable read-only report when old login returns WL-EU, including sanitized server error code and error type.",
    default = false
) {
    compatibleWith(NICOVIDEO_LEGACY_855_COMPATIBILITY)
    extendWith("extensions/extension.mpe")

    execute {
        val mapper = LegacyAccountPassportErrorMapper.method
        val insns = mapper.implementation!!.instructions.toList()
        val errorCodeCall = insns.indexOfFirst {
            val m = (it as? ReferenceInstruction)?.reference as? MethodReference
            m?.definingClass ==
                "Ljp/co/dwango/android/nicoca/account/model/AccountPassportErrorType;" &&
                m.name == "getErrorCode" && m.parameterTypes.isEmpty()
        }
        if (errorCodeCall < 0 || errorCodeCall + 1 >= insns.size ||
            insns[errorCodeCall + 1].opcode != Opcode.MOVE_RESULT_OBJECT
        ) {
            throw PatchException("8.55.0 legacy error mapper getErrorCode() not found")
        }

        // At errorCodeCall + 1 the original moves the returned string to p2.
        // Insert AFTER the move-result-object; the original comparison remains intact.
        mapper.addInstruction(
            errorCodeCall + 2,
            "invoke-static {p2}, $LEGACY_DIAGNOSTICS->recordErrorCode(Ljava/lang/String;)V"
        )
        mapper.addInstruction(
            0,
            "invoke-static {p2}, $LEGACY_DIAGNOSTICS->recordErrorType(Ljava/lang/Object;)V"
        )


        // Observe HTTP statuses from the original Retrofit Response objects.
        // No response bodies, OAuth credentials or request headers are read.
        val network = classDefBy("Ljp/co/dwango/android/nicoca/account/c;")
            .methods.first { it.name == "f" && it.parameterTypes.size == 6 }
        val method = mutableClassDefBy("Ljp/co/dwango/android/nicoca/account/c;")
            .methods.first { it.name == network.name && it.parameterTypes.size == 6 }
        val instructions = method.implementation!!.instructions.toList()
        val responseChecks = instructions.indices.filter { index ->
            val ref = (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
            ref?.definingClass == "Lyx/r;" && ref.name == "e" &&
                ref.parameterTypes.isEmpty() && ref.returnType == "Z"
        }
        if (responseChecks.size != 2) {
            throw PatchException("Expected two login API response checks; found " + responseChecks.size)
        }
        responseChecks.asReversed().forEachIndexed { reverseIndex, index ->
            val hookName = if (reverseIndex == 0) "recordPassportHttpStatus" else "recordOauthHttpStatus"
            method.addInstructions(index, """
                invoke-virtual {v0}, Lyx/r;->b()I
                move-result v3
                invoke-static {v3}, $LEGACY_DIAGNOSTICS->$hookName(I)V
            """.trimIndent())
        }

        val uiMethod = LegacyLoginSnackbar.method
        uiMethod.addInstruction(
            0,
            "invoke-static {p1, p2}, $LEGACY_DIAGNOSTICS->showIfUnknown(Landroid/view/View;Ljava/lang/String;)V"
        )
    }
}
