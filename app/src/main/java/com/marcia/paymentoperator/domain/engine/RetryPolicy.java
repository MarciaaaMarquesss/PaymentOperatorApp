package com.marcia.paymentoperator.domain.engine;

public class RetryPolicy {

    private final int maxAttempts;
    private final long baseDelayMillis;

    public RetryPolicy(int maxAttempts, long baseDelayMillis) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMillis = baseDelayMillis;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long delayForAttempt(int attempt) {
        if (attempt <= 1) {
            return baseDelayMillis;
        }
        long multiplier = 1L << Math.min(attempt - 1, 10);
        return baseDelayMillis * multiplier;
    }
}
