package com.marcia.paymentoperator.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Transaction {

    private final UUID id;
    private final long amountRequested;

    private long amountApproved;

    private TransactionState state;

    private int retryCount;

    private long lastUpdated;

    private final List<String> history = new ArrayList<>();

    public Transaction(UUID id, long amountRequested) {
        this.id = id;
        this.amountRequested = amountRequested;
        this.state = TransactionState.AUTHORIZING;
        this.retryCount = 0;
        this.lastUpdated = System.currentTimeMillis();

        addEvent(TransactionState.AUTHORIZING.name());
    }

    public UUID getId() {
        return id;
    }

    public long getAmountRequested() {
        return amountRequested;
    }

    public long getAmountApproved() {
        return amountApproved;
    }

    public TransactionState getState() {
        return state;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public List<String> getHistory() {
        return history;
    }

    public void updateState(TransactionState newState) {
        this.state = newState;
        this.lastUpdated = System.currentTimeMillis();
        addEvent("STATE → " + newState.name());
    }

    public void setAmountApproved(long amountApproved) {
        this.amountApproved = amountApproved;
        addEvent("APPROVED_AMOUNT → " + amountApproved);
    }

    public void incrementRetry() {
        this.retryCount++;
        addEvent("RETRY → " + retryCount);
    }

    private void addEvent(String event) {
        history.add(System.currentTimeMillis() + " | " + event);
    }
}
