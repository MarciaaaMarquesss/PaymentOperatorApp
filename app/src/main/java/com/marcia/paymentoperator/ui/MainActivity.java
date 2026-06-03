package com.marcia.paymentoperator.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.marcia.paymentoperator.R;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String BACK_STACK_HISTORY = "history";
    private static final String BACK_STACK_DETAIL = "detail";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            showAuthorize();
        }
    }

    public void showAuthorize() {
        getSupportFragmentManager().popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        replaceFragment(new AuthorizeFragment(), null);
    }

    public void showHistory() {
        replaceFragment(new HistoryFragment(), BACK_STACK_HISTORY);
    }

    public void showTransactionDetail(UUID transactionId) {
        replaceFragment(TransactionDetailFragment.newInstance(transactionId), BACK_STACK_DETAIL);
    }

    private void replaceFragment(Fragment fragment, String backStackName) {
        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment);
        if (backStackName != null) {
            transaction.addToBackStack(backStackName);
        }
        transaction.commit();
    }
}
