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
 * Read-only diagnostics. Never writes App Check tokens, OAuth codes, session
 * values, PKCE verifiers, HTTP headers or response bodies to logs or clipboard.
 */
@SuppressWarnings({"deprecation", "unused"})
public final class LoginDiagnostics {
    private LoginDiagnostics() { }

    // Latest observation from this process; expected to be from the current
    // login attempt. Values are strictly non-secret, fixed-state metadata.
    private static volatile String appCheckAcquisition = "not observed";
    private static volatile int appCheckInvocationCount = 0;
    private static volatile String appCheckErrorType = "none";
    private static volatile String appCheckCauseChain = "not observed";
    private static volatile String appCheckErrorIndicators = "not observed";
    private static volatile String passportTokenArgument = "not observed";
    private static volatile String passportHttpStatus = "not observed";

    public static void recordAppCheckEntered() {
        appCheckInvocationCount++;
        appCheckAcquisition = "entered (outcome not observed yet)";
        appCheckErrorType = "none";
        appCheckCauseChain = "none";
        appCheckErrorIndicators = "none";
    }

    public static void recordAppCheckResult(String token) {
        appCheckAcquisition = token == null ? "null (no token returned)"
            : token.length() == 0 ? "empty" : "nonempty token returned";
        appCheckErrorType = "none";
        appCheckCauseChain = "none";
        appCheckErrorIndicators = "none";
        passportTokenArgument = "not observed";
        passportHttpStatus = "not observed";
    }

    public static void recordAppCheckFailure(Throwable error) {
        appCheckAcquisition = "failed (null fallback)";
        appCheckErrorType = error == null ? "unknown"
            : error.getClass().getSimpleName();
        appCheckCauseChain = describeCauseChain(error);
        appCheckErrorIndicators = describeSafeIndicators(error);
        passportTokenArgument = "not observed";
        passportHttpStatus = "not observed";
    }

    /**
     * Only exception class names and classified error indicators leave this
     * method; raw Throwable messages can contain credentials and are withheld.
     */
    private static String describeCauseChain(Throwable error) {
        if (error == null) return "unknown";
        StringBuilder chain = new StringBuilder();
        Throwable current = error;
        for (int i = 0; i < 6 && current != null; i++) {
            if (i > 0) chain.append(" -> ");
            String name = current.getClass().getName();
            chain.append(name.length() < 150 ? name : "unusual class name");
            Throwable next;
            try { next = current.getCause(); }
            catch (RuntimeException ignored) { break; }
            if (next == current) break;
            current = next;
        }
        return chain.toString();
    }

    private static String describeSafeIndicators(Throwable error) {
        StringBuilder indicators = new StringBuilder();
        Throwable current = error;
        for (int i = 0; i < 6 && current != null; i++) {
            String message;
            try { message = current.getMessage(); }
            catch (RuntimeException ignored) { message = null; }
            if (message != null) {
                String lower = message.toLowerCase(Locale.US);
                String category = classifyMessage(lower);
                if (!"unclassified".equals(category)) {
                    if (indicators.length() > 0) indicators.append("; ");
                    indicators.append(category);
                }
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "(?i)integrity api error [(](-?[0-9]{1,4})[)]"
                ).matcher(message);
                if (matcher.find()) {
                    if (indicators.length() > 0) indicators.append("; ");
                    indicators.append("integrity-code=").append(matcher.group(1));
                }
            }
            Throwable next;
            try { next = current.getCause(); }
            catch (RuntimeException ignored) { break; }
            if (next == current) break;
            current = next;
        }
        return indicators.length() == 0 ? "unclassified (message withheld)" : indicators.toString();
    }

    private static String classifyMessage(String text) {
        if (text.contains("no appcheckprovider installed"))
            return "provider_not_installed";
        if (text.contains("integrity api error"))
            return "play_integrity_api_error";
        if (text.contains("app attestation failed"))
            return "app_attestation_failed";
        if (text.contains("firebase installations"))
            return "firebase_installations_error";
        if (text.contains("not registered"))
            return "app_not_registered";
        if (text.contains("permission denied") || text.contains("unauthorized"))
            return "permission_denied";
        if (text.contains("signature") || text.contains("signing certificate"))
            return "signature_related";
        if (text.contains("too many attempts") || text.contains("throttled")
                || text.contains("too many requests"))
            return "rate_limited";
        if (text.contains("timeout") || text.contains("unknownhost")
                || text.contains("could not connect"))
            return "network_error";
        if (text.contains("unexpected server response"))
            return "unexpected_server_response";
        if (text.contains("403") || text.contains("forbidden"))
            return "http_forbidden";
        if (text.contains("appcheck") || text.contains("app check"))
            return "app_check_error";
        return "unclassified";
    }

    public static void recordPassportToken(String token) {
        passportTokenArgument = token == null ? "null"
            : token.length() == 0 ? "empty" : "nonempty";
    }

    public static void recordPassportStatus(int httpStatus) {
        passportHttpStatus = String.valueOf(httpStatus);
    }

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
                // A diagnostic must not crash login UI.
            }
        };
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
        StringBuilder report = new StringBuilder(850);
        report.append("Morphe niconico login diagnostics v4\n");
        report.append("Time: ")
            .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()))
            .append('\n');
        report.append("Login error code: ").append(code == null ? "unknown" : code.toString()).append('\n');
        report.append("Shown message: ").append(message == null ? "none" : message).append('\n');
        report.append("App Check method calls: ").append(appCheckInvocationCount).append('\n');
        report.append("App Check acquisition: ").append(appCheckAcquisition).append('\n');
        report.append("App Check exception type: ").append(appCheckErrorType).append('\n');
        report.append("App Check exception chain: ").append(appCheckCauseChain).append('\n');
        report.append("App Check error indicators: ").append(appCheckErrorIndicators).append('\n');
        report.append("Passport request appCheckToken argument: ").append(passportTokenArgument).append('\n');
        report.append("Passport API HTTP status: ").append(passportHttpStatus).append('\n');
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
        report.append("Play Integrity verdict: not available to app diagnostics\n");
        report.append("Note: 'nonempty' means token is present, not that server accepted it.\n");
        report.append("Note: Request argument presence does not prove network transmission.\n");
        report.append("Note: No token, nonce, cookie, auth code or response body is captured.\n");
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
            if (signer == null) return "unavailable";
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signer.toByteArray());
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b & 0xff));
            return hex.toString();
        } catch (Exception ignored) {
            return "unavailable";
        }
    }
}
