package app.yuubinnkyoku.extension.nicovideo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Diagnostic of the legacy AccountPassport error mapping in 8.55.0. */
@SuppressWarnings({"deprecation", "unused"})
public final class LegacyLoginDiagnostics {
    private static volatile String errorType = "not observed";
    private static volatile String errorCode = "not observed";
    private static volatile String oauthHttpStatus = "not observed";
    private static volatile String passportHttpStatus = "not observed";
    private LegacyLoginDiagnostics() { }

    public static void recordErrorType(Object error) {
        errorType = error == null ? "null" : error.getClass().getName();
        errorCode = "not observed";
    }

    /** Records only the numeric HTTP status; never receives a response body. */
    public static void recordOauthHttpStatus(int status) {
        oauthHttpStatus = status >= 100 && status <= 599 ? String.valueOf(status) : "invalid";
        passportHttpStatus = "not observed";
    }

    public static void recordPassportHttpStatus(int status) {
        passportHttpStatus = status >= 100 && status <= 599 ? String.valueOf(status) : "invalid";
    }

    public static void recordErrorCode(String rawErrorCode) {
        // Only a protocol-style error identifier is allowed into the report.
        // This also avoids exposing unrelated server response details.
        if (rawErrorCode == null) {
            errorCode = "null";
            return;
        }
        if (rawErrorCode.length() == 0 || rawErrorCode.length() > 96
                || !rawErrorCode.matches("[A-Za-z0-9_.-]+")) {
            errorCode = "non-identifier (redacted)";
            return;
        }
        errorCode = rawErrorCode;
    }

    public static void showIfUnknown(View view, String shownMessage) {
        if (view == null || shownMessage == null || !shownMessage.contains("WL-EU")) return;
        final Context context = view.getContext();
        if (context == null) return;
        final String report = buildReport(context);
        new Handler(Looper.getMainLooper()).post(() -> {
            Activity activity = findActivity(context);
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            try {
                new AlertDialog.Builder(activity)
                    .setTitle("ニコニコ動画 8.55.0 認証診断")
                    .setMessage(report)
                    .setPositiveButton("ログをコピー", (dialog, which) -> {
                        ClipboardManager manager = (ClipboardManager)
                            activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (manager != null) {
                            manager.setPrimaryClip(ClipData.newPlainText(
                                "niconico 8.55.0 legacy login diagnostics", report));
                            Toast.makeText(activity, "診断ログをコピーしました", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("閉じる", null)
                    .show();
            } catch (RuntimeException ignored) {
                // Do not affect app login just because the diagnostic UI fails.
            }
        });
    }

    private static String buildReport(Context context) {
        StringBuilder s = new StringBuilder(400);
        s.append("Morphe niconico 8.55.0 legacy login diagnostics v2\n");
        s.append("Time: ").append(new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append('\n');
        s.append("Displayed login error: WL-EU\n");
        s.append("Account Passport error type: ").append(errorType).append('\n');
        s.append("Account Passport error code: ").append(errorCode).append('\n');
        s.append("OAuth token API HTTP status: ").append(oauthHttpStatus).append('\n');
        s.append("Account Passport API HTTP status: ").append(passportHttpStatus).append('\n');
        s.append("Package: ").append(context.getPackageName()).append('\n');
        s.append("Android SDK: ").append(Build.VERSION.SDK_INT).append('\n');
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), 0);
            s.append("App version: ").append(info.versionName).append(" (")
                .append(info.versionCode).append(")\n");
        } catch (Exception e) {
            s.append("App version: unavailable\n");
        }
        s.append("Note: Only a sanitized API error identifier and class are recorded.\n");
        s.append("Note: Only numeric HTTP codes are observed from Retrofit responses.\n");
        s.append("Note: No tokens, request headers or response bodies are logged.\n");
        return s.toString();
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        for (int i = 0; current != null && i < 12; i++) {
            if (current instanceof Activity) return (Activity) current;
            if (!(current instanceof ContextWrapper)) return null;
            Context next = ((ContextWrapper) current).getBaseContext();
            if (next == current) return null;
            current = next;
        }
        return null;
    }
}
