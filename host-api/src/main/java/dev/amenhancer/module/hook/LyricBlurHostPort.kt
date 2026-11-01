package dev.amenhancer.module.hook

import android.view.View
import android.view.ViewGroup

/** Android binding is confined to the host boundary; core receives immutable copied values. */
interface LyricBlurHostPort {
    fun recycler(): ViewGroup?
    fun document(): LyricDecodeResult<Map<Int, Int>>
    fun displayed(rows: List<LyricBlurBoundRow>): LyricDecodeResult<LyricDisplaySnapshot>
    fun row(view: View, bindingGeneration: Long): LyricBlurBoundRow
}

data class LyricBlurBoundRow(val view: View, val identity: LyricRowSnapshot)
