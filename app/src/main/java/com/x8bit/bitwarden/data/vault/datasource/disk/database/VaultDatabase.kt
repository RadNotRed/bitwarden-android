package com.x8bit.bitwarden.data.vault.datasource.disk.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.x8bit.bitwarden.data.vault.datasource.disk.convertor.ZonedDateTimeTypeConverter
import com.x8bit.bitwarden.data.vault.datasource.disk.dao.CiphersDao
import com.x8bit.bitwarden.data.vault.datasource.disk.dao.FoldersDao
import com.x8bit.bitwarden.data.vault.datasource.disk.entity.CipherEntity
import com.x8bit.bitwarden.data.vault.datasource.disk.entity.FolderEntity

/**
 * Room database for storing any persisted data from the vault sync.
 */
@Database(
    entities = [
        CipherEntity::class,
        FolderEntity::class,
    ],
    version = 1,
)
@TypeConverters(ZonedDateTimeTypeConverter::class)
abstract class VaultDatabase : RoomDatabase() {

    /**
     * Provides the DAO for accessing cipher data.
     */
    abstract fun cipherDao(): CiphersDao

    /**
     * Provides the DAO for accessing folder data.
     */
    abstract fun folderDao(): FoldersDao
}