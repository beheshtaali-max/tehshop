package dev.dev7.lib.v2ray.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class SafeLog {
    private static volatile Context appContext;

    private SafeLog() {}

    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static void d(String tag, String msg) {
        write("D", tag, msg, null);
    }

    public static void i(String tag, String msg) {
        write("I", tag, msg, null);
    }

    public static void w(String tag, String msg) {
        write("W", tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable t) {
        write("W", tag, msg, t);
    }

    public static void e(String tag, String msg) {
        write("E", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        write("E", tag, msg, t);
    }

    private static void write(String level, String tag, String msg, Throwable t) {
        String safeMsg = mask(msg);

        switch (level) {
            case "D":
                Log.d(tag, safeMsg);
                break;
            case "I":
                Log.i(tag, safeMsg);
                break;
            case "W":
                Log.w(tag, safeMsg, t);
                break;
            default:
                Log.e(tag, safeMsg, t);
                break;
        }

        StringBuilder line = new StringBuilder();
        line.append(new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
        ).format(new Date()));

        line.append(" [").append(level).append("] ");
        line.append(tag).append(": ");
        line.append(safeMsg == null ? "" : safeMsg);

        if (t != null) {
            line.append("\n");
            java.io.StringWriter sw = new java.io.StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            pw.flush();
            line.append(mask(sw.toString()));
        }

        appendToFile(line.toString());
    }

    private static synchronized void appendToFile(String text) {
        try {
            Context context = appContext;
            if (context == null) return;

            File dir = new File(context.getFilesDir(), "logs");
            if (!dir.exists() && !dir.mkdirs()) return;

            File file = new File(dir, "teh-vpn.log");

            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(text);
                writer.write("\n");
            }

            // Keep the log from growing indefinitely.
            if (file.length() > 2 * 1024 * 1024) {
                File old = new File(dir, "teh-vpn.log.old");
                if (old.exists()) old.delete();
                if (!file.renameTo(old)) {
                    // If rename fails, simply continue using the current log.
                }
            }
        } catch (Throwable ignored) {
            // Logging must never crash the VPN service.
        }
    }

    public static String readLog(Context context) {
        try {
            File file = new File(
                    context.getApplicationContext().getFilesDir(),
                    "logs/teh-vpn.log"
            );

            if (!file.exists()) {
                return "";
            }

            return new String(
                    java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        } catch (Throwable e) {
            return "";
        }
    }

    public static void clearLog(Context context) {
        try {
            File file = new File(
                    context.getApplicationContext().getFilesDir(),
                    "logs/teh-vpn.log"
            );

            if (file.exists()) {
                file.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    public static String mask(String s) {
        if (s == null) return null;

        String out = s;

        out = out.replaceAll(
                "(?i)(\\\"password\\\"\\s*:\\s*\\\")[^\\\"]*",
                "$1***"
        );

        out = out.replaceAll(
                "(?i)(password=)[^\\s&]+",
                "$1***"
        );

        out = out.replaceAll(
                "(?i)(passwd=)[^\\s&]+",
                "$1***"
        );

        out = out.replaceAll(
                "(?i)(pwd=)[^\\s&]+",
                "$1***"
        );

        out = out.replaceAll(
                "(?i)(private_key\\\"\\s*:\\s*\\\")[^\\\"]*",
                "$1***"
        );

        return out;
    }
}
