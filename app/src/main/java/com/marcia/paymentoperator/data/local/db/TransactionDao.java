package com.marcia.paymentoperator.data.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TransactionEntity tx);

    @Update
    void update(TransactionEntity tx);

    @Query("SELECT * FROM TransactionEntity")
    List<TransactionEntity> getAll();
}
