package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchHistoryEntity::class,
        WatchlistEntity::class,
        DramaStatsEntity::class,
        CachedFeedEntity::class,
        CachedWatchDetailEntity::class,
        LocalInboxEntity::class,          // 👈 নতুন: অফলাইন ইনবক্স টেবিল
        LocalPrivateMessageEntity::class  // 👈 নতুন: অফলাইন মেসেজ হিস্ট্রি টেবিল
    ],
    version = 6, // 👈 নতুন চ্যাট টেবিল যুক্ত করার কারণে ভার্সন ৬ করা হলো
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun dramaStatsDao(): DramaStatsDao
    abstract fun contentCacheDao(): ContentCacheDao
    abstract fun privateChatDao(): PrivateChatDao // 👈 নতুন: প্রাইভেট চ্যাট ডাও অ্যাক্সেসর

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "playdramaflix_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
