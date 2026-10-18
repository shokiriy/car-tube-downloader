package com.shokirjon.cartubedownloader.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DownloadTask::class, TrackedPlaylist::class, PlaylistEntry::class], version = 1, exportSchema = false)
abstract class CarTubeDatabase : RoomDatabase() {
    abstract fun dao(): CarTubeDao
    companion object {
        @Volatile private var instance: CarTubeDatabase? = null
        fun get(context: Context): CarTubeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CarTubeDatabase::class.java, "cartube.db").build().also { instance = it }
        }
    }
}
