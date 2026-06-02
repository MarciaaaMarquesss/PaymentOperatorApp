package com.marcia.paymentoperator.domain.model;

public enum TransactionState {
    NEW,
    AUTHORIZING,
    AUTHORIZED,
    CAPTURING,
    CAPTURED,
    CAPTURE_FAILED,
    CANCELLING,
    CANCELLED,
    CANCEL_FAILED,
    DECLINED
}
