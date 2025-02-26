package com.ajith.voipcall;

import android.app.AlarmManager;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.facebook.react.bridge.ReadableMap;

import java.util.Timer;
import java.util.TimerTask;

public class RNVoipNotificationHelper {
    private static RNVoipNotificationHelper sInstance = null;
    public final String callChannel = "Call";
    public  final  String notificationChannel = "NotificationChannel";
    private Context context;
    private Handler mHandler;
    private Runnable mRunnable;
    final int notificationID = 9999;//json.getInt("notificationId");
    public RNVoipNotificationHelper(Application context){
        this.context = context;
        sInstance = this;
    }

    public static RNVoipNotificationHelper getInstance() {
        return sInstance;
    }

    public Class getMainActivityClass() {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }


    public void sendCallNotification(final ReadableMap jsonObject){
            // Param is optional, to run task on UI thread.
            mHandler = new Handler(this.context.getMainLooper());
            mRunnable = new Runnable() {
                private int counter = 0;
                @Override
                public void run() {
                    if(counter >= 10){
                        clearNotificationRepeat();
                        return;
                    }
                    counter += 1;
                    sendNotification(jsonObject);
                    if(mHandler != null){
                        mHandler.postDelayed(this, 6000);
                    }else{
                        clearAllNotifications();
                    }
                }
            };
            mHandler.postDelayed(mRunnable, 0);
    }

    public void clearNotificationRepeat(){
        if(mHandler != null){
            mHandler.removeCallbacksAndMessages(mRunnable);
            mHandler = null;
            mRunnable = null;
        }
        clearAllNotifications();
    }


    public void sendNotification(ReadableMap json){
        Intent dissmissIntent = new Intent(context, RNVoipBroadcastReciever.class);
        dissmissIntent.setAction("callDismiss");
        dissmissIntent.putExtra("notificationId",notificationID);
        dissmissIntent.putExtra("callerId", json.getString("callerId"));
        dissmissIntent.putExtra("missedCallTitle", json.getString("missedCallTitle"));
        dissmissIntent.putExtra("missedCallBody", json.getString("missedCallBody"));
        PendingIntent callDismissIntent = PendingIntent.getBroadcast(context,0, dissmissIntent ,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

//        Uri sounduri = Uri.parse("android.resource://" + context.getPackageName() + "/"+ R.raw.ringtune);
        Uri sounduri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        Notification notification = new NotificationCompat.Builder(context,callChannel)
                .setAutoCancel(true)
                .setDefaults(0)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setTimeoutAfter(5500)
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .setVibrate(new long[]{0, 500, 1000,500,1000,500})
                .setFullScreenIntent(getPendingIntent(notificationID, "fullScreenIntent", json) , true)
                .setContentIntent(getPendingIntent(notificationID, "contentTap", json))
                .setSmallIcon(R.drawable.ic_call_black_24dp)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentTitle(json.getString("notificationTitle"))
                .setSound(sounduri)
                .setContentText(json.getString("notificationBody"))
                .addAction(0, json.getString("answerActionTitle"), getPendingIntent(notificationID, "callAnswer",json))
                .addAction(0, json.getString("declineActionTitle"), callDismissIntent)
                .build();

        NotificationManager notificationManager = notificationManager();
        createCallNotificationChannel(notificationManager, json);
        notificationManager.notify(notificationID,notification);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    clearNotification(notificationID);
                }
            },5000);
        }
    }



    public void createCallNotificationChannel(NotificationManager manager, ReadableMap json){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            Uri sounduri = Uri.parse("android.resource://" + context.getPackageName() + "/"+ R.raw.ringtune);
            Uri sounduri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            NotificationChannel channel = new NotificationChannel(callChannel, json.getString("channel_name"), NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Call Notifications");
            channel.setSound(sounduri ,
                    new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000,500, 1000, 500, 1000});
            channel.enableVibration(json.getBoolean("vibration"));
            manager.createNotificationChannel(channel);
        }
    }

    public boolean isDeviceLocked(Context context){
        KeyguardManager myKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return myKM.inKeyguardRestrictedInputMode();
    }

    public PendingIntent getPendingIntent(int notificationID , String type, ReadableMap json){

        Class intentClass = getMainActivityClass();
        Intent intent = new Intent(context, isDeviceLocked(this.context)? RNVoipBroadcastReciever.class:intentClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("notificationId",notificationID);
        intent.putExtra("callerId", json.getString("callerId"));
        intent.putExtra("action", type);
        intent.setAction(type);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, notificationID, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return pendingIntent;
    }


    //Missed Call Notification
    public void showMissCallNotification(String title , String body, String callerId){
        Log.i("WebrtcPushNotification", title + " ======  " + body);
        int missNotification = 123;
        Uri missedCallSound= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Class intentClass = getMainActivityClass();
        Intent intent = new Intent(context, intentClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("notificationId",missNotification);
        intent.putExtra("callerId", callerId);
        intent.setAction("missedCallTape");
        PendingIntent contentIntent = PendingIntent.getActivity(context, missNotification, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, notificationChannel)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_phone_missed_black_24dp)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(missedCallSound)
                .setContentIntent(contentIntent)
                .build();
        NotificationManager notificationManager = notificationManager();
        createNotificationChannel(notificationManager,missedCallSound);
        notificationManager.notify(missNotification,notification);
    }


    public void createNotificationChannel(NotificationManager manager , Uri sounduri){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notificationChannel, "missed call", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Call Notifications");
            channel.setSound(sounduri ,
                    new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_UNKNOWN).build());
            channel.setVibrationPattern(new long[]{0, 1000});
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }
    }




    public void clearNotification(int notificationID) {
        NotificationManager notificationManager = notificationManager();
        notificationManager.cancel(notificationID);
    }


    public void clearAllNotifications(){
        NotificationManager manager = notificationManager();
        manager.cancelAll();
    }


    private NotificationManager notificationManager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }


}
