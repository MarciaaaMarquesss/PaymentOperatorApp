package com.marcia.paymentoperator.domain.engine;

import androidx.lifecycle.LiveData;

import com.elecctro.recruitment.paymentterminal.AuthorizationResult;
import com.elecctro.recruitment.paymentterminal.ErrorCode;
import com.elecctro.recruitment.paymentterminal.State;
import com.elecctro.recruitment.paymentterminal.TerminalResult;
import com.marcia.paymentoperator.data.gateway.PaymentTerminalGateway;
import com.marcia.paymentoperator.data.repository.TransactionRepository;
import com.marcia.paymentoperator.domain.model.Transaction;
import com.marcia.paymentoperator.domain.model.TransactionState;

import java.util.List;
import java.util.UUID;

public class TransactionEngine {

    private static final int DEFAULT_MAX_RETRY_ATTEMPTS = 3;
    private static final long DEFAULT_RETRY_BASE_DELAY_MILLIS = 1000L;
    private static final long MIN_CAPTURE_AMOUNT_CENTS = 1L;
    private static final long NO_CAPTURE_AMOUNT = 0L;
    private static final String RECOVERY_ERROR = "RECOVERY";

    private final PaymentTerminalGateway gateway;
    private final TransactionRepository repository;
    private final RetryPolicy retryPolicy;
    private final RetryScheduler retryScheduler;

    public TransactionEngine(PaymentTerminalGateway gateway, TransactionRepository repository) {
        this(
                gateway,
                repository,
                new RetryPolicy(DEFAULT_MAX_RETRY_ATTEMPTS, DEFAULT_RETRY_BASE_DELAY_MILLIS),
                new ExecutorRetryScheduler()
        );
    }

    public TransactionEngine(PaymentTerminalGateway gateway,
                             TransactionRepository repository,
                             RetryPolicy retryPolicy,
                             RetryScheduler retryScheduler) {
        this.gateway = gateway;
        this.repository = repository;
        this.retryPolicy = retryPolicy;
        this.retryScheduler = retryScheduler;
    }

    public synchronized Transaction authorize(long amount) {
        UUID id = UUID.randomUUID();
        Transaction tx = new Transaction(id, amount);
        repository.save(tx);

        gateway.authorize(id, amount)
                .subscribe(
                        result -> handleAuthorizationResult(id, result),
                        throwable -> failAuthorization(id, throwable)
                );

        return tx;
    }

    public synchronized LiveData<List<Transaction>> observeTransactions() {
        return repository.observeTransactions();
    }

    public synchronized boolean capture(UUID txnId, long amount) {
        Transaction tx = repository.getTransaction(txnId);
        if (tx == null || tx.getState() != TransactionState.AUTHORIZED) {
            return false;
        }
        if (amount < MIN_CAPTURE_AMOUNT_CENTS || amount > tx.getAmountApproved()) {
            return false;
        }

        tx.setCaptureAmount(amount);
        tx.resetRetries();
        tx.updateState(TransactionState.CAPTURING);
        repository.update(tx);
        issueCaptureAttempt(txnId);
        return true;
    }

    public synchronized boolean cancel(UUID txnId) {
        Transaction tx = repository.getTransaction(txnId);
        if (tx == null) {
            return false;
        }
        if (tx.getState() != TransactionState.AUTHORIZED
                && tx.getState() != TransactionState.AUTHORIZING) {
            return false;
        }

        tx.resetRetries();
        tx.updateState(TransactionState.CANCELLING);
        repository.update(tx);
        issueCancelAttempt(txnId);
        return true;
    }

    public synchronized boolean retry(UUID txnId) {
        Transaction tx = repository.getTransaction(txnId);
        if (tx == null) {
            return false;
        }

        if (tx.getState() == TransactionState.CAPTURE_FAILED && tx.getCaptureAmount() > NO_CAPTURE_AMOUNT) {
            tx.resetRetries();
            tx.updateState(TransactionState.CAPTURING);
            repository.update(tx);
            issueCaptureAttempt(txnId);
            return true;
        }

        if (tx.getState() == TransactionState.CANCEL_FAILED) {
            tx.resetRetries();
            tx.updateState(TransactionState.CANCELLING);
            repository.update(tx);
            issueCancelAttempt(txnId);
            return true;
        }

        return false;
    }

    public synchronized void recoverInFlight() {
        List<Transaction> transactions = repository.getAll();
        for (Transaction tx : transactions) {
            if (tx.getState() == TransactionState.AUTHORIZING) {
                tx.updateState(TransactionState.CANCELLING, RECOVERY_ERROR);
                repository.update(tx);
                issueCancelAttempt(tx.getId());
            } else if (tx.getState() == TransactionState.CAPTURING) {
                issueCaptureAttempt(tx.getId());
            } else if (tx.getState() == TransactionState.CANCELLING) {
                issueCancelAttempt(tx.getId());
            }
        }
    }

