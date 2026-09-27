package com.hermes.noir;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms all enabled scheduled messages after a reboot. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null||!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
        final PendingResult pending=goAsync();
        new Thread(()->{try{Alarms.scheduleAll(c);}catch(Exception ignored){}finally{pending.finish();}},"hermes-boot").start();
    }
}
