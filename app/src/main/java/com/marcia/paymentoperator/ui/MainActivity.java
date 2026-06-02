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
import com.marcia.paymentoperator.domain.engine.TransactionEngine;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private TransactionEngine engine;
    private TextView txResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn = findViewById(R.id.btnAuthorize);
        txResult = findViewById(R.id.txtResult);

        PaymentTerminal terminal = PaymentTerminal.create(PaymentTerminal.Mode.HAPPY);
        PaymentTerminalGateway gateway = new PaymentTerminalGatewayImpl(terminal);
        TransactionEngine engine = new TransactionEngine(gateway);
        System.out.println("BTN = " + btn);

        btn.setOnClickListener(v -> {
            System.out.println("CLICK DETECTED");
            long amount = 1000;
            engine.authorize(amount)
                    .observe(this, result -> {
                        txResult.setText(result.state.toString());
                    });
        });
    }
}
