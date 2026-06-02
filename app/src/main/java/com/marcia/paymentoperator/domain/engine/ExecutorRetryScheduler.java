package com.marcia.paymentoperator.domain.engine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExecutorRetryScheduler implements RetryScheduler {

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void schedule(Runnable runnable, long delayMillis) {
        executorService.schedule(runnable, delayMillis, TimeUnit.MILLISECONDS);
    }
}
