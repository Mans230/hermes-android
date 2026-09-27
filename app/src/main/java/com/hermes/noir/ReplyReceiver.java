package com.hermes.noir;

import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Notification quick reply: submits the typed text without opening the app. */
public final class ReplyReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        Bundle results=RemoteInput.getResultsFromIntent(intent);
        CharSequence said=results==null?null:results.getCharSequence("reply");
        String thread=intent==null?null:intent.getStringExtra("thread");
        if(said==null||said.toString().trim().isEmpty()||thread==null||thread.isEmpty())return;
        final String text=said.toString().trim();
        final PendingResult pending=goAsync();
        new Thread(()->{
            try{
                try{NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.cancel(2);}catch(Exception ignored){}
                Send.submit(c,thread,text);
            }catch(Exception ignored){}finally{pending.finish();}
        },"hermes-quick-reply").start();
    }
}
