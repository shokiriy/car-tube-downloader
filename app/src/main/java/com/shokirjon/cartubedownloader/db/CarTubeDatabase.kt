package com.shokirjon.cartubedownloader.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DownloadTask::class, TrackedPlaylist::class, PlaylistEntry::class], version = 2, exportSchema = false)
abstract class CarTubeDatabase : RoomDatabase() {
    abstract fun dao(): CarTubeDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlist_items ADD COLUMN duration INTEGER")
                db.execSQL("ALTER TABLE tracked_playlists ADD COLUMN totalCount INTEGER")
                db.execSQL("ALTER TABLE tracked_playlists ADD COLUMN remoteOffset INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: CarTubeDatabase? = null
        fun get(context: Context): CarTubeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CarTubeDatabase::class.java, "cartube.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
