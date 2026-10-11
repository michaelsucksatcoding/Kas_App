package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure JVM tests for the amount rules. No Android dependency, so these always
 * run as part of {@code testDebugUnitTest}.
 */
public class AmountValidatorTest {

    private void assertRejected(String input, AmountValidator.Error expected) {
        AmountValidator.Result result = AmountValidator.validate(input);
        assertFalse("expected \"" + input + "\" to be rejected", result.isValid());
        assertEquals(expected, result.getError());
    }

    private void assertAccepted(String input, int expectedAmount) {
        AmountValidator.Result result = AmountValidator.validate(input);
        assertTrue("expected \"" + input + "\" to be accepted", result.isValid());
        assertEquals(expectedAmount, result.getAmount());
    }

    @Test
    public void validAmountsAreParsed() {
        assertAccepted("1", 1);
        assertAccepted("50000", 50000);
        assertAccepted("121000", 121000);
        assertAccepted("  7500  ", 7500);
        assertAccepted("2147483647", Integer.MAX_VALUE);
    }

    @Test
    public void emptyInputIsRejected() {
        assertRejected("", AmountValidator.Error.EMPTY);
        assertRejected("   ", AmountValidator.Error.EMPTY);
        assertRejected(null, AmountValidator.Error.EMPTY);
    }

    @Test
    public void malformedNumbersAreRejectedWithoutThrowing() {
        assertRejected("abc", AmountValidator.Error.NOT_A_NUMBER);
        assertRejected("1.000.000", AmountValidator.Error.NOT_A_NUMBER);
        assertRejected("1,5", AmountValidator.Error.NOT_A_NUMBER);
        assertRejected("12a", AmountValidator.Error.NOT_A_NUMBER);
        assertRejected("-100", AmountValidator.Error.NOT_A_NUMBER);
        assertRejected("+100", AmountValidator.Error.NOT_A_NUMBER);
        assertRejected("1 000", AmountValidator.Error.NOT_A_NUMBER);
    }

    @Test
    public void zeroIsRejected() {
        assertRejected("0", AmountValidator.Error.NOT_POSITIVE);
        assertRejected(" 000 ", AmountValidator.Error.NOT_POSITIVE);
    }

    @Test
    public void oversizedValuesAreRejectedInsteadOfOverflowing() {
        assertRejected("2147483648", AmountValidator.Error.TOO_LARGE);
        assertRejected("99999999999999999999", AmountValidator.Error.TOO_LARGE);
        assertRejected("999999999999999999999999999999999999999999", AmountValidator.Error.TOO_LARGE);
    }

    @Test
    public void resultsNeverLeakAPartialAmount() {
        for (String bad : new String[]{"", "abc", "1.000.000", "0", "-5", "99999999999999999999"}) {
            AmountValidator.Result result = AmountValidator.validate(bad);
            assertEquals(0, result.getAmount());
        }
    }
}
