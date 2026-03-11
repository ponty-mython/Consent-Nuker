package com.consentnuker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class NukeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.consentnuker.ACTION_NUKE".equals(intent.getAction())) {
            Log.d("ConsentNuker", "Nuke action received");
            ConsentNukerService service = ConsentNukerService.getInstance();
            if (service != null) {
                service.executeNuke();
            }
        }
    }
}
