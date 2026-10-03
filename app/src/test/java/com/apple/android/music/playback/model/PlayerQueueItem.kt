package com.apple.android.music.playback.model

interface PlayerQueueItem {
    fun getItem(): PlayerMediaItem
    fun getPlaybackQueueId(): Long
}

interface PlayerMediaItem {
    fun getGenreName(): String
    fun getDuration(): Long
    fun getTitle(): String
    fun getSubscriptionStoreId(): String
    fun getPersistentId(): Long
    fun getArtistName(): String
}