    private synchronized void handleAuthorizationResult(UUID txnId, AuthorizationResult result) {
        Transaction tx = repository.getTransaction(txnId);
        if (tx == null || tx.getState() != TransactionState.AUTHORIZING) {
            return;
        }

        if (result.state == State.APPROVED) {
            tx.setAmountApproved(result.approvedAmount);
            tx.clearError();
            tx.updateState(TransactionState.AUTHORIZED);
            repository.update(tx);
            return;
        }

        if (result.state == State.DECLINED) {
            tx.updateState(TransactionState.DECLINED, errorName(result.error));
            repository.update(tx);
            return;
        }

        tx.resetRetries();
        tx.updateState(TransactionState.CANCELLING, errorName(result.error));
        repository.update(tx);
        issueCancelAttempt(txnId);
    }

    private synchronized void failAuthorization(UUID txnId, Throwable throwable) {
        Transaction tx = repository.getTransaction(txnId);
        if (tx == null || tx.getState() != TransactionState.AUTHORIZING) {
            return;
        }
        tx.updateState(TransactionState.DECLINED, throwable.getClass().getSimpleName());
        repository.update(tx);
    }

    private synchronized void issueCaptureAttempt(UUID txnId) {
        Transaction tx = repository.getTransaction(txnId);
        if (tx == null || tx.getState() != TransactionState.CAPTURING) {
            return;
        }

        tx.incrementRetry();
        repository.update(tx);

        gateway.capture(txnId, tx.getCaptureAmount())
                .subscribe(
                        result -> handleCaptureResult(txnId, result),
                        throwable -> failCapture(txnId, throwable.getClass().getSimpleName())
                );
    }

    private synchronized void handleCaptureResult(UUID txnId, TerminalResult result) {
        Transaction tx = repository.getTransaction(txnId);
        if (tx == null || tx.getState() != TransactionState.CAPTURING) {
            return;
        }

        if (result.state == State.APPROVED
                || (result.state == State.DECLINED && result.error == ErrorCode.ALREADY_CAPTURED)) {
            tx.clearError();
            tx.updateState(TransactionState.CAPTURED);
            repository.update(tx);
            return;
        }

        if (result.state == State.TIMED_OUT) {
            retryCaptureOrFail(tx, errorName(result.error));
            return;
        }

        tx.updateState(TransactionState.CAPTURE_FAILED, errorName(result.error));
        repository.update(tx);
    }

    private synchronized void retryCaptureOrFail(Transaction tx, String error) {
        if (tx.getRetryCount() >= retryPolicy.getMaxAttempts()) {
            tx.updateState(TransactionState.CAPTURE_FAILED, error);
            repository.update(tx);
            return;
        }

        long delay = retryPolicy.delayForAttempt(tx.getRetryCount());
        retryScheduler.schedule(() -> issueCaptureAttempt(tx.getId()), delay);
    }

    private synchronized void failCapture(UUID txnId, String error) {
        Transaction tx = repository.getTransaction(txnId);
        if (tx == null || tx.getState() != TransactionState.CAPTURING) {
            return;
        }
        tx.updateState(TransactionState.CAPTURE_FAILED, error);
        repository.update(tx);
    }

    private synchronized void issueCancelAttempt(UUID txnId) {
        Transaction tx = repository.getTransaction(txnId);
        if (tx == null || tx.getState() != TransactionState.CANCELLING) {
            return;
        }

        tx.incrementRetry();
        repository.update(tx);

        gateway.cancel(txnId)
                .subscribe(
                        result -> handleCancelResult(txnId, result),
                        throwable -> failCancel(txnId, throwable.getClass().getSimpleName())
                );
    }

    private synchronized void handleCancelResult(UUID txnId, TerminalResult result) {
        Transaction tx = repository.getTransaction(txnId);
        if (tx == null || tx.getState() != TransactionState.CANCELLING) {
            return;
        }

        if (result.state == State.APPROVED
                || (result.state == State.DECLINED && result.error == ErrorCode.UNKNOWN_TRANSACTION_ID)
                || (result.state == State.DECLINED && result.error == ErrorCode.ALREADY_CANCELLED)) {
            tx.clearError();
            tx.updateState(TransactionState.CANCELLED);
            repository.update(tx);
            return;
        }

        if (result.state == State.TIMED_OUT) {
            retryCancelOrFail(tx, errorName(result.error));
            return;
        }

        tx.updateState(TransactionState.CANCEL_FAILED, errorName(result.error));
        repository.update(tx);
    }

    private synchronized void retryCancelOrFail(Transaction tx, String error) {
        if (tx.getRetryCount() >= retryPolicy.getMaxAttempts()) {
            tx.updateState(TransactionState.CANCEL_FAILED, error);
            repository.update(tx);
            return;
        }

        long delay = retryPolicy.delayForAttempt(tx.getRetryCount());
        retryScheduler.schedule(() -> issueCancelAttempt(tx.getId()), delay);
    }

    private synchronized void failCancel(UUID txnId, String error) {
        Transaction tx = repository.getTransaction(txnId);
        if (tx == null || tx.getState() != TransactionState.CANCELLING) {
            return;
        }
        tx.updateState(TransactionState.CANCEL_FAILED, error);
        repository.update(tx);
    }

    private String errorName(ErrorCode errorCode) {
        return errorCode == null ? null : errorCode.name();
    }
}
