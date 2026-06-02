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
        if (amount == null || amount <= 0) {
            showMessage("Enter an amount greater than zero");
            return;
        }
        selectedTransaction = viewModel.authorize(amount);
        inputAmount.setText("");
        renderDetail(selectedTransaction);
    }

    private void capture() {
        if (selectedTransaction == null) {
            return;
        }

        Long amount = readAmount(inputCaptureAmount);
        if (amount == null) {
            showMessage("Enter a capture amount");
            return;
        }

        boolean accepted = viewModel.capture(selectedTransaction, amount);
        if (!accepted) {
            showMessage("Capture is not available for that amount");
        }
    }

    private void cancel() {
        if (selectedTransaction == null) {
            return;
        }
        boolean accepted = viewModel.cancel(selectedTransaction);
        if (!accepted) {
            showMessage("Cancel is not available");
        }
    }

    private void retry() {
        if (selectedTransaction == null) {
            return;
        }
        boolean accepted = viewModel.retry(selectedTransaction);
        if (!accepted) {
            showMessage("Retry is not available");
        }
    }

    private void renderHistory(List<Transaction> transactions) {
        historyList.removeAllViews();

        if (transactions.isEmpty()) {
            selectedTransaction = null;
            txtDetail.setText("No transactions yet");
            updateActions();
            return;
        }

        if (selectedTransaction == null || findSelected(transactions) == null) {
            selectedTransaction = transactions.get(0);
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
            txtDetail.setText("Select a transaction");
            updateActions();
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("ID: ").append(tx.getId()).append('\n');
        builder.append("Requested: ").append(tx.getAmountRequested()).append(" cents\n");
        builder.append("Approved: ").append(tx.getAmountApproved()).append(" cents\n");
        builder.append("Capture amount: ").append(tx.getCaptureAmount()).append(" cents\n");
        builder.append("State: ").append(stateLabel(tx)).append('\n');
        builder.append("Last error: ").append(tx.getLastError() == null ? "-" : tx.getLastError()).append('\n');
        builder.append("Updated: ").append(dateFormat.format(new Date(tx.getLastUpdated()))).append("\n\n");

        for (String event : tx.getHistory()) {
            builder.append(event).append('\n');
        }

        txtDetail.setText(builder.toString());
        inputCaptureAmount.setText(tx.getAmountApproved() > 0 ? String.valueOf(tx.getAmountApproved()) : "");
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
        return shortId(tx)
                + " | " + tx.getAmountRequested() + " cents"
                + " | " + stateLabel(tx)
                + "\nUpdated " + dateFormat.format(new Date(tx.getLastUpdated()));
    }

    private String stateLabel(Transaction tx) {
        if (tx.getState() == TransactionState.CAPTURING || tx.getState() == TransactionState.CANCELLING) {
            return tx.getState().name() + " (#" + tx.getRetryCount() + ")";
        }
        return tx.getState().name();
    }

    private String shortId(Transaction tx) {
        String value = tx.getId().toString();
        return value.substring(0, 8);
    }

    private Long readAmount(EditText editText) {
        String value = editText.getText().toString().trim();
        if (value.length() == 0) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
