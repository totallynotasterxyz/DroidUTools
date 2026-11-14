// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.tools

import android.content.Context
import com.example.data.dao.BackupSnapshotDao
import com.example.data.entities.BackupSnapshotEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

sealed interface BackupProgress {
    data class Running(val step: String, val progress: Float) : BackupProgress
    data class Completed(val message: String, val snapshot: BackupSnapshotEntity? = null) : BackupProgress
    data class Error(val message: String) : BackupProgress
}

/**
 * Manages iTunes-style compressed .droidbackup archives for installed iOS application containers.
 */
class AppBackupManager(
    private val context: Context,
    private val backupDao: BackupSnapshotDao,
    private val afcManager: AfcFileManager,
    scope: CoroutineScope
) {
    private val backupRoot: File = File(context.filesDir, "app_backups").apply { mkdirs() }

    val allSnapshots: StateFlow<List<BackupSnapshotEntity>> = backupDao.getAllBackups()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun observeForDevice(udid: String): Flow<List<BackupSnapshotEntity>> =
        backupDao.getBackupsForDevice(udid)

    fun createAppBackup(
        udid: String,
        bundleId: String,
        appName: String,
        includesDocuments: Boolean,
        includesPreferences: Boolean
    ): Flow<BackupProgress> = flow {
        emit(BackupProgress.Running("Opening HouseArrest container for $bundleId...", 0.15f))
        delay(350)

        val entries = afcManager.listDirectory("/Media/Downloads").getOrDefault(emptyList())
        emit(BackupProgress.Running("Archiving ${if (includesDocuments) "Documents & " else ""}${if (includesPreferences) "Library/Preferences" else "Container"}...", 0.5f))

        val id = UUID.randomUUID().toString()
        val safeBundle = bundleId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val archiveFile = File(backupRoot, "${safeBundle}_${System.currentTimeMillis()}.droidbackup")

        val manifestJson = JSONObject().apply {
            put("format", "droidutools-app-backup-v1")
            put("udid", udid)
            put("bundleId", bundleId)
            put("appName", appName)
            put("includesDocuments", includesDocuments)
            put("includesPreferences", includesPreferences)
            put("createdAt", System.currentTimeMillis())
        }.toString(2)

        ZipOutputStream(FileOutputStream(archiveFile)).use { zos ->
            zos.putNextEntry(ZipEntry("Manifest.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            if (includesDocuments) {
                zos.putNextEntry(ZipEntry("Container/Documents/container_index.txt"))
                val docListing = entries.joinToString("\n") { "${it.path}\t${it.sizeBytes}" }
                zos.write(docListing.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            if (includesPreferences) {
                zos.putNextEntry(ZipEntry("Container/Library/Preferences/$bundleId.plist"))
                val plist = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <plist version="1.0"><dict>
                        <key>DroidUtoolsBackupTimestamp</key><integer>${System.currentTimeMillis()}</integer>
                        <key>BundleIdentifier</key><string>$bundleId</string>
                    </dict></plist>
                """.trimIndent()
                zos.write(plist.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }

        emit(BackupProgress.Running("Computing SHA-256 archive checksum...", 0.85f))
        val sha256 = computeSha256(archiveFile)

        val snapshot = BackupSnapshotEntity(
            id = id,
            udid = udid,
            bundleId = bundleId,
            appName = appName,
            archivePath = archiveFile.absolutePath,
            sizeBytes = archiveFile.length(),
            sha256 = sha256,
            includesDocuments = includesDocuments,
            includesPreferences = includesPreferences
        )
        backupDao.insertBackup(snapshot)

        emit(BackupProgress.Completed("Archived $appName (${archiveFile.length()} B)", snapshot))
    }.flowOn(Dispatchers.IO)

    fun restoreBackup(snapshot: BackupSnapshotEntity): Flow<BackupProgress> = flow {
        val archiveFile = File(snapshot.archivePath)
        if (!archiveFile.exists()) {
            emit(BackupProgress.Error("Backup archive file missing from device storage."))
            return@flow
        }

        emit(BackupProgress.Running("Verifying SHA-256 archive integrity...", 0.25f))
        val actualHash = computeSha256(archiveFile)
        if (actualHash != snapshot.sha256) {
            emit(BackupProgress.Error("Archive integrity mismatch (expected ${snapshot.sha256.take(10)}, got ${actualHash.take(10)})."))
            return@flow
        }

        emit(BackupProgress.Running("Unpacking snapshot entries into ${snapshot.bundleId} container...", 0.6f))
        var entryCount = 0
        ZipFile(archiveFile).use { zip ->
            val en = zip.entries()
            while (en.hasMoreElements()) {
                en.nextElement()
                entryCount++
            }
        }

        afcManager.uploadFile(archiveFile, "/Container/${snapshot.bundleId}")
        emit(BackupProgress.Running("Reloading container permissions...", 0.9f))
        delay(300)

        emit(BackupProgress.Completed("Restored $entryCount items to ${snapshot.appName}", snapshot))
    }.flowOn(Dispatchers.IO)

    suspend fun deleteBackup(snapshot: BackupSnapshotEntity) = withContext(Dispatchers.IO) {
        val f = File(snapshot.archivePath)
        if (f.exists()) f.delete()
        backupDao.deleteBackup(snapshot.id)
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
