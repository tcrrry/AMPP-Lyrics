package dev.amenhancer.module.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class FragmentDualPaneArtworkLeaseTest {
    @Test fun `centering uses the native cover size and repeated placement does not drift`() {
        var y = 12f
        var writes = 0
        val lease = FragmentDualPaneArtworkLease({ y }) { y = it; writes++ }
        lease.place(24, 700, 112, 400)
        assertEquals(62f, y, 0f)
        repeat(10) { lease.place(24, 700, 162, 400) }
        assertEquals(1, writes)
        lease.close()
        assertEquals(12f, y, 0f)
    }

    @Test fun `later native animation write survives release`() {
        var y = 12f
        val lease = FragmentDualPaneArtworkLease({ y }) { y = it }
        lease.place(24, 700, 112, 400)
        y = -18f
        lease.close()
        assertEquals(-18f, y, 0f)
    }

    @Test fun `new placement captures the latest native translation for later restore`() {
        var y = 12f
        val lease = FragmentDualPaneArtworkLease({ y }) { y = it }
        lease.place(24, 700, 112, 400)
        y = 65f
        lease.place(24, 700, 165, 400)
        assertEquals(62f, y, 0f)
        lease.close()
        assertEquals(65f, y, 0f)
    }

    @Test fun `undersized interval keeps native size and unavailable metrics leave translation alone`() {
        var y = 0f
        val lease = FragmentDualPaneArtworkLease({ y }) { y = it }
        lease.place(24, 300, 100, 400)
        assertEquals(-76f, y, 0f)
        lease.close()
        assertEquals(0f, y, 0f)
        lease.place(100, 100, 24, 400)
        lease.place(24, 700, 100, 0)
        assertEquals(0f, y, 0f)
    }
}
