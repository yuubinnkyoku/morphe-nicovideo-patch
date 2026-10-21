package app.yuubinnkyoku.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

// Fingerprints are verified against the 9.16.3 (479) base APK from
// the original split-APK package. Newer versions are not assumed compatible.
val NICOVIDEO_COMPATIBILITY = Compatibility(
    name = "ニコニコ動画",
    packageName = "jp.nicovideo.android",
    apkFileType = ApkFileType.APKS,
    appIconColor = 0x252525,
    targets = listOf(
        AppTarget(
            version = "9.16.3",
            versionCode = 479,
            isExperimental = true
        )
    )
)
