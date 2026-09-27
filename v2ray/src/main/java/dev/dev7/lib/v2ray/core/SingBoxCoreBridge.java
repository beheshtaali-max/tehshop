package dev.dev7.lib.v2ray.core;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import dev.dev7.lib.v2ray.services.V2rayVPNService;
import dev.dev7.lib.v2ray.utils.SafeLog;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class SingBoxCoreBridge {
    private static final String TAG = "SingBoxCoreBridge";

    private final V2rayVPNService service;
    private final Context appContext;

    private Class<?> libcoreClass;
    private Class<?> nb4aInterfaceClass;
    private Class<?> boxPlatformInterfaceClass;
    private Class<?> localDnsTransportClass;

    private Object nb4aProxy;
    private Object platformProxy;
    private Object localDnsProxy;
    private Object boxInstance;
    private boolean initialized;

    private final AtomicInteger platformCallCount = new AtomicInteger();
    private final AtomicInteger nb4aCallCount = new AtomicInteger();
    private final AtomicInteger dnsCallCount = new AtomicInteger();

    public SingBoxCoreBridge(V2rayVPNService service) {
        this.service = service;
        this.appContext = service.getApplicationContext();
    }

    public synchronized void init() throws Exception {
        if (initialized) {
            SafeLog.i(TAG, "init() skipped: already initialized");
            return;
        }

        SafeLog.i(TAG, "init() begin. process=" + currentProcessName() + " files=" + appContext.getFilesDir().getAbsolutePath());

        try {
            System.loadLibrary("gojni");
            SafeLog.i(TAG, "System.loadLibrary(gojni) OK");
        } catch (UnsatisfiedLinkError e) {
            SafeLog.w(TAG, "System.loadLibrary(gojni) failed or already handled by libcore classes: " + e.getMessage());
        }

        libcoreClass = loadClass("libcore.Libcore");
        nb4aInterfaceClass = loadClass("libcore.NB4AInterface");
        boxPlatformInterfaceClass = loadClass("libcore.BoxPlatformInterface");
        localDnsTransportClass = loadClass("libcore.LocalDNSTransport");

        logClassMethods(libcoreClass, "Libcore");
        logClassMethods(nb4aInterfaceClass, "NB4AInterface");
        logClassMethods(boxPlatformInterfaceClass, "BoxPlatformInterface");
        logClassMethods(localDnsTransportClass, "LocalDNSTransport");

        nb4aProxy = Proxy.newProxyInstance(
                nb4aInterfaceClass.getClassLoader(),
                new Class[]{nb4aInterfaceClass},
                new Nb4aHandler()
        );
        platformProxy = Proxy.newProxyInstance(
                boxPlatformInterfaceClass.getClassLoader(),
                new Class[]{boxPlatformInterfaceClass},
                new PlatformHandler()
        );
        localDnsProxy = Proxy.newProxyInstance(
                localDnsTransportClass.getClassLoader(),
                new Class[]{localDnsTransportClass},
                new LocalDnsHandler()
        );
        SafeLog.i(TAG, "Proxy objects created");

        File internalAssets = new File(appContext.getFilesDir(), "internal-assets");
        File externalAssets = new File(appContext.getFilesDir(), "external-assets");
        File cache = appContext.getCacheDir();
        ensureDir(internalAssets, "internalAssets");
        ensureDir(externalAssets, "externalAssets");
        ensureDir(cache, "cache");

        // IMPORTANT:
        // NekoBox libcore treats any process name ending with ":bg" as its full
        // official background service and starts extra asset/background init work.
        // In this minimal SSH-only app we do not ship the complete NekoBox assets
        // and do not need that path. Passing the plain package name keeps libcore
        // out of bg-mode and avoids the silent process death seen right after InitCore.
        String actualProcessName = currentProcessName();
        String processName = appContext.getPackageName();
        SafeLog.i(TAG, "InitCore logicalProcess=" + processName + " actualProcess=" + actualProcessName + " forceNonBg=true");

        Object[] args = new Object[]{
                processName,
                cache.getAbsolutePath(),
                ensureTrailingSlash(internalAssets),
                ensureTrailingSlash(externalAssets),
                512,
                true,
                nb4aProxy,
                platformProxy,
                localDnsProxy
        };

        SafeLog.i(TAG, "Calling Libcore.InitCore process=" + processName
                + " cache=" + cache.getAbsolutePath()
                + " internalAssets=" + ensureTrailingSlash(internalAssets)
                + " externalAssets=" + ensureTrailingSlash(externalAssets));

        Method initCore = findMethod(libcoreClass, new String[]{"InitCore", "initCore"}, 9);
        invokeStatic(initCore, args);

        initialized = true;
        SafeLog.i(TAG, "Libcore.InitCore OK");
    }

    public synchronized void start(String configJson) throws Exception {
        SafeLog.i(TAG, "start() begin");
        init();
        stop();

        SafeLog.d(TAG, "Config passed to core:\n" + configJson);

        Method newInstance = findMethodFlexible(libcoreClass, new String[]{"NewSingBoxInstance", "newSingBoxInstance"}, 2, 1);
        SafeLog.i(TAG, "Calling " + methodLabel(newInstance));
        if (newInstance.getParameterTypes().length == 2) {
            boxInstance = invokeStatic(newInstance, configJson, localDnsProxy);
        } else {
            boxInstance = invokeStatic(newInstance, configJson);
        }
        if (boxInstance == null) throw new IllegalStateException("NewSingBoxInstance returned null");

        SafeLog.i(TAG, "NewSingBoxInstance OK. instanceClass=" + boxInstance.getClass().getName());
        logClassMethods(boxInstance.getClass(), "BoxInstance");

        if (methodExists(boxInstance.getClass(), "SetAsMain", "setAsMain")) {
            SafeLog.i(TAG, "Calling BoxInstance.SetAsMain");
            invokeNoArg(boxInstance, "SetAsMain", "setAsMain");
            SafeLog.i(TAG, "BoxInstance.SetAsMain OK");
        } else {
            SafeLog.w(TAG, "BoxInstance.SetAsMain not found; continuing");
        }

        SafeLog.i(TAG, "Calling BoxInstance.Start");
        invokeNoArg(boxInstance, "Start", "start");
        SafeLog.i(TAG, "BoxInstance.Start OK; core should be running now");
    }

    public synchronized void stop() {
        if (boxInstance == null) {
            SafeLog.d(TAG, "stop() skipped: no boxInstance");
            return;
        }
        SafeLog.i(TAG, "Stopping BoxInstance class=" + boxInstance.getClass().getName());
        try {
            if (methodExists(boxInstance.getClass(), "Close", "close")) {
                invokeNoArg(boxInstance, "Close", "close");
                SafeLog.i(TAG, "BoxInstance.Close OK");
            } else if (methodExists(boxInstance.getClass(), "Stop", "stop")) {
                invokeNoArg(boxInstance, "Stop", "stop");
                SafeLog.i(TAG, "BoxInstance.Stop OK");
            } else {
                SafeLog.w(TAG, "No Close/Stop method found on BoxInstance");
            }
        } catch (Throwable t) {
            SafeLog.w(TAG, "BoxInstance stop/close failed", t);
        } finally {
            boxInstance = null;
        }
    }

    private final class Nb4aHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            logProxyCall("NB4A", nb4aCallCount, method, args);
            try {
                if (name.equals("UseOfficialAssets") || name.equals("useOfficialAssets")) {
                    return adaptReturn(method.getReturnType(), false);
                }
                if (name.equals("Selector_OnProxySelected") || name.equals("selector_OnProxySelected")) {
                    return adaptReturn(method.getReturnType(), null);
                }
                if (name.toLowerCase().contains("log") || name.toLowerCase().contains("message")) {
                    SafeLog.i(TAG, "NB4A core message: " + argsToString(args));
                    return adaptReturn(method.getReturnType(), null);
                }
                SafeLog.w(TAG, "Unhandled NB4A callback: " + methodLabel(method));
                return defaultValue(method.getReturnType());
            } catch (Throwable t) {
                SafeLog.e(TAG, "NB4A callback failed: " + methodLabel(method), t);
                throw t;
            }
        }
    }

    private final class PlatformHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            logProxyCall("Platform", platformCallCount, method, args);
            try {
                if (name.equals("AutoDetectInterfaceControl") || name.equals("autoDetectInterfaceControl")) {
                    int fd = args != null && args.length > 0 ? ((Number) args[0]).intValue() : -1;
                    boolean ok = service.protectFd(fd);
                    SafeLog.i(TAG, "AutoDetectInterfaceControl protect fd=" + fd + " ok=" + ok);
                    return adaptReturn(method.getReturnType(), null);
                }
                if (name.equals("OpenTun") || name.equals("openTun")) {
                    String tunOptions = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
                    String platformOptions = args != null && args.length > 1 && args[1] != null ? String.valueOf(args[1]) : "";
                    SafeLog.i(TAG, "OpenTun requested. tunOptions=" + tunOptions + " platformOptions=" + platformOptions);
                    int fd = service.openTun(tunOptions, platformOptions);
                    SafeLog.i(TAG, "OpenTun returning fd=" + fd + " returnType=" + method.getReturnType().getName());
                    return adaptReturn(method.getReturnType(), fd);
                }
                if (name.equals("UseProcFS") || name.equals("useProcFS")) {
                    boolean useProc = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
                    SafeLog.i(TAG, "UseProcFS -> " + useProc);
                    return adaptReturn(method.getReturnType(), useProc);
                }
                if (name.equals("FindConnectionOwner") || name.equals("findConnectionOwner")) {
                    int uid = service.getConnectionOwnerUidCompat(args);
                    SafeLog.d(TAG, "FindConnectionOwner -> " + uid);
                    return adaptReturn(method.getReturnType(), uid);
                }
                if (name.equals("PackageNameByUid") || name.equals("packageNameByUid")) {
                    int uid = args != null && args.length > 0 ? ((Number) args[0]).intValue() : 0;
                    String[] packages = appContext.getPackageManager().getPackagesForUid(uid);
                    String result = packages != null && packages.length > 0 ? packages[0] : (uid <= 1000 ? "android" : appContext.getPackageName());
                    SafeLog.d(TAG, "PackageNameByUid uid=" + uid + " -> " + result);
                    return adaptReturn(method.getReturnType(), result);
                }
                if (name.equals("UIDByPackageName") || name.equals("uidByPackageName")) {
                    String packageName = args != null && args.length > 0 ? String.valueOf(args[0]) : "";
                    int uid = 0;
                    try {
                        ApplicationInfo info = appContext.getPackageManager().getApplicationInfo(packageName, 0);
                        uid = info.uid;
                    } catch (Exception ignored) {
                    }
                    SafeLog.d(TAG, "UIDByPackageName package=" + packageName + " -> " + uid);
                    return adaptReturn(method.getReturnType(), uid);
                }
                if (name.equals("WIFIState") || name.equals("wifiState")) {
                    String state = wifiState();
                    SafeLog.d(TAG, "WIFIState -> " + state);
                    return adaptReturn(method.getReturnType(), state);
                }
                SafeLog.w(TAG, "Unhandled Platform callback: " + methodLabel(method));
                return defaultValue(method.getReturnType());
            } catch (Throwable t) {
                SafeLog.e(TAG, "Platform callback failed: " + methodLabel(method), t);
                throw t;
            }
        }
    }

    private final class LocalDnsHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            logProxyCall("LocalDNS", dnsCallCount, method, args);
            try {
                if (name.equals("Raw") || name.equals("raw")) return adaptReturn(method.getReturnType(), false);
                if (name.equals("NetworkHandle") || name.equals("networkHandle")) return adaptReturn(method.getReturnType(), 0L);
                if (name.equals("Lookup") || name.equals("lookup")) {
                    handleLookup(args);
                    return adaptReturn(method.getReturnType(), null);
                }
                if (name.equals("Exchange") || name.equals("exchange")) {
                    SafeLog.w(TAG, "LocalDNSTransport.Exchange called but not implemented. args=" + argsToString(args));
                    return defaultValue(method.getReturnType());
                }
                SafeLog.w(TAG, "Unhandled LocalDNS callback: " + methodLabel(method));
                return defaultValue(method.getReturnType());
            } catch (Throwable t) {
                SafeLog.e(TAG, "LocalDNS callback failed: " + methodLabel(method), t);
                throw t;
            }
        }
    }

    private void handleLookup(Object[] args) {
        if (args == null || args.length < 3) {
            SafeLog.w(TAG, "Lookup called with unexpected args=" + argsToString(args));
            return;
        }
        Object exchangeContext = args[0];
        String network = String.valueOf(args[1]);
        String domain = stripFinalDot(String.valueOf(args[2]));
        SafeLog.i(TAG, "LocalDNS.Lookup network=" + network + " domain=" + domain);
        try {
            List<String> ips = new ArrayList<>();
            for (InetAddress address : InetAddress.getAllByName(domain)) {
                String ip = address.getHostAddress();
                if (ip == null) continue;
                boolean v6 = ip.contains(":");
                if ("ip4".equals(network) && v6) continue;
                if ("ip6".equals(network) && !v6) continue;
                ips.add(ip);
            }
            String joined = joinLines(ips);
            SafeLog.i(TAG, "LocalDNS.Lookup success domain=" + domain + " ips=" + joined);
            callIfExists(exchangeContext, "Success", "success", joined);
        } catch (Throwable t) {
            SafeLog.w(TAG, "LocalDNS.Lookup failed domain=" + domain, t);
            callIfExists(exchangeContext, "ErrnoCode", "errnoCode", -1);
        }
    }

    private Class<?> loadClass(String className) throws ClassNotFoundException {
        SafeLog.i(TAG, "Loading class " + className);
        Class<?> cls = Class.forName(className);
        SafeLog.i(TAG, "Loaded class " + className + " from " + cls.getClassLoader());
        return cls;
    }

    private static Method findMethod(Class<?> cls, String[] names, int argCount) throws NoSuchMethodException {
        for (String name : names) {
            for (Method method : cls.getMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == argCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new NoSuchMethodException(cls.getName() + " method " + Arrays.toString(names) + " with " + argCount + " args. Available=" + methodSummary(cls));
    }

    private static Method findMethodFlexible(Class<?> cls, String[] names, int... argCounts) throws NoSuchMethodException {
        for (int count : argCounts) {
            try {
                return findMethod(cls, names, count);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(cls.getName() + " method " + Arrays.toString(names) + " with counts=" + Arrays.toString(argCounts) + ". Available=" + methodSummary(cls));
    }

    private static Object invokeStatic(Method method, Object... args) throws Exception {
        try {
            Object result = method.invoke(null, args);
            SafeLog.i(TAG, "invoke OK: " + methodLabel(method) + " result=" + shortObj(result));
            return result;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            SafeLog.e(TAG, "invoke FAILED: " + methodLabel(method) + " cause=" + cause, cause);
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }

    private static Object invokeTarget(Method method, Object target, Object... args) throws Exception {
        try {
            Object result = method.invoke(target, args);
            SafeLog.i(TAG, "invoke OK: " + methodLabel(method) + " result=" + shortObj(result));
            return result;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            SafeLog.e(TAG, "invoke FAILED: " + methodLabel(method) + " cause=" + cause, cause);
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }

    private static void invokeNoArg(Object target, String... names) throws Exception {
        Method m = findMethod(target.getClass(), names, 0);
        invokeTarget(m, target);
    }

    private static boolean methodExists(Class<?> cls, String... names) {
        try {
            findMethod(cls, names, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void callIfExists(Object target, String name1, String name2, Object arg) {
        if (target == null) return;
        try {
            for (Method method : target.getClass().getMethods()) {
                if ((method.getName().equals(name1) || method.getName().equals(name2)) && method.getParameterTypes().length == 1) {
                    method.setAccessible(true);
                    SafeLog.d(TAG, "Calling exchangeContext." + method.getName() + " arg=" + shortObj(arg));
                    method.invoke(target, arg);
                    return;
                }
            }
            SafeLog.w(TAG, "No exchangeContext method found: " + name1 + "/" + name2 + " on " + target.getClass().getName());
        } catch (Throwable t) {
            SafeLog.w(TAG, "exchangeContext call failed: " + name1 + "/" + name2, t);
        }
    }

    private static Object adaptReturn(Class<?> type, Object value) {
        if (type == void.class) return null;
        if (value == null) return defaultValue(type);
        if (!type.isPrimitive()) {
            if (type == Integer.class && value instanceof Number) return ((Number) value).intValue();
            if (type == Long.class && value instanceof Number) return ((Number) value).longValue();
            if (type == Boolean.class && value instanceof Boolean) return value;
            if (type == String.class) return String.valueOf(value);
            return value;
        }
        if (type == boolean.class) return value instanceof Boolean ? value : Boolean.parseBoolean(String.valueOf(value));
        if (value instanceof Number) {
            Number n = (Number) value;
            if (type == byte.class) return n.byteValue();
            if (type == short.class) return n.shortValue();
            if (type == int.class) return n.intValue();
            if (type == long.class) return n.longValue();
            if (type == float.class) return n.floatValue();
            if (type == double.class) return n.doubleValue();
        }
        if (type == char.class) return (char) 0;
        return defaultValue(type);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return (char) 0;
        return null;
    }

    private void logProxyCall(String group, AtomicInteger counter, Method method, Object[] args) {
        int n = counter.incrementAndGet();
        String name = method.getName();
        boolean important = name.toLowerCase().contains("tun")
                || name.toLowerCase().contains("control")
                || name.toLowerCase().contains("lookup")
                || name.toLowerCase().contains("exchange")
                || name.toLowerCase().contains("proc")
                || name.toLowerCase().contains("wifi")
                || name.toLowerCase().contains("uid")
                || n <= 20;
        if (important) {
            SafeLog.d(TAG, group + " callback #" + n + " " + methodLabel(method) + " args=" + argsToString(args));
        }
    }

    private static void logClassMethods(Class<?> cls, String label) {
        StringBuilder sb = new StringBuilder();
        Method[] methods = cls.getMethods();
        for (Method m : methods) {
            if (m.getDeclaringClass() == Object.class) continue;
            sb.append('\n').append("  ").append(methodLabel(m));
        }
        SafeLog.i(TAG, label + " methods:" + sb);
    }

    private static String methodSummary(Class<?> cls) {
        StringBuilder sb = new StringBuilder();
        for (Method m : cls.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            sb.append(methodLabel(m)).append("; ");
        }
        return sb.toString();
    }

    private static String methodLabel(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getReturnType().getSimpleName()).append(' ')
                .append(method.getDeclaringClass().getSimpleName()).append('.')
                .append(method.getName()).append('(');
        Class<?>[] p = method.getParameterTypes();
        for (int i = 0; i < p.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(p[i].getSimpleName());
        }
        sb.append(')');
        return sb.toString();
    }

    private static String ensureTrailingSlash(File file) {
        String path = file.getAbsolutePath();
        return path.endsWith(File.separator) ? path : path + File.separator;
    }

    private static void ensureDir(File dir, String label) {
        if (dir.exists()) {
            SafeLog.i(TAG, label + " exists path=" + dir.getAbsolutePath() + " canRead=" + dir.canRead() + " canWrite=" + dir.canWrite());
            return;
        }
        boolean ok = dir.mkdirs();
        SafeLog.i(TAG, label + " mkdirs path=" + dir.getAbsolutePath() + " ok=" + ok);
    }

    private static String stripFinalDot(String domain) {
        return domain != null && domain.endsWith(".") ? domain.substring(0, domain.length() - 1) : domain;
    }

    private String wifiState() {
        try {
            WifiManager wifiManager = (WifiManager) appContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wifiManager == null ? null : wifiManager.getConnectionInfo();
            if (info == null) return ",";
            return String.valueOf(info.getSSID()) + "," + String.valueOf(info.getBSSID());
        } catch (Throwable ignored) {
            return ",";
        }
    }

    private static String currentProcessName() {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                String name = Application.getProcessName();
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Throwable ignored) {
        }
        try (FileInputStream in = new FileInputStream("/proc/self/cmdline")) {
            byte[] buf = new byte[256];
            int len = in.read(buf);
            if (len > 0) {
                int end = 0;
                while (end < len && buf[end] != 0) end++;
                return new String(buf, 0, end, StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String argsToString(Object[] args) {
        if (args == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(shortObj(args[i]));
        }
        return sb.append(']').toString();
    }

    private static String shortObj(Object obj) {
        if (obj == null) return "null";
        String s;
        if (obj instanceof byte[]) {
            s = "byte[" + ((byte[]) obj).length + "]";
        } else {
            s = String.valueOf(obj);
        }
        if (s.length() > 500) s = s.substring(0, 500) + "...";
        return SafeLog.mask(s);
    }

    private static String joinLines(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(values.get(i));
        }
        return sb.toString();
    }
}
