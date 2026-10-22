package app.yuubinnkyoku.patches.nicovideo

import app.morphe.patcher.patch.bytecodePatch
import app.yuubinnkyoku.patches.shared.NICOVIDEO_LEGACY_855_COMPATIBILITY

/**
 * Diagnostic for the pre-9.x login API in niconico 8.55.0 (450).
 *
 * This patch deliberately does not alter classes, manifest, resources or
 * network behavior. It allows a correctly repacked and newly signed 8.55.0
 * build to be installed for testing whether the legacy login flow still
 * works with the current niconico server.
 *
 * Caveat: Morphe's APK packaging and signing still change the artifact.
 * This experiment does not turn 8.55.0 into a recognized Play Store build.
 */
@Suppress("unused")
val legacy855ResigningDiagnosticPatch = bytecodePatch(
    name = "niconico 8.55.0: re-sign/login test (no changes)",
    description = "For niconico 8.55.0 only. Repack/re-sign without bytecode edits to check whether its older login API still authenticates. Experimental.",
    default = false
) {
    compatibleWith(NICOVIDEO_LEGACY_855_COMPATIBILITY)

    execute {
        // Deliberately empty. Do not apply 9.16.3 fingerprints here.
    }
}
