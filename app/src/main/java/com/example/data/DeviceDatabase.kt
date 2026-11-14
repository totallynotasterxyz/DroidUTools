// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.ActivityLogDao
import com.example.data.dao.BackupSnapshotDao
import com.example.data.dao.DeviceDao
import com.example.data.dao.PairingRecordDao
import com.example.data.entities.ActivityLogEntity
import com.example.data.entities.BackupSnapshotEntity
import com.example.data.entities.DeviceEntity
import com.example.data.entities.PairingRecordEntity

@Database(
    entities = [
        DeviceEntity::class,
        PairingRecordEntity::class,
        ActivityLogEntity::class,
        BackupSnapshotEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DeviceDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun pairingRecordDao(): PairingRecordDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun backupSnapshotDao(): BackupSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: DeviceDatabase? = null

        fun getInstance(context: Context): DeviceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DeviceDatabase::class.java,
                    "droidutools.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
