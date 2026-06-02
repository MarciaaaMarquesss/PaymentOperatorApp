package com.marcia.paymentoperator.data.gateway;

import java.util.UUID;

import com.elecctro.recruitment.paymentterminal.AuthorizationResult;
import com.elecctro.recruitment.paymentterminal.TerminalResult;

import io.reactivex.Single;

public interface PaymentTerminalGateway {

    Single<AuthorizationResult> authorize(UUID txnId, long amountCents);

    Single<TerminalResult> capture(UUID txnId, long amountCents);

    Single<TerminalResult> cancel(UUID txnId);
}
