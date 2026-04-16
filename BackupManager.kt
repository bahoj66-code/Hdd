package com.haydaybackup

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    private const val TAG = "HayDayBackup"
    private const val SOURCE_PATH = "/data/data/com.supercell.hayday/shared_prefs"
    private const val DEST_ROOT = "/sdcard/HayDayBackups"

    sealed class BackupResult {
        data class Success(val destPath: String, val timestamp: String) : BackupResult()
        data class Failure(val message: String) : BackupResult()
    }

    /**
     * Runs [cmd] as a privileged shell via Shizuku.
     * Returns the combined stdout+stderr output and the exit code.
     */
    private fun runShizukuCommand(vararg cmd: String): Pair<String, Int> {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd.joinToString(" ")), null, null)
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
        val exit = process.waitFor()
        process.destroy()
        val combined = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n")
        return Pair(combined, exit)
    }

    /**
     * Performs the backup:
     * 1. Creates a timestamped subfolder under /sdcard/HayDayBackups/
     * 2. Copies /data/data/com.supercell.hayday/shared_prefs/ into it via Shizuku shell.
     */
    suspend fun backup(): BackupResult = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) {
            return@withContext BackupResult.Failure("Shizuku binder is not available. Make sure Shizuku is running.")
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val destPath = "$DEST_ROOT/$timestamp"

        Log.d(TAG, "Starting backup: $SOURCE_PATH -> $destPath")

        // Step 1: Create destination directory
        val (mkdirOut, mkdirExit) = runShizukuCommand("mkdir -p \"$destPath\"")
        if (mkdirExit != 0) {
            Log.e(TAG, "mkdir failed ($mkdirExit): $mkdirOut")
            return@withContext BackupResult.Failure("Could not create backup directory: $mkdirOut")
        }

        // Step 2: Copy shared_prefs directory recursively
        val (cpOut, cpExit) = runShizukuCommand("cp -r \"$SOURCE_PATH/.\" \"$destPath/\"")
        if (cpExit != 0) {
            Log.e(TAG, "cp failed ($cpExit): $cpOut")
            return@withContext BackupResult.Failure("Copy failed (exit $cpExit): $cpOut")
        }

        // Step 3: Fix permissions so media scanner and file managers can read the files
        runShizukuCommand("chmod -R 644 \"$destPath\"")
        runShizukuCommand("chmod 755 \"$destPath\"")

        Log.d(TAG, "Backup completed: $destPath")
        BackupResult.Success(destPath, timestamp)
    }
}
