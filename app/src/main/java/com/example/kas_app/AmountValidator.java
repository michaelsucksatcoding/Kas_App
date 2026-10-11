package com.example.kas_app;

/**
 * Single place where a user supplied Rupiah amount is turned into the
 * {@code INTEGER} value stored in {@code table_payments.amount} and
 * {@code table_ledger.amount}.
 *
 * <p>Amounts are plain integer Rupiah, which is the representation the rest of
 * the app already relies on. This class does not change that representation; it
 * only guarantees that a malformed value can never reach
 * {@link Integer#parseInt(String)} and crash the app.
 *
 * <p>Deliberately free of any Android dependency so the rules can be unit
 * tested on the JVM. The caller maps {@link Error} to a localized string.
 */
public final class AmountValidator {

    private AmountValidator() {
    }

    public enum Error {
        /** Input is null, empty, or only whitespace. */
        EMPTY,
        /** Input is not a plain sequence of digits (e.g. "abc", "1.000.000", "-100", "1,5"). */
        NOT_A_NUMBER,
        /** Input is a well formed number but not strictly positive ("0"). */
        NOT_POSITIVE,
        /** Input is a well formed number that does not fit in {@code INTEGER}. */
        TOO_LARGE
    }

    public static final class Result {

        private static final Result VALID = new Result(0, null);

        private final int amount;
        private final Error error;

        private Result(int amount, Error error) {
            this.amount = amount;
            this.error = error;
        }

        public boolean isValid() {
            return error == null;
        }

        /** @return the parsed Rupiah amount, or {@code 0} when {@link #isValid()} is false. */
        public int getAmount() {
            return amount;
        }

        /** @return the reason the input was rejected, or {@code null} when {@link #isValid()}. */
        public Error getError() {
            return error;
        }
    }

    /**
     * Validates and parses a raw amount field value.
     *
     * <p>Accepted input is one or more ASCII digits with optional surrounding
     * whitespace. Grouping separators such as {@code 1.000.000} are rejected
     * rather than normalized, because accepting them would mean two textual
     * representations of the same stored value and the localization phase will
     * define the formatting rules.
     *
     * @param rawInput the value as typed by the user, may be {@code null}
     * @return a {@link Result} that is either valid or carries an {@link Error}
     */
    public static Result validate(String rawInput) {
        if (rawInput == null) {
            return new Result(0, Error.EMPTY);
        }
        String trimmed = rawInput.trim();
        if (trimmed.isEmpty()) {
            return new Result(0, Error.EMPTY);
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < '0' || c > '9') {
                return new Result(0, Error.NOT_A_NUMBER);
            }
        }
        // Parsed as a long first: an over-long digit string throws instead of
        // silently wrapping around, so no amount can ever be stored wrong.
        long parsed;
        try {
            parsed = Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return new Result(0, Error.TOO_LARGE);
        }
        if (parsed <= 0L) {
            return new Result(0, Error.NOT_POSITIVE);
        }
        if (parsed > Integer.MAX_VALUE) {
            return new Result(0, Error.TOO_LARGE);
        }
        return new Result((int) parsed, null);
    }

    /**
     * Validates an amount that has already been parsed into an {@code int}.
     *
     * <p>The data access layer receives an {@code int}, not raw text, so it uses
     * this overload to apply the same "must be strictly positive" rule as
     * {@link #validate(String)} without introducing a second amount parser. A
     * zero or negative amount stored in the ledger would corrupt the balance, so
     * it is refused here as well as at the input.
     *
     * @param amount the already parsed amount
     * @return a valid {@link Result} when {@code amount > 0}
     */
    public static Result validate(int amount) {
        if (amount <= 0) {
            return new Result(0, Error.NOT_POSITIVE);
        }
        return new Result(amount, null);
    }
}
