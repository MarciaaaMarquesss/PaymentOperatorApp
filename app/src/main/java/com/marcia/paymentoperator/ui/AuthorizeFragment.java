package com.marcia.paymentoperator.ui;

import android.os.Bundle;
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AuthorizeFragment extends Fragment {

    private static final long MIN_AMOUNT_CENTS = 1L;
    private static final int FIRST_TRANSACTION_INDEX = 0;

    private PaymentOperatorViewModel viewModel;
    private TransactionUiFormatter formatter;
    private EditText inputAmount;
    private TextView txtAuthorizationResult;
    private Button btnOpenDetails;
    private List<Transaction> transactions = Collections.emptyList();
    private UUID latestAuthorizationId;
    private Transaction currentResult;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_authorize, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        formatter = new TransactionUiFormatter(getResources());
        viewModel = new ViewModelProvider(requireActivity()).get(PaymentOperatorViewModel.class);

        inputAmount = view.findViewById(R.id.inputAmount);
        txtAuthorizationResult = view.findViewById(R.id.txtAuthorizationResult);
        btnOpenDetails = view.findViewById(R.id.btnOpenDetails);

        Button btnAuthorize = view.findViewById(R.id.btnAuthorize);
        Button btnOpenHistory = view.findViewById(R.id.btnOpenHistory);

        btnAuthorize.setOnClickListener(v -> authorize());
        btnOpenHistory.setOnClickListener(v -> mainActivity().showHistory());
        btnOpenDetails.setOnClickListener(v -> openCurrentDetails());

        viewModel.observeTransactions().observe(getViewLifecycleOwner(), updatedTransactions -> {
            transactions = updatedTransactions;
            renderLatestAuthorization();
        });
        viewModel.observeLatestAuthorizationId().observe(getViewLifecycleOwner(), transactionId -> {
            latestAuthorizationId = transactionId;
            renderLatestAuthorization();
        });

        renderLatestAuthorization();
    }

    private void authorize() {
        Long amount = readAmount(inputAmount);
        if (amount == null || amount < MIN_AMOUNT_CENTS) {
            showMessage(R.string.toast_enter_amount);
            return;
        }

        Transaction transaction = viewModel.authorize(amount);
        latestAuthorizationId = transaction.getId();
        currentResult = transaction;
        inputAmount.getText().clear();
        renderAuthorizationResult(transaction);
    }

    private void renderLatestAuthorization() {
        Transaction transaction = findTransaction(latestAuthorizationId);
        if (transaction == null
                && currentResult != null
                && currentResult.getId().equals(latestAuthorizationId)) {
            transaction = currentResult;
        }
        if (transaction == null && !transactions.isEmpty()) {
            transaction = transactions.get(FIRST_TRANSACTION_INDEX);
        }
        renderAuthorizationResult(transaction);
    }

    private Transaction findTransaction(UUID transactionId) {
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

    private void renderAuthorizationResult(Transaction transaction) {
        currentResult = transaction;
        if (transaction == null) {
            txtAuthorizationResult.setText(R.string.text_no_authorization_result);
            btnOpenDetails.setEnabled(false);
            return;
        }

        txtAuthorizationResult.setText(formatter.authorizationResult(transaction));
        btnOpenDetails.setEnabled(true);
    }

    private void openCurrentDetails() {
        if (currentResult != null) {
            mainActivity().showTransactionDetail(currentResult.getId());
        }
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
