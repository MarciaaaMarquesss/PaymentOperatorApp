package com.marcia.paymentoperator.ui;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.marcia.paymentoperator.R;
import com.marcia.paymentoperator.domain.model.Transaction;
import com.marcia.paymentoperator.domain.model.TransactionState;

import java.util.List;
import java.util.UUID;

public class TransactionDetailFragment extends Fragment {

    private static final String ARG_TRANSACTION_ID = "transactionId";
    private static final long NO_APPROVED_AMOUNT = 0L;

    private PaymentOperatorViewModel viewModel;
    private TransactionUiFormatter formatter;
    private UUID transactionId;
    private Transaction currentTransaction;
    private EditText inputCaptureAmount;
    private TextView txtDetail;
    private Button btnCapture;
    private Button btnCancel;
    private Button btnRetry;

    public static TransactionDetailFragment newInstance(UUID transactionId) {
        TransactionDetailFragment fragment = new TransactionDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TRANSACTION_ID, transactionId.toString());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transaction_detail, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        String transactionIdValue = args.getString(ARG_TRANSACTION_ID);
        if (transactionIdValue == null) {
            return;
        }

        try {
            transactionId = UUID.fromString(transactionIdValue);
        } catch (IllegalArgumentException ignored) {
            transactionId = null;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        formatter = new TransactionUiFormatter(getResources());
        viewModel = new ViewModelProvider(requireActivity()).get(PaymentOperatorViewModel.class);

        txtDetail = view.findViewById(R.id.txtDetail);
        txtDetail.setMovementMethod(new ScrollingMovementMethod());
        inputCaptureAmount = view.findViewById(R.id.inputCaptureAmount);
        btnCapture = view.findViewById(R.id.btnCapture);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnRetry = view.findViewById(R.id.btnRetry);

        Button btnNewAuthorization = view.findViewById(R.id.btnNewAuthorization);
        Button btnOpenHistory = view.findViewById(R.id.btnOpenHistory);

        btnNewAuthorization.setOnClickListener(v -> mainActivity().showAuthorize());
        btnOpenHistory.setOnClickListener(v -> mainActivity().showHistory());
        btnCapture.setOnClickListener(v -> capture());
        btnCancel.setOnClickListener(v -> cancel());
        btnRetry.setOnClickListener(v -> retry());

        viewModel.observeTransactions().observe(getViewLifecycleOwner(), this::renderTransaction);
        updateActions();
    }

    private void renderTransaction(List<Transaction> transactions) {
        currentTransaction = findTransaction(transactions);
        if (currentTransaction == null) {
            txtDetail.setText(R.string.text_transaction_not_found);
            inputCaptureAmount.getText().clear();
            updateActions();
            return;
        }

        txtDetail.setText(formatter.detail(currentTransaction));
        if (currentTransaction.getAmountApproved() > NO_APPROVED_AMOUNT) {
            inputCaptureAmount.setText(String.valueOf(currentTransaction.getAmountApproved()));
        } else {
            inputCaptureAmount.getText().clear();
        }
        updateActions();
    }

    private Transaction findTransaction(List<Transaction> transactions) {
        if (transactionId == null) {
            return null;
        }
        for (Transaction transaction : transactions) {
            if (transaction.getId().equals(transactionId)) {
                return transaction;
            }
        }
        return null;
    }

    private void capture() {
        Long amount = readAmount(inputCaptureAmount);
        if (amount == null) {
            showMessage(R.string.toast_enter_capture_amount);
            return;
        }

        boolean accepted = viewModel.capture(transactionId, amount);
        if (!accepted) {
            showMessage(R.string.toast_capture_unavailable);
        }
    }

    private void cancel() {
        boolean accepted = viewModel.cancel(transactionId);
        if (!accepted) {
            showMessage(R.string.toast_cancel_unavailable);
        }
    }

    private void retry() {
        boolean accepted = viewModel.retry(transactionId);
        if (!accepted) {
            showMessage(R.string.toast_retry_unavailable);
        }
    }

    private void updateActions() {
        TransactionState state = currentTransaction == null ? null : currentTransaction.getState();
        boolean canCapture = state == TransactionState.AUTHORIZED;
        boolean canCancel = state == TransactionState.AUTHORIZED || state == TransactionState.AUTHORIZING;
        boolean canRetry = state == TransactionState.CAPTURE_FAILED || state == TransactionState.CANCEL_FAILED;

        btnCapture.setEnabled(canCapture);
        btnCancel.setEnabled(canCancel);
        btnRetry.setEnabled(canRetry);
        inputCaptureAmount.setEnabled(canCapture);
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
        Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show();
    }

    private MainActivity mainActivity() {
        return (MainActivity) requireActivity();
    }
}
