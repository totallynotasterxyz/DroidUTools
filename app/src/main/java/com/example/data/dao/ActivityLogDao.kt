// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.entities.ActivityLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE udid = :udid ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsForDevice(udid: String, limit: Int = 100): Flow<List<ActivityLogEntity>>

    @Insert
    suspend fun insertLog(log: ActivityLogEntity)

    @Query("DELETE FROM activity_logs")
    suspend fun clearLogs()
}
