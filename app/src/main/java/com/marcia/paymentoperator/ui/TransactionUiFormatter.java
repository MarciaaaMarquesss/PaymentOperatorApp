package com.marcia.paymentoperator.ui;

import android.content.res.Resources;

import com.marcia.paymentoperator.R;
import com.marcia.paymentoperator.domain.model.Transaction;
import com.marcia.paymentoperator.domain.model.TransactionState;

import java.text.DateFormat;
import java.util.Date;

final class TransactionUiFormatter {

    private static final int FIRST_TRANSACTION_INDEX = 0;
    private static final int SHORT_TRANSACTION_ID_LENGTH = 8;
    private static final char LINE_BREAK = '\n';

    private final Resources resources;
    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);

    TransactionUiFormatter(Resources resources) {
        this.resources = resources;
    }

    String authorizationResult(Transaction transaction) {
        String lastError = transaction.getLastError() == null
                ? resources.getString(R.string.detail_empty_value)
                : transaction.getLastError();
        return resources.getString(
                R.string.authorization_result,
                transaction.getId(),
                transaction.getAmountRequested(),
                transaction.getAmountApproved(),
                stateLabel(transaction),
                lastError,
                updatedAt(transaction)
        );
    }

    String detail(Transaction transaction) {
        String lastError = transaction.getLastError() == null
                ? resources.getString(R.string.detail_empty_value)
                : transaction.getLastError();
        StringBuilder builder = new StringBuilder();
        builder.append(resources.getString(R.string.detail_id, transaction.getId()));
        builder.append(resources.getString(R.string.detail_requested, transaction.getAmountRequested()));
        builder.append(resources.getString(R.string.detail_approved, transaction.getAmountApproved()));
        builder.append(resources.getString(R.string.detail_capture_amount, transaction.getCaptureAmount()));
        builder.append(resources.getString(R.string.detail_state, stateLabel(transaction)));
        builder.append(resources.getString(R.string.detail_last_error, lastError));
        builder.append(resources.getString(R.string.detail_updated, updatedAt(transaction)));

        for (String event : transaction.getHistory()) {
            builder.append(event).append(LINE_BREAK);
        }

        return builder.toString();
    }

    String row(Transaction transaction) {
        return resources.getString(
                R.string.transaction_row,
                shortId(transaction),
                transaction.getAmountRequested(),
                stateLabel(transaction),
                updatedAt(transaction)
        );
    }

    private String stateLabel(Transaction transaction) {
        if (transaction.getState() == TransactionState.CAPTURING
                || transaction.getState() == TransactionState.CANCELLING) {
            return resources.getString(
                    R.string.state_retry_count,
                    transaction.getState().name(),
                    transaction.getRetryCount()
            );
        }
        return transaction.getState().name();
    }

    private String shortId(Transaction transaction) {
        String value = transaction.getId().toString();
        return value.substring(FIRST_TRANSACTION_INDEX, SHORT_TRANSACTION_ID_LENGTH);
    }

    private String updatedAt(Transaction transaction) {
        return dateFormat.format(new Date(transaction.getLastUpdated()));
    }
}
