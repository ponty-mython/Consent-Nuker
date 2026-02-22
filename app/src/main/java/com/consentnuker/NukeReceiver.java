package com.consentnuker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class NukeReceiver extends BroadcastReceiver {

    private static final String TAG = "ConsentNuker";

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.consentnuker.ACTION_NUKE".equals(intent.getAction())) {
            Log.d(TAG, "Nuke action received from notification");
            ConsentNukerService service = ConsentNukerService.getInstance();
            if (service != null) {
                service.executeNuke();
            } else {
                Log.e(TAG, "Service not running - cannot nuke");
            }
        }
    }
}
