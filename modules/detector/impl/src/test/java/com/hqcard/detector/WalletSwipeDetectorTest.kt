package com.hqcard.detector

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 对应 modules/detector/docs/verify.md — 纯判定逻辑 */
class WalletSwipeDetectorTest {

    @Test
    fun `window state changed from tsmclient is a wallet card ui event`() {
        assertTrue(
            isWalletCardUiEvent(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                WALLET_PACKAGE,
            ),
        )
    }

    @Test
    fun `other packages are rejected`() {
        assertFalse(
            isWalletCardUiEvent(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                "com.hqcard",
            ),
        )
    }

    @Test
    fun `other event types from tsmclient are rejected`() {
        assertFalse(
            isWalletCardUiEvent(
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                WALLET_PACKAGE,
            ),
        )
    }

    @Test
    fun `null package is rejected`() {
        assertFalse(isWalletCardUiEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, null))
    }

    @Test
    fun `first detection is recorded`() {
        assertTrue(shouldRecordDetection(lastRecordedAt = null, now = 10_000L))
    }

    @Test
    fun `detection within debounce window is skipped`() {
        assertFalse(shouldRecordDetection(lastRecordedAt = 10_000L, now = 10_000L + 4_999L))
    }

    @Test
    fun `detection after debounce window is recorded`() {
        assertTrue(shouldRecordDetection(lastRecordedAt = 10_000L, now = 10_000L + 5_000L))
    }
}
