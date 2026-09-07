package com.carwashmiami.ceramico;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // La nueva aplicación de ventas no utiliza recordatorios al iniciar.
    }
}
