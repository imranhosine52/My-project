package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single visited-page record for the in-app browser's History list.
 */
@Entity(tableName = "browser_history")
data class BrowserHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
)

/**
 * A saved bookmark for the in-app browser. Keyed by URL so re-bookmarking
 * the same page simply refreshes its title/timestamp instead of duplicating it.
 */
@Entity(tableName = "browser_bookmarks")
data class BrowserBookmarkEntity(
    @PrimaryKey val url: String,
    val title: String,
    val addedAt: Long = System.currentTimeMillis()
)
