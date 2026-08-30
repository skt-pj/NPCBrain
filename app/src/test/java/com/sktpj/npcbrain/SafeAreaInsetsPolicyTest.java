package com.sktpj.npcbrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SafeAreaInsetsPolicyTest {
    @Test
    public void addsSafeInsetsToOriginalBasePadding() {
        SafeAreaInsetsPolicy.Padding padding = SafeAreaInsetsPolicy.resolve(
                4, 5, 6, 7,
                10, 20, 30, 40);
        assertEquals(14, padding.left);
        assertEquals(25, padding.top);
        assertEquals(36, padding.right);
        assertEquals(47, padding.bottom);
    }

    @Test
    public void zeroInsetsPreserveBasePadding() {
        SafeAreaInsetsPolicy.Padding padding = SafeAreaInsetsPolicy.resolve(
                4, 5, 6, 7,
                0, 0, 0, 0);
        assertEquals(4, padding.left);
        assertEquals(5, padding.top);
        assertEquals(6, padding.right);
        assertEquals(7, padding.bottom);
    }

    @Test
    public void repeatedResolutionFromSameBaseDoesNotAccumulate() {
        SafeAreaInsetsPolicy.Padding first = SafeAreaInsetsPolicy.resolve(
                4, 5, 6, 7,
                10, 20, 30, 40);
        SafeAreaInsetsPolicy.Padding second = SafeAreaInsetsPolicy.resolve(
                4, 5, 6, 7,
                10, 20, 30, 40);
        assertEquals(first.left, second.left);
        assertEquals(first.top, second.top);
        assertEquals(first.right, second.right);
        assertEquals(first.bottom, second.bottom);
    }

    @Test
    public void negativeInputsCannotCreateNegativePadding() {
        SafeAreaInsetsPolicy.Padding padding = SafeAreaInsetsPolicy.resolve(
                -1, -2, -3, -4,
                -5, -6, -7, -8);
        assertEquals(0, padding.left);
        assertEquals(0, padding.top);
        assertEquals(0, padding.right);
        assertEquals(0, padding.bottom);
    }
}
