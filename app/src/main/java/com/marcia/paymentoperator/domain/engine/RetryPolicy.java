package com.marcia.paymentoperator.domain.engine;

public class RetryPolicy {

    private static final int FIRST_RETRY_ATTEMPT = 1;
    private static final int MAX_BACKOFF_SHIFT = 10;

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
        if (attempt <= FIRST_RETRY_ATTEMPT) {
            return baseDelayMillis;
        }
        long multiplier = 1L << Math.min(attempt - FIRST_RETRY_ATTEMPT, MAX_BACKOFF_SHIFT);
        return baseDelayMillis * multiplier;
    }
}
