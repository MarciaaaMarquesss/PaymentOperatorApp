package com.marcia.paymentoperator.domain.engine;

public interface RetryScheduler {
    void schedule(Runnable runnable, long delayMillis);
}
