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
            throw new IllegalStateException("Could not read transaction journal", e);
        }

        int count = intProperty(properties, "count", 0);
        for (int i = 0; i < count; i++) {
            String prefix = "tx." + i + ".";
            String id = properties.getProperty(prefix + "id");
            if (id == null) {
                continue;
            }

            List<String> history = new ArrayList<>();
            int historyCount = intProperty(properties, prefix + "history.count", 0);
            for (int j = 0; j < historyCount; j++) {
                String event = properties.getProperty(prefix + "history." + j);
                if (event != null) {
                    history.add(event);
                }
            }

            Transaction transaction = Transaction.restore(
                    UUID.fromString(id),
                    longProperty(properties, prefix + "amountRequested", 0L),
                    longProperty(properties, prefix + "createdAt", 0L),
                    longProperty(properties, prefix + "amountApproved", 0L),
                    longProperty(properties, prefix + "captureAmount", 0L),
                    TransactionState.valueOf(properties.getProperty(prefix + "state")),
                    intProperty(properties, prefix + "retryCount", 0),
                    emptyToNull(properties.getProperty(prefix + "lastError")),
                    longProperty(properties, prefix + "lastUpdated", 0L),
                    history
            );
            transactions.put(transaction.getId(), transaction);
        }

        trimToLimit();
    }

    private void writeToDisk() {
        File parent = journalFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create journal directory");
        }

        Properties properties = new Properties();
        List<Transaction> snapshot = snapshotNewestFirst();
        properties.setProperty("count", String.valueOf(snapshot.size()));

        for (int i = 0; i < snapshot.size(); i++) {
            Transaction transaction = snapshot.get(i);
            String prefix = "tx." + i + ".";

            properties.setProperty(prefix + "id", transaction.getId().toString());
            properties.setProperty(prefix + "amountRequested", String.valueOf(transaction.getAmountRequested()));
            properties.setProperty(prefix + "createdAt", String.valueOf(transaction.getCreatedAt()));
            properties.setProperty(prefix + "amountApproved", String.valueOf(transaction.getAmountApproved()));
            properties.setProperty(prefix + "captureAmount", String.valueOf(transaction.getCaptureAmount()));
            properties.setProperty(prefix + "state", transaction.getState().name());
            properties.setProperty(prefix + "retryCount", String.valueOf(transaction.getRetryCount()));
            properties.setProperty(prefix + "lastError", nullToEmpty(transaction.getLastError()));
            properties.setProperty(prefix + "lastUpdated", String.valueOf(transaction.getLastUpdated()));

            List<String> history = transaction.getHistory();
            properties.setProperty(prefix + "history.count", String.valueOf(history.size()));
            for (int j = 0; j < history.size(); j++) {
                properties.setProperty(prefix + "history." + j, history.get(j));
            }
        }

        File tmpFile = parent == null
                ? new File(journalFile.getName() + ".tmp")
                : new File(parent, journalFile.getName() + ".tmp");
        try (FileOutputStream outputStream = new FileOutputStream(tmpFile)) {
            properties.store(outputStream, "payment-operator-journal");
        } catch (IOException e) {
            throw new IllegalStateException("Could not write transaction journal", e);
        }

        if (journalFile.exists() && !journalFile.delete()) {
            throw new IllegalStateException("Could not replace transaction journal");
        }
        if (!tmpFile.renameTo(journalFile)) {
            throw new IllegalStateException("Could not commit transaction journal");
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
            Transaction oldest = oldestFirst.remove(0);
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

    private static int intProperty(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static long longProperty(Properties properties, String key, long fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Long.parseLong(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.length() == 0 ? null : value;
    }
}
