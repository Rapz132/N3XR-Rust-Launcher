
package com.n3xr.core;

public final class N3XRNative {

    private static final boolean AVAILABLE;
    private static final Throwable LOAD_ERROR;

    static {
        Throwable error = null;

        try {
            System.loadLibrary("n3xr_core");
        } catch (Throwable t) {
            error = t;
        }

        LOAD_ERROR = error;
        AVAILABLE = error == null;
    }

    private N3XRNative() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static Throwable loadError() {
        return LOAD_ERROR;
    }

    public static native String nativeVersion();

    public static native int nativeAdd(int a, int b);

    public static native String nativeArchitecture();

    public static native String nativeOperatingSystem();

    public static native int nativeCpuThreads();

    public static native String nativeDeviceMemoryInfo();

    public static native String nativeProcessMemoryInfo();

    public static native float nativeCpuUsagePercent();

    public static native String nativeSystemReport();

    public static native String nativeDirectoryInfo(String path);

    public static native String nativeFileInfo(String path);

    public static native String nativeNetworkInfo();
}
