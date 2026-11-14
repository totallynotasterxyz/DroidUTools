// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.root

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object RootShell {
    private const val TAG = "DroidUtools_RootShell"
    private const val DEFAULT_TIMEOUT_SECONDS = 15L

    val KNOWN_SU_PATHS = listOf(
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/vendor/bin/su",
        "/data/local/tmp/su"
    )

    fun findSuBinary(): String? {
        for (path in KNOWN_SU_PATHS) {
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                return path
            }
        }
        // Check PATH
        val pathEnv = System.getenv("PATH") ?: ""
        for (dir in pathEnv.split(":")) {
            val file = File(dir, "su")
            if (file.exists() && file.canExecute()) {
                return file.absolutePath
            }
        }
        return null
    }

    suspend fun execute(command: String, timeoutSec: Long = DEFAULT_TIMEOUT_SECONDS): RootCommandResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val stdoutList = mutableListOf<String>()
            val stderrList = mutableListOf<String>()

            var process: Process? = null
            try {
                process = ProcessBuilder("su", "-c", command).start()

                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                val finished = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process.waitFor(timeoutSec, TimeUnit.SECONDS)
                } else {
                    withTimeoutOrNull(timeoutSec * 1000L) {
                        process.waitFor()
                        true
                    } ?: false
                }
                if (!finished) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        process.destroyForcibly()
                    } else {
                        process.destroy()
                    }
                    return@withContext RootCommandResult(
                        exitCode = -1,
                        stdout = emptyList(),
                        stderr = listOf("Command timed out after $timeoutSec seconds"),
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }

                var line: String?
                while (stdoutReader.readLine().also { line = it } != null) {
                    line?.let { stdoutList.add(it) }
                }
                while (stderrReader.readLine().also { line = it } != null) {
                    line?.let { stderrList.add(it) }
                }

                val exitCode = process.exitValue()
                RootCommandResult(
                    exitCode = exitCode,
                    stdout = stdoutList,
                    stderr = stderrList,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute root command: $command", e)
                RootCommandResult(
                    exitCode = -1,
                    stdout = emptyList(),
                    stderr = listOf(e.message ?: "Unknown process execution error"),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            } finally {
                process?.destroy()
            }
        }

    /**
     * Grants read/write permissions to all USB host nodes under /dev/bus/usb
     * so that daemon and child processes can interact with Apple hardware.
     */
    suspend fun grantUsbNodePermissions(): Boolean = withContext(Dispatchers.IO) {
        val res = execute("chmod -R 0666 /dev/bus/usb 2>/dev/null || true")
        res.isSuccess
    }

    /**
     * Checks SELinux enforcing status.
     */
    suspend fun getSELinuxStatus(): String = withContext(Dispatchers.IO) {
        val res = execute("getenforce")
        if (res.isSuccess && res.stdout.isNotEmpty()) {
            res.stdout.first().trim()
        } else {
            "Unknown"
        }
    }
}
