package app.yuubinnkyoku.patches.nicovideo

import app.morphe.patcher.patch.bytecodePatch
import app.yuubinnkyoku.patches.shared.NICOVIDEO_COMPATIBILITY

/**
 * Diagnostic patch for isolating APK re-signing effects.
 *
 * This intentionally performs no bytecode or resource modifications.
 * Select this patch ALONE, then let Morphe process and sign the APK as usual.
 * This tests whether the signed/repacked APK can authenticate without
 * the PiP or background-playback modifications.
 *
 * An unchanged patch body does not imply bit-for-bit identical APK output.
 */
@Suppress("unused")
val resigningDiagnosticPatch = bytecodePatch(
    name = "Diagnostic: re-sign only (no changes)",
    description = "Diagnostic only: no app behavior changes. Select this alone to isolate APK repacking/re-signing from the playback patches.",
    default = false,
) {
    compatibleWith(NICOVIDEO_COMPATIBILITY)
    execute {
        // Intentionally empty.
    }
}
