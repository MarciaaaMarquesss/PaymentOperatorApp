package com.marcia.paymentoperator.data.local.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class TransactionEntity {
    @PrimaryKey
    @NonNull
    public String id;

    public long amount;
    public String state;
    public long approvedAmount;
    public long lastUpdated;
}
