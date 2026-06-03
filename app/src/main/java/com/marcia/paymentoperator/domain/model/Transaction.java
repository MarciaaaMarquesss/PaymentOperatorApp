package com.marcia.paymentoperator.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Transaction {

    private static final long INITIAL_AMOUNT = 0L;
    private static final int INITIAL_RETRY_COUNT = 0;
    private static final int RETRY_INCREMENT = 1;
    private static final String EVENT_STATE = "STATE ";
    private static final String EVENT_ERROR = " ERROR ";
    private static final String EVENT_APPROVED_AMOUNT = "APPROVED_AMOUNT ";
    private static final String EVENT_CAPTURE_AMOUNT = "CAPTURE_AMOUNT ";
    private static final String EVENT_RETRY_RESET = "RETRY_RESET";
    private static final String EVENT_RETRY = "RETRY ";
    private static final String EVENT_SEPARATOR = " | ";

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
        this.amountApproved = INITIAL_AMOUNT;
        this.captureAmount = INITIAL_AMOUNT;
        this.state = TransactionState.AUTHORIZING;
        this.retryCount = INITIAL_RETRY_COUNT;
        this.lastError = null;
        this.lastUpdated = now;
        this.history = new ArrayList<>();

        addEvent(now, EVENT_STATE + TransactionState.AUTHORIZING.name());
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
        touch(EVENT_APPROVED_AMOUNT + amountApproved);
    }

    public void setCaptureAmount(long captureAmount) {
        this.captureAmount = captureAmount;
        touch(EVENT_CAPTURE_AMOUNT + captureAmount);
    }

    public void updateState(TransactionState newState) {
        updateState(newState, null);
    }

    public void updateState(TransactionState newState, String error) {
        this.state = newState;
        this.lastError = error;
        touch(error == null
                ? EVENT_STATE + newState.name()
                : EVENT_STATE + newState.name() + EVENT_ERROR + error);
    }

    public void clearError() {
        this.lastError = null;
    }

    public void resetRetries() {
        this.retryCount = INITIAL_RETRY_COUNT;
        touch(EVENT_RETRY_RESET);
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
        touch(EVENT_RETRY + retryCount);
    }

    public void incrementRetry() {
        setRetryCount(retryCount + RETRY_INCREMENT);
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
        history.add(timestamp + EVENT_SEPARATOR + event);
    }
}
