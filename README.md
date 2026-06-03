# The Payment Operator App

Android app for the Elecctro senior Android challenge. The app keeps one fused transaction state machine per payment, stores every state change in a bounded file journal before calling the terminal SDK, and resumes in-flight work on launch by interrogating the terminal with safe capture/cancel retries instead of retrying authorization. `MainActivity` hosts three fragments for authorization, history, and transaction detail actions.

## Build

Use JDK 17 to run Gradle. The app source itself is compiled with Java 8 compatibility.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebug
```

## Run

Open the project in Android Studio, select JDK 17 as the Gradle JDK, sync, and run the `app` configuration on an emulator or device. `PaymentOperatorFactory` creates the terminal in `REAL` mode.

## Demo Recording

The demo recording is available at https://youtu.be/LRlAz0sKqH8.

## Libraries

- `payment-terminal.aar`: challenge SDK.
- AndroidX AppCompat/Activity/ConstraintLayout/Material: standard Android UI and fragment hosting support.
- RxJava 2: async terminal boundary used by the domain engine.
- AndroidX Lifecycle: `ViewModel` for the activity boundary and `LiveData` for UI history updates.
- AndroidX core-testing: JVM tests for LiveData-backed state.

## Known Issues / If I Had More Time

- The UI is intentionally simple and fragment-based. It is functional for the challenge flows, but I would improve visual hierarchy and accessibility for production.
- Backoff is process-resumed by retrying in-flight states on launch, not by persisting a future wake-up timestamp. That is acceptable for this app because no work should happen while the process is dead, but I would revisit it for a background service.
- The journal is a compact properties file rather than Room. It keeps the persistence boundary easy to test and reason about for the challenge, but Room would be worth revisiting if queries grew beyond loading the latest 50 transactions.

## AI Assistance

I used ChatGPT/Codex to help inspect the brief, configure Gradle, and prepare the demo recording outline. I reviewed and kept the code in this repository as the source of truth.
