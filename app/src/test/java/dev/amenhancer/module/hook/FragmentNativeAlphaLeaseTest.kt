package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class FragmentNativeAlphaLeaseTest {
    @Test fun `native foreground setAlpha one stays suppressed beneath glass`() {
        var alpha = 1f
        val lease = FragmentNativeAlphaLease({ alpha }, { alpha = it })
        lease.hide(true)
        repeat(4) { alpha = lease.hostWrite(1f) ?: 1f; lease.hide(true); assertEquals(0f, alpha, 0f) }
        lease.hide(false)
        assertEquals(1f, alpha, 0f)
    }
    @Test fun `cached native material write is reconciled before draw and restored accurately`() {
        var alpha = 1f
        val lease = FragmentNativeAlphaLease({ alpha }, { alpha = it })
        lease.hide(true)
        alpha = .75f // Native RenderNode/material restoration bypassed the Java setter hook.
        lease.hide(true)
        assertEquals(0f, alpha, 0f)
        lease.hide(false)
        assertEquals(.75f, alpha, 0f)
    }
    @Test fun `host hiding an already hidden native material is preserved on close`() {
        var alpha = 1f
        val lease = FragmentNativeAlphaLease({ alpha }, { alpha = it })
        lease.hide(true)
        alpha = lease.hostWrite(0f) ?: 0f
        lease.hide(false)
        assertEquals(0f, alpha, 0f)
    }
    @Test fun `material created hidden can become visible natively while glass owns it`() {
        var alpha = 0f
        val lease = FragmentNativeAlphaLease({ alpha }, { alpha = it })
        lease.hide(true)
        alpha = lease.hostWrite(1f) ?: 1f
        assertEquals(0f, alpha, 0f)
        lease.hide(false)
        assertEquals(1f, alpha, 0f)
    }
    @Test fun `ownership release preserves an unhooked later native value`() {
        var alpha = 1f
        val lease = FragmentNativeAlphaLease({ alpha }, { alpha = it })
        lease.hide(true)
        alpha = .4f
        lease.hide(false)
        assertEquals(.4f, alpha, 0f)
    }
}
