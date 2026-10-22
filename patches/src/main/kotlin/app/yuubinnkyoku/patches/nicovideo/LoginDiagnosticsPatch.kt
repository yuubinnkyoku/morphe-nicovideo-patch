package app.yuubinnkyoku.patches.nicovideo

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.yuubinnkyoku.patches.shared.NICOVIDEO_COMPATIBILITY
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val DIAGNOSTICS = "Lapp/yuubinnkyoku/extension/nicovideo/LoginDiagnostics;"

/** Stock login error UI callback: maps the account API error to WL-E30 / WL-E31. */
private object LoginErrorUiFingerprint : Fingerprint(
    definingClass = "Lyja;",
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(smali = "Lzy6;->error_login_api_general_app_check_authentication_error:I")
    )
)

/** The actual Firebase App Check token acquisition coroutine in niconico 9.16.3. */
private object AppCheckAcquisitionFingerprint : Fingerprint(
    definingClass = "Lwm5;",
    name = "D0",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Lkl;", "Lsc1;"),
    filters = listOf(string("GDN-17185"))
)

/** Kotlin coroutine creates IssueAccountPassportRequest(code, verifier, redirect, appCheckToken). */
private object PassportRequestFingerprint : Fingerprint(
    definingClass = "Lz6;",
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;")
)

/** Account Passport issue call: HTTP status is read from Lpd7.d. */
private object PassportApiFingerprint : Fingerprint(
    definingClass = "Li7;",
    name = "a",
    returnType = "Ljava/lang/Object;",
    parameters = listOf(
        "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;",
        "Ljava/lang/String;", "Lsc1;"
    ),
    filters = listOf(fieldAccess(smali = "Lpd7;->d:I"))
)

/**
 * Read-only, opt-in observability.
 *
 * Captures only:
 * - whether Firebase App Check acquisition returned a nonempty token or failed;
 * - whether the Account Passport request was constructed with a token argument;
 * - the numeric HTTP status received by the Account Passport client.
 *
 * No token text, authorization code, PKCE verifier, nonce, cookies, or response
 * bodies are recorded. App Check and backend enforcement are NOT modified.
 */
@Suppress("unused")
val loginDiagnosticsPatch = bytecodePatch(
    name = "Login diagnostics: copy error report",
    description = "Copy a safe login diagnostic report with App Check token presence, Account Passport request token presence and HTTP status. Never copies tokens.",
    default = false
) {
    compatibleWith(NICOVIDEO_COMPATIBILITY)
    extendWith("extensions/extension.mpe")

    execute {
        // 1. Capture App Check acquisition (successful string / null / error type).
        val appCheckMethod = AppCheckAcquisitionFingerprint.method
        val appCheckInstructions = appCheckMethod.implementation!!.instructions.toList()

        val tokenFieldIndex = appCheckInstructions.indexOfFirst {
            val field = (it as? ReferenceInstruction)?.reference as? FieldReference
            field?.definingClass == "Lgq1;" && field.name == "a" && field.type == "Ljava/lang/String;"
        }
        val errorBuilderIndex = appCheckInstructions.indexOfFirst {
            val reference = (it as? ReferenceInstruction)?.reference
            it.opcode == com.android.tools.smali.dexlib2.Opcode.NEW_INSTANCE &&
                (reference as? com.android.tools.smali.dexlib2.iface.reference.TypeReference)?.type == "Lzs;"
        }
        if (tokenFieldIndex < 0)
            throw PatchException("Could not locate App Check result field (Lgq1;->a)")
        if (errorBuilderIndex < 0)
            throw PatchException("Could not locate App Check error logger (new-instance Lzs;)")

        // High instruction indexes first so earlier offsets remain correct.
        appCheckMethod.addInstruction(
            errorBuilderIndex,
            "invoke-static {p0}, $DIAGNOSTICS->recordAppCheckFailure(Ljava/lang/Throwable;)V"
        )
        appCheckMethod.addInstruction(
            tokenFieldIndex + 1,
            "invoke-static {p0}, $DIAGNOSTICS->recordAppCheckResult(Ljava/lang/String;)V"
        )

        // 2. Observe the token argument passed into the serialized API request.
        val requestMethod = PassportRequestFingerprint.method
        val requestInstructions = requestMethod.implementation!!.instructions.toList()
        val ctorIndex = requestInstructions.indexOfFirst {
            val method = (it as? ReferenceInstruction)?.reference as? MethodReference
            method?.definingClass ==
                "Ljp/co/dwango/android/nicoca/account/model/IssueAccountPassportRequest;" &&
                method.name == "<init>" && method.parameterTypes.size == 4
        }
        if (ctorIndex < 0)
            throw PatchException("IssueAccountPassportRequest constructor not found")
        val ctor = requestInstructions[ctorIndex] as? FiveRegisterInstruction
            ?: throw PatchException("Unexpected IssueAccountPassportRequest argument register format")
        // The 5th register in invoke-direct {object, code, verifier, redirect, token}
        // contains the appCheckToken, by definition of IssueAccountPassportRequest.
        requestMethod.addInstruction(
            ctorIndex,
            "invoke-static {v${ctor.registerG}}, $DIAGNOSTICS->recordPassportToken(Ljava/lang/String;)V"
        )

        // 3. Observe the HTTP status of the Account Passport response, not its body.
        val passportApiMethod = PassportApiFingerprint.method
        val apiInstructions = passportApiMethod.implementation!!.instructions.toList()
        val statusIndex = apiInstructions.indexOfFirst {
            val field = (it as? ReferenceInstruction)?.reference as? FieldReference
            field?.definingClass == "Lpd7;" && field.name == "d" && field.type == "I"
        }
        if (statusIndex < 0)
            throw PatchException("Account Passport HTTP status field not found")
        val statusInstruction = apiInstructions[statusIndex] as? TwoRegisterInstruction
            ?: throw PatchException("Unexpected HTTP status register format")
        passportApiMethod.addInstruction(
            statusIndex + 1,
            "invoke-static {v${statusInstruction.registerA}}, $DIAGNOSTICS->recordPassportStatus(I)V"
        )

        // 4. Present the report next to the existing login failure UI.
        val loginUiMethod = LoginErrorUiFingerprint.method
        val loginInstructions = loginUiMethod.implementation!!.instructions.toList()
        val stockErrorUiCall = loginInstructions.indexOfFirst {
            val method = (it as? ReferenceInstruction)?.reference as? MethodReference
            method?.definingClass == "Ldka;" && method.name == "i" &&
                method.parameterTypes.size == 3
        }
        if (stockErrorUiCall < 0)
            throw PatchException("Niconico login error UI call not found")
        loginUiMethod.addInstruction(
            stockErrorUiCall + 1,
            "invoke-static {v3, v0, p0}, $DIAGNOSTICS->show(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/String;)V"
        )
    }
}
