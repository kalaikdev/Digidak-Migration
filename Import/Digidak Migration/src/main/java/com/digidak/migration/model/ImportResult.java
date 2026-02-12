package com.digidak.migration.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Model class to track import results and statistics
 */
public class ImportResult {
    private AtomicInteger totalDocuments = new AtomicInteger(0);
    private AtomicInteger successfulImports = new AtomicInteger(0);
    private AtomicInteger failedImports = new AtomicInteger(0);
    private AtomicInteger foldersCreated = new AtomicInteger(0);
    private AtomicInteger movementRegistersCreated = new AtomicInteger(0);

    private List<String> errors = new ArrayList<>();
    private long startTime;
    private long endTime;

    public ImportResult() {
        this.startTime = System.currentTimeMillis();
    }

    public void incrementTotal() {
        totalDocuments.incrementAndGet();
    }

    public void incrementSuccess() {
        successfulImports.incrementAndGet();
    }

    public void incrementFailed() {
        failedImports.incrementAndGet();
    }

    public void incrementFoldersCreated() {
        foldersCreated.incrementAndGet();
    }

    public void incrementMovementRegisters() {
        movementRegistersCreated.incrementAndGet();
    }

    public synchronized void addError(String error) {
        errors.add(error);
    }

    public void markComplete() {
        this.endTime = System.currentTimeMillis();
    }

    public long getDurationMillis() {
        return (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
    }

    public int getTotalDocuments() {
        return totalDocuments.get();
    }

    public int getSuccessfulImports() {
        return successfulImports.get();
    }

    public int getFailedImports() {
        return failedImports.get();
    }

    public int getFoldersCreated() {
        return foldersCreated.get();
    }

    public int getMovementRegistersCreated() {
        return movementRegistersCreated.get();
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    @Override
    public String toString() {
        return "ImportResult{" +
                "totalDocuments=" + totalDocuments.get() +
                ", successfulImports=" + successfulImports.get() +
                ", failedImports=" + failedImports.get() +
                ", foldersCreated=" + foldersCreated.get() +
                ", movementRegistersCreated=" + movementRegistersCreated.get() +
                ", durationMillis=" + getDurationMillis() +
                '}';
    }
}
