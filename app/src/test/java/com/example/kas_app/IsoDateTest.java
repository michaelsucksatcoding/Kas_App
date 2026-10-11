package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.regex.Pattern;

/**
 * Unit tests for {@link IsoDate}, the single place that turns a picked
 * year/month/day into the ledger's {@code yyyy-MM-dd} string and back.
 *
 * <p>The important guarantee is that the chosen day is stored exactly as shown:
 * the numbers are copied field by field, so no timezone or {@link java.util.Date}
 * conversion can move the day by one.
 */
public class IsoDateTest {

    @Test
    public void formatTurnsThePickerMonthIntoATwoDigitDate() {
        assertEquals("Calendar month 0 is January", "2026-01-05", IsoDate.format(2026, 0, 5));
        assertEquals("2026-06-15", IsoDate.format(2026, 5, 15));
        assertEquals("2026-12-31", IsoDate.format(2026, 11, 31));
    }

    @Test
    public void formatPadsSingleDigitMonthAndDay() {
        assertEquals("2026-02-03", IsoDate.format(2026, 1, 3));
        assertEquals("2026-09-09", IsoDate.format(2026, 8, 9));
    }

    @Test
    public void parseKeepsTheSameDayWithNoTimezoneShift() {
        Calendar calendar = IsoDate.toCalendar("2026-06-15");

        assertEquals(2026, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.JUNE, calendar.get(Calendar.MONTH));
        assertEquals(15, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals("round-tripping must not shift the day", "2026-06-15",
                IsoDate.format(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)));
    }

    @Test
    public void parseHandlesEveryMonthOfTheYear() {
        for (int month = 1; month <= 12; month++) {
            String iso = IsoDate.format(2026, month - 1, 1);
            Calendar calendar = IsoDate.toCalendar(iso);
            assertEquals("month " + month + " of " + iso, month - 1,
                    calendar.get(Calendar.MONTH));
            assertEquals("month " + month + " of " + iso, 1,
                    calendar.get(Calendar.DAY_OF_MONTH));
        }
    }

    @Test
    public void parseFallsBackToTodayForNullEmptyAndGarbage() {
        Calendar today = Calendar.getInstance();
        String[] badValues = {null, "", "   ", "not-a-date", "2026/06/15", "2026-13-01", "2026-00-10"};

        for (String bad : badValues) {
            Calendar calendar = IsoDate.toCalendar(bad);
            assertEquals("fallback year for: " + bad, today.get(Calendar.YEAR),
                    calendar.get(Calendar.YEAR));
            assertEquals("fallback month for: " + bad, today.get(Calendar.MONTH),
                    calendar.get(Calendar.MONTH));
            assertEquals("fallback day for: " + bad, today.get(Calendar.DAY_OF_MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
        }
    }

    @Test
    public void todayIsTheDevicesDayInIsoFormat() {
        String today = IsoDate.today();
        assertTrue("today must be yyyy-MM-dd, was: " + today,
                Pattern.matches("\\d{4}-\\d{2}-\\d{2}", today));

        Calendar now = Calendar.getInstance();
        assertEquals(today, IsoDate.format(now.get(Calendar.YEAR), now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)));
    }
}
