package com.marcia.paymentoperator.domain.engine;

import androidx.lifecycle.LiveData;

import com.elecctro.recruitment.paymentterminal.AuthorizationResult;
import com.elecctro.recruitment.paymentterminal.ErrorCode;
import com.elecctro.recruitment.paymentterminal.State;
import com.marcia.paymentoperator.data.gateway.PaymentTerminalGateway;
import com.marcia.paymentoperator.data.repository.TransactionRepository;
import com.marcia.paymentoperator.domain.model.Transaction;
import com.marcia.paymentoperator.domain.model.TransactionState;

import java.util.UUID;

public class TransactionEngine {

    private final PaymentTerminalGateway gateway;
    private final TransactionRepository repository;

    public TransactionEngine(PaymentTerminalGateway gateway, TransactionRepository repository) {
        this.gateway = gateway;
        this.repository = repository;
    }

    public Transaction authorize(long amount) {
        UUID id = UUID.randomUUID();
        Transaction tx = new Transaction(id, amount);
        repository.save(tx);
        gateway.authorize(id, amount).observeForever(result -> {
            switch (result.state) {
                case APPROVED:
                    tx.setAmountApproved(result.approvedAmount);
                    tx.updateState(TransactionState.AUTHORIZED);
                    repository.update(tx);
                    break;

                case DECLINED:
                    tx.updateState(TransactionState.DECLINED);
                    repository.update(tx);
                    break;

                case TIMED_OUT:

                    tx.updateState(TransactionState.CANCELLING);
                    repository.update(tx);
                    cancel(tx.getId());

                    break;
            }
        });
        return tx;
    }

    public void capture(UUID txnId, long amount) {}

    public void cancel(UUID txnId) {
        Transaction tx = repository.getTransaction(txnId);

        if (tx == null) {
            return;
        }

        if (tx.getState() != TransactionState.AUTHORIZED
                && tx.getState() != TransactionState.AUTHORIZING) {
            return;
        }

        tx.updateState(TransactionState.CANCELLING);
        repository.update(tx);

        gateway.cancel(txnId)
                .observeForever(result -> {
                    switch (result.state) {
                        case APPROVED:
                            tx.updateState(TransactionState.CANCELLED);
                            break;
                        case DECLINED:
                            if (result.error == ErrorCode.UNKNOWN_TRANSACTION_ID
                                    || result.error == ErrorCode.ALREADY_CANCELLED) {

                                tx.updateState(TransactionState.CANCELLED);
                            } else if(result.error == ErrorCode.ALREADY_CAPTURED) {
                                tx.updateState(TransactionState.CANCEL_FAILED);
                            } else {
                                tx.updateState(TransactionState.CANCEL_FAILED);
                            }
                            break;
                        case TIMED_OUT:
                            tx.updateState(TransactionState.CANCEL_FAILED);
                            break;
                    }

                    repository.update(tx);

                });
    }
}
