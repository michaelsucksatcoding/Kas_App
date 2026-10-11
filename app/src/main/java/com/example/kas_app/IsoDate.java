package com.example.kas_app;

import java.util.Calendar;
import java.util.Locale;

/**
 * Formats and reads ledger dates in the one format the database stores:
 * {@code yyyy-MM-dd}.
 *
 * <p>Dates are built from the individual year / month / day numbers the date
 * picker reports and are read back by setting those same fields on a
 * {@link Calendar}. No {@link java.util.Date} and no timezone is involved, so a
 * selected day can never shift by one when it is stored or read again.
 */
final class IsoDate {

    private IsoDate() {
    }

    /** Today's date, in {@code yyyy-MM-dd}, from the device calendar. */
    static String today() {
        Calendar now = Calendar.getInstance();
        return format(now.get(Calendar.YEAR), now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * @param monthZeroBased the month as reported by {@link Calendar}/{@code
     *                       DatePickerDialog} (0 = January)
     * @return the date as {@code yyyy-MM-dd}
     */
    static String format(int year, int monthZeroBased, int dayOfMonth) {
        return String.format(Locale.US, "%04d-%02d-%02d", year, monthZeroBased + 1, dayOfMonth);
    }

    /**
     * Parses a stored {@code yyyy-MM-dd} date.
     *
     * @return a calendar set to that day, or to today when {@code isoDate} is
     *         null, empty or not a valid date, so the picker always has a
     *         sensible starting point.
     */
    static Calendar toCalendar(String isoDate) {
        Calendar calendar = Calendar.getInstance();
        if (isoDate == null) {
            return calendar;
        }
        String[] parts = isoDate.trim().split("-");
        if (parts.length != 3) {
            return calendar;
        }
        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                return calendar;
            }
            calendar.set(year, month - 1, day);
        } catch (NumberFormatException ignored) {
            // Keep today's date when the stored value is not a valid date.
        }
        return calendar;
    }
}
