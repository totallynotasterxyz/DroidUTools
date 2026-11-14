// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entities.PairingRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PairingRecordDao {
    @Query("SELECT * FROM pairing_records WHERE udid = :udid LIMIT 1")
    suspend fun getPairingRecord(udid: String): PairingRecordEntity?

    @Query("SELECT * FROM pairing_records WHERE udid = :udid LIMIT 1")
    fun observePairingRecord(udid: String): Flow<PairingRecordEntity?>

    @Query("SELECT * FROM pairing_records")
    fun getAllPairingRecords(): Flow<List<PairingRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePairingRecord(record: PairingRecordEntity)

    @Query("DELETE FROM pairing_records WHERE udid = :udid")
    suspend fun deletePairingRecord(udid: String)

    @Query("DELETE FROM pairing_records")
    suspend fun deleteAllPairingRecords()
}
