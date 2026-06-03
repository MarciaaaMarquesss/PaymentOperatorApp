package com.marcia.paymentoperator.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.marcia.paymentoperator.domain.model.Transaction;
import com.marcia.paymentoperator.domain.model.TransactionState;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class TransactionRepositoryImpl implements TransactionRepository {

    private static final int MAX_TRANSACTIONS = 50;
    private static final int DEFAULT_INT_VALUE = 0;
    private static final int OLDEST_TRANSACTION_INDEX = 0;
    private static final long DEFAULT_LONG_VALUE = 0L;
    private static final String EMPTY_VALUE = "";
    private static final String TMP_SUFFIX = ".tmp";
    private static final String JOURNAL_COMMENT = "payment-operator-journal";
    private static final String TRANSACTION_PREFIX = "tx.";
    private static final String PROPERTY_SEPARATOR = ".";
    private static final String PROPERTY_COUNT = "count";
    private static final String PROPERTY_ID = "id";
    private static final String PROPERTY_AMOUNT_REQUESTED = "amountRequested";
    private static final String PROPERTY_CREATED_AT = "createdAt";
    private static final String PROPERTY_AMOUNT_APPROVED = "amountApproved";
    private static final String PROPERTY_CAPTURE_AMOUNT = "captureAmount";
    private static final String PROPERTY_STATE = "state";
    private static final String PROPERTY_RETRY_COUNT = "retryCount";
    private static final String PROPERTY_LAST_ERROR = "lastError";
    private static final String PROPERTY_LAST_UPDATED = "lastUpdated";
    private static final String PROPERTY_HISTORY_COUNT = "history.count";
    private static final String PROPERTY_HISTORY_PREFIX = "history.";
    private static final String ERROR_READ_JOURNAL = "Could not read transaction journal";
    private static final String ERROR_CREATE_DIRECTORY = "Could not create journal directory";
    private static final String ERROR_WRITE_JOURNAL = "Could not write transaction journal";
    private static final String ERROR_REPLACE_JOURNAL = "Could not replace transaction journal";
    private static final String ERROR_COMMIT_JOURNAL = "Could not commit transaction journal";

    private final File journalFile;
    private final Map<UUID, Transaction> transactions = new HashMap<>();
    private final MutableLiveData<List<Transaction>> transactionLiveData = new MutableLiveData<>();

    public TransactionRepositoryImpl(File journalFile) {
        this.journalFile = journalFile;
        loadFromDisk();
        publish();
    }

    @Override
    public synchronized LiveData<List<Transaction>> observeTransactions() {
        return transactionLiveData;
    }

    @Override
    public synchronized void save(Transaction transaction) {
        transactions.put(transaction.getId(), transaction.copy());
        trimToLimit();
        writeToDisk();
        publish();
    }

    @Override
    public synchronized Transaction getTransaction(UUID id) {
        Transaction transaction = transactions.get(id);
        return transaction == null ? null : transaction.copy();
    }

    @Override
    public synchronized List<Transaction> getAll() {
        return snapshotNewestFirst();
    }

    @Override
    public synchronized void update(Transaction transaction) {
        transactions.put(transaction.getId(), transaction.copy());
        trimToLimit();
        writeToDisk();
        publish();
    }

    private void loadFromDisk() {
        if (!journalFile.exists()) {
            return;
        }

        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(journalFile)) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException(ERROR_READ_JOURNAL, e);
        }

        int count = intProperty(properties, PROPERTY_COUNT, DEFAULT_INT_VALUE);
        for (int i = 0; i < count; i++) {
            String prefix = transactionPrefix(i);
            String id = properties.getProperty(prefix + PROPERTY_ID);
            if (id == null) {
                continue;
            }

            List<String> history = new ArrayList<>();
            int historyCount = intProperty(properties, prefix + PROPERTY_HISTORY_COUNT, DEFAULT_INT_VALUE);
            for (int j = 0; j < historyCount; j++) {
                String event = properties.getProperty(prefix + PROPERTY_HISTORY_PREFIX + j);
                if (event != null) {
                    history.add(event);
                }
            }

            Transaction transaction = Transaction.restore(
                    UUID.fromString(id),
                    longProperty(properties, prefix + PROPERTY_AMOUNT_REQUESTED, DEFAULT_LONG_VALUE),
                    longProperty(properties, prefix + PROPERTY_CREATED_AT, DEFAULT_LONG_VALUE),
                    longProperty(properties, prefix + PROPERTY_AMOUNT_APPROVED, DEFAULT_LONG_VALUE),
                    longProperty(properties, prefix + PROPERTY_CAPTURE_AMOUNT, DEFAULT_LONG_VALUE),
                    TransactionState.valueOf(properties.getProperty(prefix + PROPERTY_STATE)),
                    intProperty(properties, prefix + PROPERTY_RETRY_COUNT, DEFAULT_INT_VALUE),
                    emptyToNull(properties.getProperty(prefix + PROPERTY_LAST_ERROR)),
                    longProperty(properties, prefix + PROPERTY_LAST_UPDATED, DEFAULT_LONG_VALUE),
                    history
            );
            transactions.put(transaction.getId(), transaction);
        }

        trimToLimit();
    }

    private void writeToDisk() {
        File parent = journalFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException(ERROR_CREATE_DIRECTORY);
        }

        Properties properties = new Properties();
        List<Transaction> snapshot = snapshotNewestFirst();
        properties.setProperty(PROPERTY_COUNT, String.valueOf(snapshot.size()));

        for (int i = 0; i < snapshot.size(); i++) {
            Transaction transaction = snapshot.get(i);
            String prefix = transactionPrefix(i);

            properties.setProperty(prefix + PROPERTY_ID, transaction.getId().toString());
            properties.setProperty(prefix + PROPERTY_AMOUNT_REQUESTED, String.valueOf(transaction.getAmountRequested()));
            properties.setProperty(prefix + PROPERTY_CREATED_AT, String.valueOf(transaction.getCreatedAt()));
            properties.setProperty(prefix + PROPERTY_AMOUNT_APPROVED, String.valueOf(transaction.getAmountApproved()));
            properties.setProperty(prefix + PROPERTY_CAPTURE_AMOUNT, String.valueOf(transaction.getCaptureAmount()));
            properties.setProperty(prefix + PROPERTY_STATE, transaction.getState().name());
            properties.setProperty(prefix + PROPERTY_RETRY_COUNT, String.valueOf(transaction.getRetryCount()));
            properties.setProperty(prefix + PROPERTY_LAST_ERROR, nullToEmpty(transaction.getLastError()));
            properties.setProperty(prefix + PROPERTY_LAST_UPDATED, String.valueOf(transaction.getLastUpdated()));

            List<String> history = transaction.getHistory();
            properties.setProperty(prefix + PROPERTY_HISTORY_COUNT, String.valueOf(history.size()));
            for (int j = 0; j < history.size(); j++) {
                properties.setProperty(prefix + PROPERTY_HISTORY_PREFIX + j, history.get(j));
            }
        }

        File tmpFile = parent == null
                ? new File(journalFile.getName() + TMP_SUFFIX)
                : new File(parent, journalFile.getName() + TMP_SUFFIX);
        try (FileOutputStream outputStream = new FileOutputStream(tmpFile)) {
            properties.store(outputStream, JOURNAL_COMMENT);
        } catch (IOException e) {
            throw new IllegalStateException(ERROR_WRITE_JOURNAL, e);
        }

        if (journalFile.exists() && !journalFile.delete()) {
            throw new IllegalStateException(ERROR_REPLACE_JOURNAL);
        }
        if (!tmpFile.renameTo(journalFile)) {
            throw new IllegalStateException(ERROR_COMMIT_JOURNAL);
        }
    }

    private void trimToLimit() {
        List<Transaction> oldestFirst = new ArrayList<>(transactions.values());
        Collections.sort(oldestFirst, new Comparator<Transaction>() {
            @Override
            public int compare(Transaction left, Transaction right) {
                return Long.compare(left.getCreatedAt(), right.getCreatedAt());
            }
        });

        while (oldestFirst.size() > MAX_TRANSACTIONS) {
            Transaction oldest = oldestFirst.remove(OLDEST_TRANSACTION_INDEX);
            transactions.remove(oldest.getId());
        }
    }

    private List<Transaction> snapshotNewestFirst() {
        List<Transaction> snapshot = new ArrayList<>();
        for (Transaction transaction : transactions.values()) {
            snapshot.add(transaction.copy());
        }
        Collections.sort(snapshot, new Comparator<Transaction>() {
            @Override
            public int compare(Transaction left, Transaction right) {
                return Long.compare(right.getCreatedAt(), left.getCreatedAt());
            }
        });
        return snapshot;
    }

    private void publish() {
        transactionLiveData.postValue(snapshotNewestFirst());
    }

    private static String transactionPrefix(int index) {
        return TRANSACTION_PREFIX + index + PROPERTY_SEPARATOR;
    }

    private static int intProperty(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static long longProperty(Properties properties, String key, long fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Long.parseLong(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? EMPTY_VALUE : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
