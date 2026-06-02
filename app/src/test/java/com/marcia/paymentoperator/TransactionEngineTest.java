package com.marcia.paymentoperator;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.elecctro.recruitment.paymentterminal.AuthorizationResult;
import com.elecctro.recruitment.paymentterminal.ErrorCode;
import com.elecctro.recruitment.paymentterminal.State;
import com.elecctro.recruitment.paymentterminal.TerminalResult;
import com.marcia.paymentoperator.data.gateway.PaymentTerminalGateway;
import com.marcia.paymentoperator.data.repository.TransactionRepository;
import com.marcia.paymentoperator.data.repository.TransactionRepositoryImpl;
import com.marcia.paymentoperator.domain.engine.RetryPolicy;
import com.marcia.paymentoperator.domain.engine.RetryScheduler;
import com.marcia.paymentoperator.domain.engine.TransactionEngine;
import com.marcia.paymentoperator.domain.model.Transaction;
import com.marcia.paymentoperator.domain.model.TransactionState;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Single;
import io.reactivex.subjects.SingleSubject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransactionEngineTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    // Happy paths move through the legal states, and terminal transactions cannot be cancelled again.
    @Test
    public void authorizeCaptureAndCancelFollowLegalTransitions() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.authorizations.add(new AuthorizationResult(State.APPROVED, null, 1000));
        gateway.captures.add(new TerminalResult(State.APPROVED, null));

        TransactionRepositoryImpl repository = repository();
        TransactionEngine engine = engine(gateway, repository);

        Transaction tx = engine.authorize(1000);
        assertEquals(TransactionState.AUTHORIZED, repository.getTransaction(tx.getId()).getState());

        assertTrue(engine.capture(tx.getId(), 1000));
        assertEquals(TransactionState.CAPTURED, repository.getTransaction(tx.getId()).getState());

        assertFalse(engine.cancel(tx.getId()));
        assertEquals(0, gateway.cancelCalls);

        gateway.authorizations.add(new AuthorizationResult(State.APPROVED, null, 500));
        gateway.cancels.add(new TerminalResult(State.APPROVED, null));

        Transaction cancellable = engine.authorize(500);
        assertTrue(engine.cancel(cancellable.getId()));
        assertEquals(TransactionState.CANCELLED, repository.getTransaction(cancellable.getId()).getState());
    }

    // Capture must be rejected locally when the amount is invalid, before the terminal is called.
    @Test
    public void illegalCaptureIsRejectedWithoutTerminalCall() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.authorizations.add(new AuthorizationResult(State.APPROVED, null, 700));

        TransactionRepositoryImpl repository = repository();
        TransactionEngine engine = engine(gateway, repository);

        Transaction tx = engine.authorize(1000);

        assertFalse(engine.capture(tx.getId(), 701));
        assertFalse(engine.capture(tx.getId(), 0));
        assertEquals(0, gateway.captureCalls);
        assertEquals(TransactionState.AUTHORIZED, repository.getTransaction(tx.getId()).getState());
    }

    // A declined authorization is a final state and keeps the terminal error visible to the operator.
    @Test
    public void authorizationDeclineStoresError() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.authorizations.add(new AuthorizationResult(State.DECLINED, ErrorCode.INSUFFICIENT_FUNDS, 0));

        TransactionRepositoryImpl repository = repository();
        TransactionEngine engine = engine(gateway, repository);

        Transaction tx = engine.authorize(1000);

        assertEquals(TransactionState.DECLINED, repository.getTransaction(tx.getId()).getState());
        assertEquals("INSUFFICIENT_FUNDS", repository.getTransaction(tx.getId()).getLastError());
        assertEquals(1, gateway.authorizeCalls);
        assertEquals(0, gateway.cancelCalls);
    }

    // If the operator cancels during authorization, a later authorization result must not reopen the transaction.
    @Test
    public void cancelDuringAuthorizingInterruptsAndIgnoresLateAuthorization() throws Exception {
        RacingGateway gateway = new RacingGateway();
        TransactionRepositoryImpl repository = repository();
        TransactionEngine engine = new TransactionEngine(
                gateway,
                repository,
                new RetryPolicy(3, 0L),
                new ImmediateScheduler()
        );

        Transaction tx = engine.authorize(1000);
        assertEquals(TransactionState.AUTHORIZING, repository.getTransaction(tx.getId()).getState());

        assertTrue(engine.cancel(tx.getId()));
        assertEquals(TransactionState.CANCELLED, repository.getTransaction(tx.getId()).getState());

        gateway.authorization.onSuccess(new AuthorizationResult(State.APPROVED, null, 1000));

        assertEquals(TransactionState.CANCELLED, repository.getTransaction(tx.getId()).getState());
        assertEquals(1, gateway.authorizeCalls.get());
        assertEquals(1, gateway.cancelCalls.get());
    }

    // Illegal commands for each state must be rejected without issuing authorize, capture, or cancel calls.
    @Test
    public void illegalActionsAreRejectedWithoutTerminalCalls() throws Exception {
        FakeGateway gateway = new FakeGateway();
        TransactionRepositoryImpl repository = repository();
        TransactionEngine engine = engine(gateway, repository);

        Transaction authorizing = saveTransaction(repository, TransactionState.AUTHORIZING);
        assertFalse(engine.capture(authorizing.getId(), 100));
        assertFalse(engine.retry(authorizing.getId()));

        Transaction authorized = saveTransaction(repository, TransactionState.AUTHORIZED);
        assertFalse(engine.retry(authorized.getId()));

        Transaction capturing = saveTransaction(repository, TransactionState.CAPTURING);
        assertFalse(engine.cancel(capturing.getId()));
        assertFalse(engine.retry(capturing.getId()));

        Transaction captured = saveTransaction(repository, TransactionState.CAPTURED);
        assertFalse(engine.capture(captured.getId(), 100));
        assertFalse(engine.cancel(captured.getId()));
        assertFalse(engine.retry(captured.getId()));

        Transaction cancelled = saveTransaction(repository, TransactionState.CANCELLED);
        assertFalse(engine.capture(cancelled.getId(), 100));
        assertFalse(engine.cancel(cancelled.getId()));
        assertFalse(engine.retry(cancelled.getId()));

        Transaction declined = saveTransaction(repository, TransactionState.DECLINED);
        assertFalse(engine.capture(declined.getId(), 100));
        assertFalse(engine.cancel(declined.getId()));
        assertFalse(engine.retry(declined.getId()));

        Transaction captureFailed = saveTransaction(repository, TransactionState.CAPTURE_FAILED, 0);
        assertFalse(engine.capture(captureFailed.getId(), 100));
        assertFalse(engine.cancel(captureFailed.getId()));
        assertFalse(engine.retry(captureFailed.getId()));

        Transaction cancelFailed = saveTransaction(repository, TransactionState.CANCEL_FAILED);
        assertFalse(engine.capture(cancelFailed.getId(), 100));
        assertFalse(engine.cancel(cancelFailed.getId()));

        assertEquals(0, gateway.authorizeCalls);
        assertEquals(0, gateway.captureCalls);
        assertEquals(0, gateway.cancelCalls);
    }

    // A timed-out authorization has unknown processor state, so it must resolve through cancel, not re-authorize.
    @Test
    public void timedOutAuthorizationResolvesThroughCancelChain() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.authorizations.add(new AuthorizationResult(State.TIMED_OUT, ErrorCode.NETWORK_ERROR, 0));
        gateway.cancels.add(new TerminalResult(State.TIMED_OUT, ErrorCode.NETWORK_ERROR));
        gateway.cancels.add(new TerminalResult(State.APPROVED, null));

        TransactionRepositoryImpl repository = repository();
        TransactionEngine engine = engine(gateway, repository);

        Transaction tx = engine.authorize(1000);
        Transaction stored = repository.getTransaction(tx.getId());

        assertEquals(TransactionState.CANCELLED, stored.getState());
        assertEquals(1, gateway.authorizeCalls);
        assertEquals(2, gateway.cancelCalls);
    }

    // User cancel and automatic timeout cancel can race, but only one cancel chain may be issued.
    @Test
    public void cancelRaceStartsOnlyOneCancelChain() throws Exception {
        RacingGateway gateway = new RacingGateway();
        RacingRepository repository = new RacingRepository();
        TransactionEngine engine = new TransactionEngine(
                gateway,
                repository,
                new RetryPolicy(3, 0L),
                new ImmediateScheduler()
        );

        Transaction tx = engine.authorize(1000);
        repository.raceReadsFor(tx.getId());

        AtomicReference<Boolean> cancelAccepted = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);

        Thread userCancel = new Thread(() -> {
            try {
                start.await();
                cancelAccepted.set(engine.cancel(tx.getId()));
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });

        Thread authorizationTimeout = new Thread(() -> {
            try {
                start.await();
                gateway.authorization.onSuccess(
                        new AuthorizationResult(State.TIMED_OUT, ErrorCode.NETWORK_ERROR, 0)
                );
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });

        userCancel.start();
        authorizationTimeout.start();
        start.countDown();
        userCancel.join();
        authorizationTimeout.join();

        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }

        assertEquals(1, gateway.authorizeCalls.get());
        assertEquals(1, gateway.cancelCalls.get());
        assertEquals(TransactionState.CANCELLED, repository.getTransaction(tx.getId()).getState());
    }

    // Idempotent terminal evidence confirms final state when a previous capture/cancel attempt already landed.
    @Test
    public void idempotentTerminalEvidenceResolvesToFinalStates() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.authorizations.add(new AuthorizationResult(State.APPROVED, null, 1000));
        gateway.captures.add(new TerminalResult(State.DECLINED, ErrorCode.ALREADY_CAPTURED));
        gateway.authorizations.add(new AuthorizationResult(State.APPROVED, null, 500));
        gateway.cancels.add(new TerminalResult(State.DECLINED, ErrorCode.ALREADY_CANCELLED));

        TransactionRepositoryImpl repository = repository();
        TransactionEngine engine = engine(gateway, repository);

        Transaction captured = engine.authorize(1000);
        assertTrue(engine.capture(captured.getId(), 700));
        assertEquals(TransactionState.CAPTURED, repository.getTransaction(captured.getId()).getState());

        Transaction cancelled = engine.authorize(500);
        assertTrue(engine.cancel(cancelled.getId()));
        assertEquals(TransactionState.CANCELLED, repository.getTransaction(cancelled.getId()).getState());
    }

    // Capture retries are bounded; after failure, the operator can retry the same capture amount.
    @Test
    public void captureRetriesStopAndOperatorCanRetry() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.authorizations.add(new AuthorizationResult(State.APPROVED, null, 1000));
        gateway.captures.add(new TerminalResult(State.TIMED_OUT, ErrorCode.NETWORK_ERROR));
        gateway.captures.add(new TerminalResult(State.TIMED_OUT, ErrorCode.NETWORK_ERROR));
        gateway.captures.add(new TerminalResult(State.TIMED_OUT, ErrorCode.NETWORK_ERROR));
        gateway.captures.add(new TerminalResult(State.APPROVED, null));

        TransactionRepositoryImpl repository = repository();
        TransactionEngine engine = engine(gateway, repository);

        Transaction tx = engine.authorize(1000);
        assertTrue(engine.capture(tx.getId(), 500));

        assertEquals(TransactionState.CAPTURE_FAILED, repository.getTransaction(tx.getId()).getState());
        assertEquals("NETWORK_ERROR", repository.getTransaction(tx.getId()).getLastError());

        assertTrue(engine.retry(tx.getId()));
        assertEquals(TransactionState.CAPTURED, repository.getTransaction(tx.getId()).getState());
        assertEquals(4, gateway.captureCalls);
    }

    // Cancel retries are bounded; after failure, the operator can retry cancellation.
    @Test
    public void cancelRetriesStopAndOperatorCanRetry() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.authorizations.add(new AuthorizationResult(State.APPROVED, null, 1000));
        gateway.cancels.add(new TerminalResult(State.TIMED_OUT, ErrorCode.NETWORK_ERROR));
        gateway.cancels.add(new TerminalResult(State.TIMED_OUT, ErrorCode.NETWORK_ERROR));
        gateway.cancels.add(new TerminalResult(State.TIMED_OUT, ErrorCode.NETWORK_ERROR));
        gateway.cancels.add(new TerminalResult(State.APPROVED, null));

        TransactionRepositoryImpl repository = repository();
        TransactionEngine engine = engine(gateway, repository);

        Transaction tx = engine.authorize(1000);
        assertTrue(engine.cancel(tx.getId()));

        assertEquals(TransactionState.CANCEL_FAILED, repository.getTransaction(tx.getId()).getState());
        assertEquals("NETWORK_ERROR", repository.getTransaction(tx.getId()).getLastError());

        assertTrue(engine.retry(tx.getId()));
        assertEquals(TransactionState.CANCELLED, repository.getTransaction(tx.getId()).getState());
        assertEquals(4, gateway.cancelCalls);
    }

    // Restarting with AUTHORIZING on disk must interrogate with cancel and never call authorize again.
    @Test
    public void recoveryAuthorizingCancelsWithoutDuplicateAuthorize() throws Exception {
        UUID id = UUID.randomUUID();
        TransactionRepositoryImpl repository = repository();
        repository.save(new Transaction(id, 1000));

        FakeGateway gateway = new FakeGateway();
        gateway.cancels.add(new TerminalResult(State.DECLINED, ErrorCode.UNKNOWN_TRANSACTION_ID));

        TransactionEngine engine = engine(gateway, repository);
        engine.recoverInFlight();

        assertEquals(TransactionState.CANCELLED, repository.getTransaction(id).getState());
        assertEquals(0, gateway.authorizeCalls);
        assertEquals(1, gateway.cancelCalls);
    }

    // Restarting with CAPTURING on disk retries capture and treats an unknown transaction as capture failure.
    @Test
    public void recoveryCapturingRetriesCapture() throws Exception {
        UUID id = UUID.randomUUID();
        Transaction tx = new Transaction(id, 1000);
        tx.setAmountApproved(1000);
        tx.setCaptureAmount(600);
        tx.updateState(TransactionState.CAPTURING);

        TransactionRepositoryImpl repository = repository();
        repository.save(tx);

        FakeGateway gateway = new FakeGateway();
        gateway.captures.add(new TerminalResult(State.DECLINED, ErrorCode.UNKNOWN_TRANSACTION_ID));

        TransactionEngine engine = engine(gateway, repository);
        engine.recoverInFlight();

        assertEquals(TransactionState.CAPTURE_FAILED, repository.getTransaction(id).getState());
        assertEquals(1, gateway.captureCalls);
    }

    // ALREADY_CAPTURED after restart means the capture landed before the app could persist the final state.
    @Test
    public void recoveryCapturingTreatsAlreadyCapturedAsCaptured() throws Exception {
        UUID id = UUID.randomUUID();
        Transaction tx = new Transaction(id, 1000);
        tx.setAmountApproved(1000);
        tx.setCaptureAmount(600);
        tx.updateState(TransactionState.CAPTURING);

        TransactionRepositoryImpl repository = repository();
        repository.save(tx);

        FakeGateway gateway = new FakeGateway();
        gateway.captures.add(new TerminalResult(State.DECLINED, ErrorCode.ALREADY_CAPTURED));

        TransactionEngine engine = engine(gateway, repository);
        engine.recoverInFlight();

        assertEquals(TransactionState.CAPTURED, repository.getTransaction(id).getState());
        assertEquals(1, gateway.captureCalls);
    }

    // Restarting with CANCELLING on disk retries cancel until the terminal confirms the transaction is resolved.
    @Test
    public void recoveryCancellingRetriesCancel() throws Exception {
        UUID id = UUID.randomUUID();
        Transaction tx = new Transaction(id, 1000);
        tx.updateState(TransactionState.CANCELLING);

        TransactionRepositoryImpl repository = repository();
        repository.save(tx);

        FakeGateway gateway = new FakeGateway();
        gateway.cancels.add(new TerminalResult(State.APPROVED, null));

        TransactionEngine engine = engine(gateway, repository);
        engine.recoverInFlight();

        assertEquals(TransactionState.CANCELLED, repository.getTransaction(id).getState());
        assertEquals(1, gateway.cancelCalls);
    }

    // Recovery must leave terminal states untouched and avoid extra terminal calls.
    @Test
    public void recoveryLeavesTerminalStatesUntouched() throws Exception {
        TransactionRepositoryImpl repository = repository();
        Transaction captured = saveTransaction(repository, TransactionState.CAPTURED);
        Transaction cancelled = saveTransaction(repository, TransactionState.CANCELLED);
        Transaction declined = saveTransaction(repository, TransactionState.DECLINED);
        Transaction captureFailed = saveTransaction(repository, TransactionState.CAPTURE_FAILED);
        Transaction cancelFailed = saveTransaction(repository, TransactionState.CANCEL_FAILED);

        FakeGateway gateway = new FakeGateway();
        TransactionEngine engine = engine(gateway, repository);
        engine.recoverInFlight();

        assertEquals(TransactionState.CAPTURED, repository.getTransaction(captured.getId()).getState());
        assertEquals(TransactionState.CANCELLED, repository.getTransaction(cancelled.getId()).getState());
        assertEquals(TransactionState.DECLINED, repository.getTransaction(declined.getId()).getState());
        assertEquals(TransactionState.CAPTURE_FAILED, repository.getTransaction(captureFailed.getId()).getState());
        assertEquals(TransactionState.CANCEL_FAILED, repository.getTransaction(cancelFailed.getId()).getState());
        assertEquals(0, gateway.authorizeCalls);
        assertEquals(0, gateway.captureCalls);
        assertEquals(0, gateway.cancelCalls);
    }

    // The persistent journal is capped, not just the UI list, so only the newest 50 survive reload.
    @Test
    public void journalKeepsOnlyNewestFiftyTransactions() throws Exception {
        TransactionRepositoryImpl repository = repository();

        for (int i = 0; i < 55; i++) {
            repository.save(new Transaction(UUID.randomUUID(), i + 1));
        }

        assertEquals(50, repository.getAll().size());

        TransactionRepositoryImpl restored = new TransactionRepositoryImpl(journalFile());
        assertEquals(50, restored.getAll().size());
    }

    private TransactionEngine engine(FakeGateway gateway, TransactionRepositoryImpl repository) {
        return new TransactionEngine(
                gateway,
                repository,
                new RetryPolicy(3, 0L),
                new ImmediateScheduler()
        );
    }

    private Transaction saveTransaction(TransactionRepositoryImpl repository,
                                        TransactionState state) {
        return saveTransaction(repository, state, 500);
    }

    private Transaction saveTransaction(TransactionRepositoryImpl repository,
                                        TransactionState state,
                                        long captureAmount) {
        Transaction tx = new Transaction(UUID.randomUUID(), 1000);
        if (state == TransactionState.AUTHORIZED
                || state == TransactionState.CAPTURING
                || state == TransactionState.CAPTURED
                || state == TransactionState.CAPTURE_FAILED) {
            tx.setAmountApproved(1000);
        }
        if (state == TransactionState.CAPTURING
                || state == TransactionState.CAPTURED
                || state == TransactionState.CAPTURE_FAILED) {
            tx.setCaptureAmount(captureAmount);
        }
        if (state != TransactionState.AUTHORIZING) {
            tx.updateState(state, failedStateError(state));
        }
        repository.save(tx);
        return tx;
    }

    private String failedStateError(TransactionState state) {
        if (state == TransactionState.CAPTURE_FAILED || state == TransactionState.CANCEL_FAILED) {
            return ErrorCode.NETWORK_ERROR.name();
        }
        return null;
    }

    private TransactionRepositoryImpl repository() throws Exception {
        return new TransactionRepositoryImpl(journalFile());
    }

    private File journalFile() throws Exception {
        return new File(temporaryFolder.getRoot(), "transactions.properties");
    }

    private static class ImmediateScheduler implements RetryScheduler {
        @Override
        public void schedule(Runnable runnable, long delayMillis) {
            runnable.run();
        }
    }

    private static class FakeGateway implements PaymentTerminalGateway {
        final Queue<AuthorizationResult> authorizations = new ArrayDeque<>();
        final Queue<TerminalResult> captures = new ArrayDeque<>();
        final Queue<TerminalResult> cancels = new ArrayDeque<>();

        int authorizeCalls;
        int captureCalls;
        int cancelCalls;

        @Override
        public Single<AuthorizationResult> authorize(UUID txnId, long amountCents) {
            authorizeCalls++;
            return Single.just(authorizations.remove());
        }

        @Override
        public Single<TerminalResult> capture(UUID txnId, long amountCents) {
            captureCalls++;
            return Single.just(captures.remove());
        }

        @Override
        public Single<TerminalResult> cancel(UUID txnId) {
            cancelCalls++;
            return Single.just(cancels.remove());
        }
    }

    private static class RacingGateway implements PaymentTerminalGateway {
        final SingleSubject<AuthorizationResult> authorization = SingleSubject.create();
        final AtomicInteger authorizeCalls = new AtomicInteger();
        final AtomicInteger cancelCalls = new AtomicInteger();

        @Override
        public Single<AuthorizationResult> authorize(UUID txnId, long amountCents) {
            authorizeCalls.incrementAndGet();
            return authorization;
        }

        @Override
        public Single<TerminalResult> capture(UUID txnId, long amountCents) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Single<TerminalResult> cancel(UUID txnId) {
            cancelCalls.incrementAndGet();
            return Single.just(new TerminalResult(State.APPROVED, null));
        }
    }

    private static class RacingRepository implements TransactionRepository {
        private final Object lock = new Object();
        private final Map<UUID, Transaction> transactions = new HashMap<>();
        private final MutableLiveData<List<Transaction>> liveData = new MutableLiveData<>();

        private UUID racingId;
        private CountDownLatch racingReads;

        void raceReadsFor(UUID id) {
            racingId = id;
            racingReads = new CountDownLatch(2);
        }

        @Override
        public LiveData<List<Transaction>> observeTransactions() {
            return liveData;
        }

        @Override
        public void save(Transaction transaction) {
            update(transaction);
        }

        @Override
        public Transaction getTransaction(UUID id) {
            Transaction snapshot;
            synchronized (lock) {
                Transaction transaction = transactions.get(id);
                snapshot = transaction == null ? null : transaction.copy();
            }

            if (snapshot != null
                    && id.equals(racingId)
                    && snapshot.getState() == TransactionState.AUTHORIZING) {
                racingReads.countDown();
                try {
                    racingReads.await(200, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }

            return snapshot;
        }

        @Override
        public List<Transaction> getAll() {
            synchronized (lock) {
                List<Transaction> snapshot = new ArrayList<>();
                for (Transaction transaction : transactions.values()) {
                    snapshot.add(transaction.copy());
                }
                return snapshot;
            }
        }

        @Override
        public void update(Transaction transaction) {
            synchronized (lock) {
                transactions.put(transaction.getId(), transaction.copy());
            }
            liveData.postValue(getAll());
        }
    }
}
