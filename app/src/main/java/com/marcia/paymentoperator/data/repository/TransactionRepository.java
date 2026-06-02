package com.marcia.paymentoperator.data.repository;

import androidx.lifecycle.LiveData;

import com.marcia.paymentoperator.domain.model.Transaction;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository {

    LiveData<List<Transaction>> observeTransactions();

    void save(Transaction transaction);

    Transaction getTransaction(UUID id);

    List<Transaction> getAll();

    void update(Transaction transaction);
}
