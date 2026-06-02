# ADR-001: Persisted Transaction Journal Before Terminal Calls

## Context

The terminal SDK is non-idempotent for authorization, returns asynchronous `LiveData`, processes one call at a time, and forgets all transaction memory when the app process dies. A timed-out authorization is especially dangerous because the app cannot know whether the processor charged the customer. The challenge therefore depends on one load-bearing property: the app must persist each transaction transition before issuing the SDK call that advances it, and after restart it must recover from disk without ever sending a duplicate authorization.

## Decision

The app owns a single transaction aggregate with states such as `AUTHORIZING`, `AUTHORIZED`, `CAPTURING`, `CANCELLING`, and terminal states. `MainActivity` stays at the UI boundary and delegates payment work to `PaymentOperatorViewModel`, which owns the engine for the screen. `TransactionEngine` is the only class that advances this state machine. It serializes transitions so competing UI actions, recovery, and terminal callbacks cannot start duplicate terminal chains for the same transaction. It writes the next state to `TransactionRepository` before calling the terminal gateway. The repository stores the newest 50 transactions in a file-backed journal and publishes `LiveData` only after the write succeeds. The terminal is hidden behind `PaymentTerminalGateway`, which converts one-shot SDK `LiveData` responses into RxJava `Single`s and removes each `observeForever` observer after emission or cancellation.

Recovery is driven from the journal. `AUTHORIZING` never calls `authorize` again; it moves to `CANCELLING` and calls `cancel`. `CAPTURING` retries `capture`, and `CANCELLING` retries `cancel`. Timed-out capture and cancel calls retry with bounded exponential backoff, then land in `CAPTURE_FAILED` or `CANCEL_FAILED` with the last error surfaced for operator retry.

## Alternatives Considered

Room database: Room would give stronger schema tooling and easier future queries, but for this challenge it adds Android-specific test setup while the app only needs to atomically load and store the latest 50 aggregates.

Independent authorization/capture/cancel records: Separate records look simple at first, but they make recovery depend on cross-record consistency and invite duplicate or contradictory operations for the same transaction id.

Retrying authorization after timeout: This would be the smallest implementation, but it violates the payment safety requirement because the SDK explicitly treats repeated authorization calls as new charges.

## Consequences

The no-double-charge rule is easy to audit because all terminal calls flow through one engine and the journal is updated first. JVM tests can pre-seed disk state, create a fresh engine and fake gateway, and prove recovery behavior without an emulator. The trade-off is that the repository currently rewrites a small snapshot file instead of using database transactions; that is acceptable with the 50-transaction cap, but I would re-evaluate it if the history size, query surface, or concurrent writers grew.
