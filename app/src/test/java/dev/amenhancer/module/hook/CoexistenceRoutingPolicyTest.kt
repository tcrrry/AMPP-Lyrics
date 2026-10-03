package dev.amenhancer.module.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class CoexistenceRoutingPolicyTest {
    private val target = "com.tcrrry.ampplyrics.coexist"

    @Test
    fun `redirects self provider without changing query or path`() {
        assertEquals("content://$target.provider/item/7?token=a#part",
            CoexistenceRoutingPolicy.contentUri(
                "content://com.apple.android.music.provider/item/7?token=a#part", target))
        assertEquals("$target.ams.stable",
            CoexistenceRoutingPolicy.authority("com.apple.android.music.ams.stable", target))
    }

    @Test
    fun `keeps authentication urls and other providers untouched`() {
        listOf("https://music.apple.com/path", "musicsdk://applemusic/authenticate-v.1",
            "content://media/external/audio", "content://com.apple.android.musicother.provider/x").forEach {
            assertEquals(it, CoexistenceRoutingPolicy.contentUri(it, target))
        }
        assertEquals("com.apple.android.musicother", CoexistenceRoutingPolicy.packageName("com.apple.android.musicother", target))
        assertEquals(null, CoexistenceRoutingPolicy.contentUri(null, target))
    }

    @Test
    fun `already isolated identities remain unchanged`() {
        assertEquals(target, CoexistenceRoutingPolicy.packageName(target, target))
        assertEquals("content://$target.provider/x",
            CoexistenceRoutingPolicy.contentUri("content://$target.provider/x", target))
    }
}
