package com.marcia.paymentoperator.ui;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.marcia.paymentoperator.R;
import com.marcia.paymentoperator.domain.model.Transaction;
import com.marcia.paymentoperator.domain.model.TransactionState;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int FIRST_TRANSACTION_INDEX = 0;
    private static final int SHORT_TRANSACTION_ID_LENGTH = 8;
    private static final long MIN_AMOUNT_CENTS = 1L;
    private static final long NO_APPROVED_AMOUNT = 0L;
    private static final char LINE_BREAK = '\n';

    private PaymentOperatorViewModel viewModel;
    private Transaction selectedTransaction;

    private LinearLayout historyList;
    private EditText inputAmount;
    private EditText inputCaptureAmount;
    private TextView txtDetail;
    private Button btnCapture;
    private Button btnCancel;
    private Button btnRetry;

    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputAmount = findViewById(R.id.inputAmount);
        inputCaptureAmount = findViewById(R.id.inputCaptureAmount);
        historyList = findViewById(R.id.historyList);
        txtDetail = findViewById(R.id.txtDetail);
        txtDetail.setMovementMethod(new ScrollingMovementMethod());

        Button btnAuthorize = findViewById(R.id.btnAuthorize);
        btnCapture = findViewById(R.id.btnCapture);
        btnCancel = findViewById(R.id.btnCancel);
        btnRetry = findViewById(R.id.btnRetry);

        viewModel = new ViewModelProvider(this).get(PaymentOperatorViewModel.class);

        viewModel.observeTransactions().observe(this, this::renderHistory);

        btnAuthorize.setOnClickListener(v -> authorize());
        btnCapture.setOnClickListener(v -> capture());
        btnCancel.setOnClickListener(v -> cancel());
        btnRetry.setOnClickListener(v -> retry());

        updateActions();
    }

    private void authorize() {
        Long amount = readAmount(inputAmount);
        if (amount == null || amount < MIN_AMOUNT_CENTS) {
            showMessage(R.string.toast_enter_amount);
            return;
        }
        selectedTransaction = viewModel.authorize(amount);
        inputAmount.getText().clear();
        renderDetail(selectedTransaction);
    }

    private void capture() {
        if (selectedTransaction == null) {
            return;
        }

        Long amount = readAmount(inputCaptureAmount);
        if (amount == null) {
            showMessage(R.string.toast_enter_capture_amount);
            return;
        }

        boolean accepted = viewModel.capture(selectedTransaction, amount);
        if (!accepted) {
            showMessage(R.string.toast_capture_unavailable);
        }
    }

    private void cancel() {
        if (selectedTransaction == null) {
            return;
        }
        boolean accepted = viewModel.cancel(selectedTransaction);
        if (!accepted) {
            showMessage(R.string.toast_cancel_unavailable);
        }
    }

    private void retry() {
        if (selectedTransaction == null) {
            return;
        }
        boolean accepted = viewModel.retry(selectedTransaction);
        if (!accepted) {
            showMessage(R.string.toast_retry_unavailable);
        }
    }

    private void renderHistory(List<Transaction> transactions) {
        historyList.removeAllViews();

        if (transactions.isEmpty()) {
            selectedTransaction = null;
            txtDetail.setText(R.string.text_no_transactions);
            updateActions();
            return;
        }

        if (selectedTransaction == null || findSelected(transactions) == null) {
            selectedTransaction = transactions.get(FIRST_TRANSACTION_INDEX);
        } else {
            selectedTransaction = findSelected(transactions);
        }

        for (Transaction tx : transactions) {
            Button row = new Button(this);
            row.setAllCaps(false);
            row.setText(rowText(tx));
            row.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            row.setOnClickListener(v -> {
                selectedTransaction = tx;
                renderDetail(tx);
            });
            historyList.addView(row);
        }

        renderDetail(selectedTransaction);
    }

    private Transaction findSelected(List<Transaction> transactions) {
        if (selectedTransaction == null) {
            return null;
        }
        for (Transaction tx : transactions) {
            if (tx.getId().equals(selectedTransaction.getId())) {
                return tx;
            }
        }
        return null;
    }

    private void renderDetail(Transaction tx) {
        if (tx == null) {
            txtDetail.setText(R.string.text_select_transaction);
            updateActions();
            return;
        }

        String lastError = tx.getLastError() == null
                ? getString(R.string.detail_empty_value)
                : tx.getLastError();
        String updatedAt = dateFormat.format(new Date(tx.getLastUpdated()));
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.detail_id, tx.getId()));
        builder.append(getString(R.string.detail_requested, tx.getAmountRequested()));
        builder.append(getString(R.string.detail_approved, tx.getAmountApproved()));
        builder.append(getString(R.string.detail_capture_amount, tx.getCaptureAmount()));
        builder.append(getString(R.string.detail_state, stateLabel(tx)));
        builder.append(getString(R.string.detail_last_error, lastError));
        builder.append(getString(R.string.detail_updated, updatedAt));

        for (String event : tx.getHistory()) {
            builder.append(event).append(LINE_BREAK);
        }

        txtDetail.setText(builder.toString());
        if (tx.getAmountApproved() > NO_APPROVED_AMOUNT) {
            inputCaptureAmount.setText(String.valueOf(tx.getAmountApproved()));
        } else {
            inputCaptureAmount.getText().clear();
        }
        updateActions();
    }

    private void updateActions() {
        TransactionState state = selectedTransaction == null ? null : selectedTransaction.getState();
        boolean canCapture = state == TransactionState.AUTHORIZED;
        boolean canCancel = state == TransactionState.AUTHORIZED || state == TransactionState.AUTHORIZING;
        boolean canRetry = state == TransactionState.CAPTURE_FAILED || state == TransactionState.CANCEL_FAILED;

        btnCapture.setEnabled(canCapture);
        btnCancel.setEnabled(canCancel);
        btnRetry.setEnabled(canRetry);
        inputCaptureAmount.setEnabled(canCapture);
    }

    private String rowText(Transaction tx) {
        return getString(
                R.string.transaction_row,
                shortId(tx),
                tx.getAmountRequested(),
                stateLabel(tx),
                dateFormat.format(new Date(tx.getLastUpdated()))
        );
    }

    private String stateLabel(Transaction tx) {
        if (tx.getState() == TransactionState.CAPTURING || tx.getState() == TransactionState.CANCELLING) {
            return getString(R.string.state_retry_count, tx.getState().name(), tx.getRetryCount());
        }
        return tx.getState().name();
    }

    private String shortId(Transaction tx) {
        String value = tx.getId().toString();
        return value.substring(FIRST_TRANSACTION_INDEX, SHORT_TRANSACTION_ID_LENGTH);
    }

    private Long readAmount(EditText editText) {
        String value = editText.getText().toString().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void showMessage(int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
    }
}
