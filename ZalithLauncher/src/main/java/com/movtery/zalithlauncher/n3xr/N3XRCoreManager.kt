
package com.movtery.zalithlauncher.n3xr

import com.n3xr.core.N3XRNative

object N3XRCoreManager {

    fun isAvailable(): Boolean =
        N3XRNative.isAvailable()

    fun getVersion(): String =
        safeCall { N3XRNative.nativeVersion() } ?: "Unavailable"

    fun getArchitecture(): String =
        safeCall { N3XRNative.nativeArchitecture() } ?: "Unavailable"

    fun getOperatingSystem(): String =
        safeCall { N3XRNative.nativeOperatingSystem() } ?: "Unavailable"

    fun getCpuThreads(): Int =
        safeCall { N3XRNative.nativeCpuThreads() } ?: -1

    fun getCpuUsage(): Float =
        safeCall { N3XRNative.nativeCpuUsagePercent() } ?: -1f

    fun getDeviceMemory(): String =
        safeCall { N3XRNative.nativeDeviceMemoryInfo() } ?: "Unavailable"

    fun getProcessMemory(): String =
        safeCall { N3XRNative.nativeProcessMemoryInfo() } ?: "Unavailable"

    fun getSystemReport(): String =
        safeCall { N3XRNative.nativeSystemReport() } ?: "Unavailable"

    fun getNetworkInfo(): String =
        safeCall { N3XRNative.nativeNetworkInfo() } ?: "Unavailable"

    fun inspectDirectory(path: String): String {
        if (path.isBlank()) return "Invalid directory path"
        return safeCall {
            N3XRNative.nativeDirectoryInfo(path)
        } ?: "Unavailable"
    }

    fun inspectFile(path: String): String {
        if (path.isBlank()) return "Invalid file path"
        return safeCall {
            N3XRNative.nativeFileInfo(path)
        } ?: "Unavailable"
    }

    fun getHealthCheck(): String =
        safeCall { N3XRNative.nativeHealthCheck() } ?: "Unavailable"

    fun getDiagnostics(): String =
        safeCall { N3XRNative.nativeDiagnostics() } ?: "Unavailable"

    fun add(a: Int, b: Int): Int =
        safeCall { N3XRNative.nativeAdd(a, b) } ?: 0

    private fun <T> safeCall(action: () -> T): T? {
        if (!N3XRNative.isAvailable()) return null

        return try {
            action()
        } catch (_: Throwable) {
            null
        }
    }
}
