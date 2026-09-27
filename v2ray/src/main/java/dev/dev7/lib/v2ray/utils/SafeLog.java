package dev.dev7.lib.v2ray.utils;

import android.util.Log;

public final class SafeLog {
    private SafeLog() {}

    public static void d(String tag, String msg) { Log.d(tag, mask(msg)); }
    public static void i(String tag, String msg) { Log.i(tag, mask(msg)); }
    public static void w(String tag, String msg) { Log.w(tag, mask(msg)); }
    public static void w(String tag, String msg, Throwable t) { Log.w(tag, mask(msg), t); }
    public static void e(String tag, String msg) { Log.e(tag, mask(msg)); }
    public static void e(String tag, String msg, Throwable t) { Log.e(tag, mask(msg), t); }

    public static String mask(String s) {
        if (s == null) return null;
        String out = s;
        out = out.replaceAll("(?i)(\\\"password\\\"\\s*:\\s*\\\")[^\\\"]*", "$1***");
        out = out.replaceAll("(?i)(password=)[^\\s&]+", "$1***");
        out = out.replaceAll("(?i)(passwd=)[^\\s&]+", "$1***");
        out = out.replaceAll("(?i)(pwd=)[^\\s&]+", "$1***");
        out = out.replaceAll("(?i)(private_key\\\"\\s*:\\s*\\\")[^\\\"]*", "$1***");
        return out;
    }
}
