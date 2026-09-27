package libv2ray;

/** Compatibility shim for code that still imports libv2ray.Libv2ray. */
public final class Libv2ray {
    private Libv2ray() {}

    public static String checkVersionX() {
        try {
            System.loadLibrary("gojni");
        } catch (Throwable ignored) {}
        try {
            Class<?> cls = Class.forName("libcore.Libcore");
            try { return String.valueOf(cls.getMethod("versionBox").invoke(null)); } catch (Throwable ignored) {}
            try { return String.valueOf(cls.getMethod("VersionBox").invoke(null)); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return "sing-box/libcore";
    }
}
