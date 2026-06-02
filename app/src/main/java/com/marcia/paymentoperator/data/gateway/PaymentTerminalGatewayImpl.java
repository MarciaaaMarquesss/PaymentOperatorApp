package com.marcia.paymentoperator.data.gateway;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.elecctro.recruitment.paymentterminal.AuthorizationResult;
import com.elecctro.recruitment.paymentterminal.PaymentTerminal;
import com.elecctro.recruitment.paymentterminal.TerminalResult;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Single;

public class PaymentTerminalGatewayImpl implements PaymentTerminalGateway {

    private final PaymentTerminal terminal;
    private final Handler mainHandler;

    public PaymentTerminalGatewayImpl(PaymentTerminal terminal) {
        this.terminal = terminal;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public Single<AuthorizationResult> authorize(UUID txnId, long amountCents) {
        return liveDataSingle(() -> terminal.authorize(txnId, amountCents));
    }

    @Override
    public Single<TerminalResult> capture(UUID txnId, long amountCents) {
        return liveDataSingle(() -> terminal.capture(txnId, amountCents));
    }

    @Override
    public Single<TerminalResult> cancel(UUID txnId) {
        return liveDataSingle(() -> terminal.cancel(txnId));
    }

    private <T> Single<T> liveDataSingle(LiveDataCall<T> call) {
        return Single.create(emitter -> {
            AtomicBoolean attached = new AtomicBoolean(false);
            AtomicReference<LiveData<T>> liveDataRef = new AtomicReference<>();
            AtomicReference<Observer<T>> observerRef = new AtomicReference<>();

            Runnable attach = () -> {
                if (emitter.isDisposed()) {
                    return;
                }

                LiveData<T> liveData = call.start();
                liveDataRef.set(liveData);

                Observer<T> observer = new Observer<T>() {
                    @Override
                    public void onChanged(T result) {
                        liveData.removeObserver(this);
                        if (!emitter.isDisposed()) {
                            emitter.onSuccess(result);
                        }
                    }
                };

                observerRef.set(observer);
                attached.set(true);
                liveData.observeForever(observer);
            };

            emitter.setCancellable(() -> {
                LiveData<T> liveData = liveDataRef.get();
                Observer<T> observer = observerRef.get();
                if (liveData == null || observer == null || !attached.get()) {
                    return;
                }
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    liveData.removeObserver(observer);
                } else {
                    mainHandler.post(() -> liveData.removeObserver(observer));
                }
            });

            if (Looper.myLooper() == Looper.getMainLooper()) {
                attach.run();
            } else {
                mainHandler.post(attach);
            }
        });
    }

    private interface LiveDataCall<T> {
        LiveData<T> start();
    }
}
