package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure JVM tests for the shared Rupiah formatter. No Android dependency, so
 * these run as part of {@code testDebugUnitTest}.
 */
public class CurrencyFormatterTest {

    @Test
    public void formatsWholeRupiahWithThousandsSeparators() {
        assertEquals("Rp 0", CurrencyFormatter.format(0L));
        assertEquals("Rp 10.000", CurrencyFormatter.format(10000L));
        assertEquals("Rp 121.000", CurrencyFormatter.format(121000L));
    }

    @Test
    public void keepsTheMinusSignOnANegativeBalance() {
        // A negative balance must be visible, not rendered as a positive amount.
        assertEquals("-Rp 1.500", CurrencyFormatter.format(-1500L));
    }

    @Test
    public void acceptsLongValuesSoTheBalanceIsNeverTruncated() {
        // getBalance() returns a long; formatting must not narrow it to int.
        String formatted = CurrencyFormatter.format(4_000_000_000L);
        assertTrue(formatted, formatted.startsWith("Rp 4.000.000.000"));
    }

    @Test
    public void outputAlwaysCarriesTheRupiahPrefix() {
        for (long amount : new long[]{0L, 1L, 999L, 1000000L, -250L}) {
            assertTrue(CurrencyFormatter.format(amount).contains("Rp"));
        }
    }
}
