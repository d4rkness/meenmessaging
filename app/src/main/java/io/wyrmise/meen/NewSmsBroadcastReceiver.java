package io.wyrmise.meen;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;
import android.os.PowerManager.WakeLock;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NewSmsBroadcastReceiver extends BroadcastReceiver {

    public static final String SMS_BUNDLE = "pdus";

    private int notificationID = 100;

    public static void insertMessage(Context context, Message msg){
        SimpleDateFormat hours = new SimpleDateFormat("h:mm a",
                Locale.US);
        msg.messageDate=hours.format(new Date());
        ContentValues values = new ContentValues();
        values.put("address", msg.messageNumber);
        values.put("body",msg.messageContent);
        values.put("read",msg.readState);
        Uri uri = Uri.parse("content://sms/");
        context.getContentResolver().insert(uri,values);
    }

    public static void markSmsAsRead(Context context, final String from, final String body) {
        Uri uri = Uri.parse("content://sms/");
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        try{
            while (cursor.moveToNext()) {
                if ((cursor.getString(cursor.getColumnIndex("address")).equals(from)) && (cursor.getInt(cursor.getColumnIndex("read")) == 0)) {
                    if (cursor.getString(cursor.getColumnIndex("body")).startsWith(body)) {
                        String SmsMessageId = cursor.getString(cursor.getColumnIndex("_id"));
                        ContentValues values = new ContentValues();
                        values.put("read", 1);
                        context.getContentResolver().update(Uri.parse("content://sms/"), values, "_id=" + SmsMessageId, null);
                        return;
                    }
                }
            }
        }catch(Exception e)
        {
            Log.e("Mark Read", "Error in Read: " + e.toString());
        }
    }

    public static void markSmsAsUnread(Context context, final String from, final String body) {
        Uri uri = Uri.parse("content://sms/");
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        try{
            while (cursor.moveToNext()) {
                if ((cursor.getString(cursor.getColumnIndex("address")).equals(from))) {
                    if (cursor.getString(cursor.getColumnIndex("body")).startsWith(body)) {
                        String SmsMessageId = cursor.getString(cursor.getColumnIndex("_id"));
                        ContentValues values = new ContentValues();
                        values.put("read", 0);
                        context.getContentResolver().update(Uri.parse("content://sms/"), values, "_id=" + SmsMessageId, null);
                        return;
                    }
                }
            }
        }catch(Exception e)
        {
        }
    }

    public void onReceive(Context context, Intent intent) {
        Bundle intentExtras = intent.getExtras();
        if (intentExtras != null) {
            Object[] sms = (Object[]) intentExtras.get(SMS_BUNDLE);
            String address  = "";
            String smsBody = "";

            for (int i = 0; i < sms.length; ++i) {
                SmsMessage smsMessage = SmsMessage
                        .createFromPdu((byte[]) sms[i]);
                smsBody = smsMessage.getMessageBody().toString();
                address = smsMessage.getOriginatingAddress();
            }
            Log.d("address",address);
            Message msg = new Message();
            msg.messageNumber=address;
            msg.messageContent=smsBody;
            SimpleDateFormat hours = new SimpleDateFormat("h:mm a",
                    Locale.US);
            msg.messageDate=hours.format(new Date());
            msg.readState = 0;
            insertMessage(context,msg);
            if(MainActivity.instance()==null) {
                pushNotification(context,msg);
            }
            else {
                MainActivity inst = MainActivity.instance();
                inst.pushNotification(msg);
                inst.updateList(msg);
            }
        }
    }

    public void pushNotification(Context context, final Message message) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
                context);
        Uri soundUri = RingtoneManager
                .getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        String number = message.messageNumber.replace(" ", "").replace("-", "");
        if (number.startsWith("+84")) {
            number = number.substring(3);
            number = "0" + number;
        }
        SharedPreferences contactPrefs = context.getSharedPreferences("contacts", context.MODE_PRIVATE);
        SharedPreferences colorPrefs = context.getSharedPreferences("colors", context.MODE_PRIVATE);
        if(contactPrefs.getString(number, null)!=null)
            message.messageNumber = contactPrefs.getString(number, null);
        Toast.makeText(context, "SMS from " + message.messageNumber,
                Toast.LENGTH_SHORT).show();
        mBuilder.setSmallIcon(R.drawable.notification_icon);
        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_launcher);
        mBuilder.setLargeIcon(largeIcon);
        mBuilder.setContentTitle(message.messageNumber);
        mBuilder.setContentText(message.messageContent);
        mBuilder.setAutoCancel(true);
        mBuilder.setSound(soundUri);

        Intent resultIntent = new Intent(context, ThreadActivity.class);
        resultIntent.putExtra("Phone", message.messageNumber);
        resultIntent.putExtra("originalAddress", message.originalAddress);
        if(colorPrefs.getInt(message.messageNumber,-1)!=-1)
            resultIntent.putExtra("Color",colorPrefs.getInt(message.messageNumber,-1));
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(ThreadActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification note = mBuilder.build();
        note.defaults |= Notification.DEFAULT_VIBRATE;
        note.defaults |= Notification.DEFAULT_SOUND;
        note.defaults |= Notification.DEFAULT_LIGHTS;

        // notificationID allows you to update the notification later on.
        mNotificationManager.notify(notificationID, mBuilder.build());
        context.getSystemService(Context.AUDIO_SERVICE);

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "TAG");
        wl.acquire(5000);
    }

}