package com.marcia.paymentoperator.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;

import com.elecctro.recruitment.paymentterminal.AuthorizationResult;
import com.elecctro.recruitment.paymentterminal.PaymentTerminal;
import com.marcia.paymentoperator.R;
import com.marcia.paymentoperator.data.gateway.PaymentTerminalGateway;
import com.marcia.paymentoperator.data.gateway.PaymentTerminalGatewayImpl;
import com.marcia.paymentoperator.data.repository.TransactionRepository;
import com.marcia.paymentoperator.data.repository.TransactionRepositoryImpl;
import com.marcia.paymentoperator.domain.engine.TransactionEngine;
import com.marcia.paymentoperator.domain.model.Transaction;

import java.io.File;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private TransactionEngine engine;
    private TransactionRepository repository;

    private Transaction lastTransaction;
    private TextView txResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn = findViewById(R.id.btnAuthorize);
        Button btnCancel = findViewById(R.id.btnCancel);
        txResult = findViewById(R.id.txtResult);

        PaymentTerminal terminal = PaymentTerminal.create(PaymentTerminal.Mode.HAPPY);

        PaymentTerminalGateway gateway = new PaymentTerminalGatewayImpl(terminal);

        repository = new TransactionRepositoryImpl(new File(getFilesDir(), "transactions.properties"));

        repository.observeTransactions()
                .observe(this, transactions -> {

                    if (transactions.isEmpty()) {
                        return;
                    }

                    Transaction tx =
                            transactions.get(transactions.size() - 1);

                    txResult.setText(
                            "ID: " + tx.getId()
                                    + "\nState: " + tx.getState()
                    );
                });

        engine = new TransactionEngine(gateway, repository);
        System.out.println("BTN = " + btn);

        btn.setOnClickListener(v -> {
            System.out.println("CLICK DETECTED");
            lastTransaction = engine.authorize(1000);
        });

        btnCancel.setOnClickListener(v -> {

            if (lastTransaction != null) {

                engine.cancel(lastTransaction.getId());

            }

        });
    }
}
