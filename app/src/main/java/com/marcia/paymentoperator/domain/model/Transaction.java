package com.marcia.paymentoperator.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Transaction {

    private final UUID id;
    private final long amountRequested;
    private final long createdAt;

    private long amountApproved;
    private long captureAmount;
    private TransactionState state;
    private int retryCount;
    private String lastError;
    private long lastUpdated;

    private final List<String> history;

    public Transaction(UUID id, long amountRequested) {
        long now = System.currentTimeMillis();
        this.id = id;
        this.amountRequested = amountRequested;
        this.createdAt = now;
        this.amountApproved = 0L;
        this.captureAmount = 0L;
        this.state = TransactionState.AUTHORIZING;
        this.retryCount = 0;
        this.lastError = null;
        this.lastUpdated = now;
        this.history = new ArrayList<>();

        addEvent(now, "STATE AUTHORIZING");
    }

    private Transaction(UUID id,
                        long amountRequested,
                        long createdAt,
                        long amountApproved,
                        long captureAmount,
                        TransactionState state,
                        int retryCount,
                        String lastError,
                        long lastUpdated,
                        List<String> history) {
        this.id = id;
        this.amountRequested = amountRequested;
        this.createdAt = createdAt;
        this.amountApproved = amountApproved;
        this.captureAmount = captureAmount;
        this.state = state;
        this.retryCount = retryCount;
        this.lastError = lastError;
        this.lastUpdated = lastUpdated;
        this.history = new ArrayList<>(history);
    }

    public static Transaction restore(UUID id,
                                      long amountRequested,
                                      long createdAt,
                                      long amountApproved,
                                      long captureAmount,
                                      TransactionState state,
                                      int retryCount,
                                      String lastError,
                                      long lastUpdated,
                                      List<String> history) {
        return new Transaction(
                id,
                amountRequested,
                createdAt,
                amountApproved,
                captureAmount,
                state,
                retryCount,
                lastError,
                lastUpdated,
                history
        );
    }

    public Transaction copy() {
        return new Transaction(
                id,
                amountRequested,
                createdAt,
                amountApproved,
                captureAmount,
                state,
                retryCount,
                lastError,
                lastUpdated,
                history
        );
    }

    public UUID getId() {
        return id;
    }

    public long getAmountRequested() {
        return amountRequested;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getAmountApproved() {
        return amountApproved;
    }

    public long getCaptureAmount() {
        return captureAmount;
    }

    public TransactionState getState() {
        return state;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public List<String> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void setAmountApproved(long amountApproved) {
        this.amountApproved = amountApproved;
        touch("APPROVED_AMOUNT " + amountApproved);
    }

    public void setCaptureAmount(long captureAmount) {
        this.captureAmount = captureAmount;
        touch("CAPTURE_AMOUNT " + captureAmount);
    }

    public void updateState(TransactionState newState) {
        updateState(newState, null);
    }

    public void updateState(TransactionState newState, String error) {
        this.state = newState;
        this.lastError = error;
        touch(error == null ? "STATE " + newState.name() : "STATE " + newState.name() + " ERROR " + error);
    }

    public void clearError() {
        this.lastError = null;
    }

    public void resetRetries() {
        this.retryCount = 0;
        touch("RETRY_RESET");
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
        touch("RETRY " + retryCount);
    }

    public void incrementRetry() {
        setRetryCount(retryCount + 1);
    }

    public boolean isTerminal() {
        return state == TransactionState.CAPTURED
                || state == TransactionState.CANCELLED
                || state == TransactionState.DECLINED
                || state == TransactionState.CAPTURE_FAILED
                || state == TransactionState.CANCEL_FAILED;
    }

    private void touch(String event) {
        long now = System.currentTimeMillis();
        this.lastUpdated = now;
        addEvent(now, event);
    }

    private void addEvent(long timestamp, String event) {
        history.add(timestamp + " | " + event);
    }
}
