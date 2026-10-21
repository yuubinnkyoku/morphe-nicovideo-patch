package app.yuubinnkyoku.patches.nicovideo

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.bytecodePatch
import app.yuubinnkyoku.patches.shared.NICOVIDEO_COMPATIBILITY

/**
 * niconico 9.16.3 (479): VideoPlayerFragment.T()Z checks whether the
 * Activity is finishing, the background_play preference or active PiP,
 * the cached membership bit z0, and the playback restriction flag K.b.
 *
 * Only the membership bit used by this decision is overridden.
 * onStop() still uses the existing SeamlessPlayerService path.
 */
private object BackgroundPlaybackEligibilityFingerprint : Fingerprint(
    definingClass = "Ljp/nicovideo/android/ui/player/VideoPlayerFragment;",
    name = "T",
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(smali = "Ljp/nicovideo/android/ui/player/VideoPlayerFragment;->z0:Z")
    )
)

@Suppress("unused")
val backgroundPlaybackPatch = bytecodePatch(
    name = "Enable background playback",
    description = "Uses the app's existing background audio service when the background-play setting is enabled.",
    default = true
) {
    compatibleWith(NICOVIDEO_COMPATIBILITY)

    execute {
        val fingerprint = BackgroundPlaybackEligibilityFingerprint
        // Original: iget-boolean v0, p0, ...VideoPlayerFragment;->z0:Z
        // Keep the preference, Activity, PiP and playback-restriction gates.
        val membershipReadIndex = fingerprint.instructionMatches[0].index
        fingerprint.method.addInstruction(membershipReadIndex + 1, "const/4 v0, 0x1")
    }
}
