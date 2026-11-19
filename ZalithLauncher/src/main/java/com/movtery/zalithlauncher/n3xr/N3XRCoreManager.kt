
package com.movtery.zalithlauncher.n3xr

import com.n3xr.core.N3XRNative

object N3XRCoreManager {

    fun isAvailable(): Boolean {
        return N3XRNative.isAvailable()
    }

    fun getVersion(): String {
        return safeCall {
            N3XRNative.nativeVersion()
        }
    }

    fun getArchitecture(): String {
        return safeCall {
            N3XRNative.nativeArchitecture()
        }
    }

    fun getOperatingSystem(): String {
        return safeCall {
            N3XRNative.nativeOperatingSystem()
        }
    }

    fun getCpuThreads(): Int {
        return safeCall {
            N3XRNative.nativeCpuThreads()
        } ?: -1
    }

    fun getCpuUsage(): Float {
        return safeCall {
            N3XRNative.nativeCpuUsagePercent()
        } ?: -1f
    }

    fun getDeviceMemory(): String {
        return safeCall {
            N3XRNative.nativeDeviceMemoryInfo()
        }
    }

    fun getProcessMemory(): String {
        return safeCall {
            N3XRNative.nativeProcessMemoryInfo()
        }
    }

    fun getSystemReport(): String {
        return safeCall {
            N3XRNative.nativeSystemReport()
        }
    }

    fun getNetworkInfo(): String {
        return safeCall {
            N3XRNative.nativeNetworkInfo()
        }
    }

    fun inspectDirectory(path: String): String {
        if (path.isBlank()) return "Invalid directory path"

        return safeCall {
            N3XRNative.nativeDirectoryInfo(path)
        }
    }

    fun inspectFile(path: String): String {
        if (path.isBlank()) return "Invalid file path"

        return safeCall {
            N3XRNative.nativeFileInfo(path)
        }
    }

    fun add(a: Int, b: Int): Int {
        return safeCall {
            N3XRNative.nativeAdd(a, b)
        } ?: 0
    }

    private fun <T> safeCall(
        action: () -> T
    ): T? {
        if (!N3XRNative.isAvailable()) {
            return null
        }

        return try {
            action()
        } catch (_: Throwable) {
            null
        }
    }
}
