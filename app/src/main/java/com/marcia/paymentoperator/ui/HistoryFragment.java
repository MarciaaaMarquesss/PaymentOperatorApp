package com.marcia.paymentoperator.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.marcia.paymentoperator.R;
import com.marcia.paymentoperator.domain.model.Transaction;

import java.util.List;

public class HistoryFragment extends Fragment {

    private TransactionUiFormatter formatter;
    private LinearLayout historyList;
    private TextView txtEmptyHistory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        formatter = new TransactionUiFormatter(getResources());
        PaymentOperatorViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(PaymentOperatorViewModel.class);

        historyList = view.findViewById(R.id.historyList);
        txtEmptyHistory = view.findViewById(R.id.txtEmptyHistory);

        Button btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> mainActivity().goBack());

        viewModel.observeTransactions().observe(getViewLifecycleOwner(), this::renderHistory);
    }

    private void renderHistory(List<Transaction> transactions) {
        historyList.removeAllViews();

        if (transactions.isEmpty()) {
            txtEmptyHistory.setVisibility(View.VISIBLE);
            return;
        }

        txtEmptyHistory.setVisibility(View.GONE);
        for (Transaction transaction : transactions) {
            Button row = new Button(requireContext());
            row.setAllCaps(false);
            row.setText(formatter.row(transaction));
            row.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            row.setOnClickListener(v -> mainActivity().showTransactionDetail(transaction.getId()));
            historyList.addView(row);
        }
    }

    private MainActivity mainActivity() {
        return (MainActivity) requireActivity();
    }
}
