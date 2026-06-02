package com.marcia.paymentoperator.domain.model;

public enum TransactionState {
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
