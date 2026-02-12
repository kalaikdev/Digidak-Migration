package com.digidak.migration.util;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DateUtil
 */
class DateUtilTest {

    @Test
    void testParseDateWithSlashFormat() {
        String dateStr = "01/05/2024, 11:58:47 AM";
        Date date = DateUtil.parseDate(dateStr);

        assertNotNull(date);
    }

    @Test
    void testParseDateWithDashFormat() {
        String dateStr = "2024-01-05 11:58:47";
        Date date = DateUtil.parseDate(dateStr);

        assertNotNull(date);
    }

    @Test
    void testParseDateWithNullInput() {
        Date date = DateUtil.parseDate(null);
        assertNull(date);
    }

    @Test
    void testParseDateWithEmptyString() {
        Date date = DateUtil.parseDate("");
        assertNull(date);
    }

    @Test
    void testFormatDate() {
        Date now = new Date();
        String formatted = DateUtil.formatDate(now);

        assertNotNull(formatted);
        assertTrue(formatted.contains("-"));
    }

    @Test
    void testFormatDateWithCustomFormat() {
        Date now = new Date();
        String formatted = DateUtil.formatDate(now, "yyyy/MM/dd");

        assertNotNull(formatted);
        assertTrue(formatted.contains("/"));
    }

    @Test
    void testFormatDateWithNull() {
        String formatted = DateUtil.formatDate(null);
        assertNull(formatted);
    }
}
