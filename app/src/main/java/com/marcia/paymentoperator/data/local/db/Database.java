package com.marcia.paymentoperator.data.local.db;

import androidx.room.RoomDatabase;

@androidx.room.Database(entities = {TransactionEntity.class}, version = 1, exportSchema = false)
public abstract class Database extends RoomDatabase {
    public abstract TransactionDao transactionDao();
}
