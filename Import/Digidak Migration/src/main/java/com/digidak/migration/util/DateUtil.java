package com.digidak.migration.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Utility class for date parsing and formatting
 */
public class DateUtil {
    private static final Logger logger = LogManager.getLogger(DateUtil.class);

    // Common date formats found in metadata
    private static final String[] DATE_FORMATS = {
            "MM/dd/yyyy, hh:mm:ss a",
            "MM/dd/yyyy HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "dd/MM/yyyy HH:mm:ss",
            "MM/dd/yyyy",
            "yyyy-MM-dd"
    };

    /**
     * Parse date string using multiple format attempts
     */
    public static Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        for (String format : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                sdf.setLenient(false);
                return sdf.parse(dateStr);
            } catch (ParseException e) {
                // Try next format
            }
        }

        logger.warn("Unable to parse date: {}", dateStr);
        return new Date(); // Return current date as fallback
    }

    /**
     * Format date to Documentum standard format
     */
    public static String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }

    /**
     * Format date to specific format
     */
    public static String formatDate(Date date, String format) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(date);
    }
}
