// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entities.BackupSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupSnapshotDao {
    @Query("SELECT * FROM backup_snapshots ORDER BY createdAt DESC")
    fun getAllBackups(): Flow<List<BackupSnapshotEntity>>

    @Query("SELECT * FROM backup_snapshots WHERE udid = :udid ORDER BY createdAt DESC")
    fun getBackupsForDevice(udid: String): Flow<List<BackupSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: BackupSnapshotEntity)

    @Query("DELETE FROM backup_snapshots WHERE id = :id")
    suspend fun deleteBackup(id: String)
}
