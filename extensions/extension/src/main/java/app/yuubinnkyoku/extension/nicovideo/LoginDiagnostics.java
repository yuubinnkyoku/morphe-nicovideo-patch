package app.yuubinnkyoku.extension.nicovideo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Opt-in diagnostics for niconico login failures.
 *
 * This deliberately does not capture logcat, auth tokens, cookies, nonces,
 * passwords, server response bodies, or requests. Android does not permit an
 * ordinary app to read arbitrary system logcat.
 */
@SuppressWarnings({"deprecation", "unused"})
public final class LoginDiagnostics {
    private LoginDiagnostics() { }

    public static void show(Context context, Object code, String message) {
        if (context == null) return;

        final String report = makeReport(context, code, message);
        Runnable present = () -> {
            Activity activity = findActivity(context);
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            try {
                new AlertDialog.Builder(activity)
                    .setTitle("ニコニコ動画・ログイン診断")
                    .setMessage(report)
                    .setPositiveButton("ログをコピー", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Niconico login diagnostics", report));
                            Toast.makeText(activity, "診断ログをコピーしました", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("閉じる", null)
                    .show();
            } catch (RuntimeException ignored) {
                // Diagnostics must never crash login UI.
            }
        };

        // The original callback normally runs on the UI thread, but do not
        // assume that on future builds.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            present.run();
        } else {
            new Handler(Looper.getMainLooper()).post(present);
        }
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        for (int i = 0; current != null && i < 12; i++) {
            if (current instanceof Activity) return (Activity) current;
            if (!(current instanceof ContextWrapper)) break;
            Context next = ((ContextWrapper) current).getBaseContext();
            if (current == next) break;
            current = next;
        }
        return null;
    }

    private static String makeReport(Context context, Object code, String message) {
        PackageManager pm = context.getPackageManager();
        StringBuilder report = new StringBuilder(512);
        report.append("Morphe niconico login diagnostics v1\n");
        report.append("Time: ")
            .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()))
            .append('\n');
        report.append("Login error code: ").append(code == null ? "unknown" : code.toString()).append('\n');
        report.append("Shown message: ").append(message == null ? "none" : message).append('\n');
        report.append("Package: ").append(context.getPackageName()).append('\n');
        report.append("Android SDK: ").append(Build.VERSION.SDK_INT).append('\n');
        report.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append('\n');
        report.append("App version: ").append(version(pm, context.getPackageName())).append('\n');
        report.append("GMS version: ").append(version(pm, "com.google.android.gms")).append('\n');
        report.append("Play Store version: ").append(version(pm, "com.android.vending")).append('\n');
        try {
            report.append("Installer: ").append(pm.getInstallerPackageName(context.getPackageName())).append('\n');
        } catch (RuntimeException ignored) {
            report.append("Installer: unavailable\n");
        }
        report.append("App signing certificate SHA-256: ").append(signingSha256(pm, context.getPackageName())).append('\n');
        report.append("Play Integrity verdict: not accessible from this UI\n");
        report.append("Note: Only the mapped login error and non-secret environment metadata are included.\n");
        return report.toString();
    }

    private static String version(PackageManager pm, String packageName) {
        try {
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            return info.versionName + " (" + info.versionCode + ")";
        } catch (Exception ignored) {
            return "unavailable";
        }
    }

    private static String signingSha256(PackageManager pm, String packageName) {
        try {
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            Signature signer = null;
            if (info.signingInfo != null) {
                Signature[] signers = info.signingInfo.getApkContentsSigners();
                if (signers != null && signers.length > 0) signer = signers[0];
            }
            if (signer == null) {
                return "unavailable";
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signer.toByteArray());
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b & 0xff));
            return hex.toString();
        } catch (Exception ignored) {
            return "unavailable";
        }
    }
}
