package app.yuubinnkyoku.patches.nicovideo

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.yuubinnkyoku.patches.shared.NICOVIDEO_COMPATIBILITY

/**
 * niconico 9.16.3 (479): c86.a()Z checks the cached membership boolean,
 * then Android's FEATURE_PICTURE_IN_PICTURE and AppOps permission.
 *
 * Only the membership result is overridden; both OS checks remain intact.
 */
private object PictureInPictureEligibilityFingerprint : Fingerprint(
    definingClass = "Lc86;",
    name = "a",
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(smali = "Lc86;->b:Z"),
        string("android.software.picture_in_picture")
    )
)

@Suppress("unused")
val pictureInPicturePatch = bytecodePatch(
    name = "Enable picture-in-picture",
    description = "Enables the app's existing PiP playback path while retaining Android's PiP support and permission checks.",
    default = true
) {
    compatibleWith(NICOVIDEO_COMPATIBILITY)

    execute {
        val fingerprint = PictureInPictureEligibilityFingerprint
        // Original: iget-boolean v0, p0, Lc86;->b:Z
        // The next instruction branches on v0. Replace only that boolean's
        // effective value, without skipping the OS feature/AppOps checks.
        val membershipReadIndex = fingerprint.instructionMatches[0].index
        fingerprint.method.addInstruction(membershipReadIndex + 1, "const/4 v0, 0x1")
    }
}
