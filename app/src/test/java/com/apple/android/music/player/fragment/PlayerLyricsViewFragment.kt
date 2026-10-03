package com.apple.android.music.player.fragment

import com.apple.android.music.model.BaseContentItem
import com.apple.android.music.ttml.javanative.model.SongInfo

/** Native1606 descriptors plus deliberately similar, unrelated pointer methods. */
class PlayerLyricsViewFragment : e() {
    var installs = 0
    fun w2(pointer: SongInfo.SongInfoPtr?) { installs++ }
    fun F2(pointer: SongInfo.SongInfoPtr?) = Unit
    fun I2(pointer: SongInfo.SongInfoPtr?) = Unit
    fun b2(metadata: z3.w, item: BaseContentItem, flags: e.c) { c = item }
    fun b2(metadata: v3.v, item: BaseContentItem, flags: e.c) = Unit
}
