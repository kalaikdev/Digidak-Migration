package com.digidak.migration.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for handling Documentum repeating attributes
 */
public class RepeatingAttributeHandler {
    private static final Logger logger = LogManager.getLogger(RepeatingAttributeHandler.class);

    /**
     * Append value to repeating attribute array
     * Simulates DFC IDfAttr append functionality
     */
    public static <T> List<T> appendValue(List<T> existingValues, T newValue) {
        if (existingValues == null) {
            existingValues = new ArrayList<>();
        }

        if (newValue != null && !existingValues.contains(newValue)) {
            existingValues.add(newValue);
            logger.debug("Appended value to repeating attribute: {}", newValue);
        }

        return existingValues;
    }

    /**
     * Update or append repeating attribute values
     */
    public static <T> List<T> updateRepeatingAttribute(List<T> existingValues,
                                                        List<T> newValues,
                                                        boolean append) {
        if (newValues == null || newValues.isEmpty()) {
            return existingValues;
        }

        if (!append || existingValues == null) {
            // Replace existing values
            return new ArrayList<>(newValues);
        }

        // Append new values
        List<T> result = new ArrayList<>(existingValues);
        for (T newValue : newValues) {
            if (newValue != null && !result.contains(newValue)) {
                result.add(newValue);
            }
        }

        return result;
    }

    /**
     * Remove value from repeating attribute
     */
    public static <T> List<T> removeValue(List<T> existingValues, T valueToRemove) {
        if (existingValues == null || valueToRemove == null) {
            return existingValues;
        }

        List<T> result = new ArrayList<>(existingValues);
        result.remove(valueToRemove);
        logger.debug("Removed value from repeating attribute: {}", valueToRemove);

        return result;
    }

    /**
     * Insert value at specific index in repeating attribute
     */
    public static <T> List<T> insertValue(List<T> existingValues, int index, T newValue) {
        if (existingValues == null) {
            existingValues = new ArrayList<>();
        }

        if (newValue != null) {
            if (index >= 0 && index <= existingValues.size()) {
                existingValues.add(index, newValue);
                logger.debug("Inserted value at index {}: {}", index, newValue);
            } else {
                logger.warn("Invalid index {} for repeating attribute insertion", index);
            }
        }

        return existingValues;
    }
}
