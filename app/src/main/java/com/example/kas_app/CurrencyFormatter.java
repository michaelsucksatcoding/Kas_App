package com.example.kas_app;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The single place where a stored Rupiah amount is turned into display text.
 *
 * <p>This consolidates the formatting that was previously duplicated in
 * {@link LedgerAdapter} and {@link PaymentAdapter}, so the balance, the ledger
 * and the payment history can never drift apart visually.
 *
 * <p>Amounts are integer Rupiah. This class does not introduce a second money
 * representation; it only formats the {@code INTEGER} already stored in the
 * database, and accepts {@code long} so the authoritative balance returned by
 * {@link DatabaseHelper#getBalance()} is never truncated.
 *
 * <p>The pattern is declared explicitly rather than taken from
 * {@code NumberFormat.getCurrencyInstance} because that factory's output depends
 * on the JDK/Device CLDR currency data: on some devices it renders Indonesian
 * Rupiah with two decimal places ({@code Rp121.000,00}). Rupiah has no minor
 * unit, so showing cents is misleading, and the separator style must not vary
 * per device either. The pattern below reproduces the convention already used
 * in the layouts ({@code Rp 0}) and is identical on every device.
 *
 * <p>Free of any Android dependency so the rules are testable on the JVM.
 */
public final class CurrencyFormatter {

    private static final String PATTERN = "Rp #,##0";

    private CurrencyFormatter() {
    }

    /**
     * Formats an amount as Indonesian Rupiah, e.g. {@code Rp 121.000}.
     *
     * <p>Indonesian grouping uses {@code .} as the thousands separator, which
     * is why the pattern uses {@code ,} as the grouping character and rebinds it
     * below. No decimal places are ever shown.
     *
     * <p>A negative value keeps its minus sign, so a negative balance is
     * rendered as {@code -Rp 1.500} rather than being hidden.
     */
    public static String format(long amount) {
        // Locale.US is only a neutral base for the symbol table; the grouping and
        // decimal separators are both overridden below, so no locale data leaks
        // into the result.
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat(PATTERN, symbols).format(amount);
    }
}
