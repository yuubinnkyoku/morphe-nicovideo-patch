package app.yuubinnkyoku.patches.nicovideo

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.bytecodePatch
import app.yuubinnkyoku.patches.shared.NICOVIDEO_COMPATIBILITY
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * niconico 9.16.3 (479): the login callback yja.invoke() maps API errors to
 * error codes and shows the stock snackbar via dka.i().
 *
 * After the original UI error is displayed, offer an in-app copyable diagnostic
 * report with the mapped error code and non-secret runtime environment details.
 * No tokens, credentials, nonces, or raw network responses are captured.
 */
private object LoginErrorUiFingerprint : Fingerprint(
    definingClass = "Lyja;",
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(smali = "Lzy6;->error_login_api_general_app_check_authentication_error:I")
    )
)

@Suppress("unused")
val loginDiagnosticsPatch = bytecodePatch(
    name = "Login diagnostics: copy error report",
    description = "Show an in-app 'Copy log' dialog when login fails, with error code and signing/GMS environment details. No tokens or passwords are recorded.",
    default = false
) {
    compatibleWith(NICOVIDEO_COMPATIBILITY)
    extendWith("extensions/extension.mpe")

    execute {
        val method = LoginErrorUiFingerprint.method
        val instructions = method.implementation!!.instructions.toList()
        val stockLoginErrorUiCall = instructions.indexOfFirst { instruction ->
            val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            ref?.definingClass == "Ldka;" && ref.name == "i" &&
                ref.parameterTypes.size == 3
        }
        if (stockLoginErrorUiCall < 0) {
            throw app.morphe.patcher.patch.PatchException(
                "niconico 9.16.3 login error UI call was not found"
            )
        }

        // At this point:
        // v3 = Context; v0 = mapped error code (e.g., WL_E30 / WL_E31);
        // p0 = localized message. All are already available in the original method.
        method.addInstruction(
            stockLoginErrorUiCall + 1,
            "invoke-static {v3, v0, p0}, Lapp/yuubinnkyoku/extension/nicovideo/LoginDiagnostics;->show(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/String;)V"
        )
    }
}
