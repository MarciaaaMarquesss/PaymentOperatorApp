package com.marcia.paymentoperator.data.gateway;

import androidx.lifecycle.LiveData;
import java.util.UUID;

import com.elecctro.recruitment.paymentterminal.AuthorizationResult;
import com.elecctro.recruitment.paymentterminal.TerminalResult;

public interface PaymentTerminalGateway {

    LiveData<AuthorizationResult> authorize(UUID txnId, long amountCents);

    LiveData<TerminalResult> capture(UUID txnId, long amountCents);

    LiveData<TerminalResult> cancel(UUID txnId);
}
