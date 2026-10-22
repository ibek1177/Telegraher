package com.ibek.ghost;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        try {
            Reminders.show(
                    context.getApplicationContext(),
                    intent.getIntExtra("id", (int) (System.currentTimeMillis() & 0x7fffffff)),
                    intent.getStringExtra("text"),
                    intent.getStringExtra("link")
            );
        } catch (Throwable ignore) {
        }
    }
}
