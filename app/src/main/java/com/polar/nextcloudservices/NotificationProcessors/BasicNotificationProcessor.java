package com.polar.nextcloudservices.NotificationProcessors;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import androidx.core.app.NotificationCompat;

import com.polar.nextcloudservices.R;

import org.json.JSONException;
import org.json.JSONObject;

public class BasicNotificationProcessor implements AbstractNotificationProcessor {
    public final int priority = 0;
    public int iconByApp(String appName) {
        if (appName.equals("spreed")) {
            return R.drawable.ic_icon_foreground;
        } else if (appName.equals("deck")) {
            return R.drawable.ic_deck;
        } else {
            return R.drawable.ic_logo;
        }
    }

    public static String prettifyChannelName(String Name) {
        if (Name.equals("updatenotification")) {
            return "Update notifications";
        }
        if (Name.equals("spreed")) {
            return "Nextcloud talk";
        }
        String[] parts = Name.split("_");
        StringBuilder nice_name = new StringBuilder();
        for (String part : parts) {
            nice_name.append(part);
        }
        String result = nice_name.toString();
        result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        return result;
    }

    @Override
    public NotificationCompat.Builder updateNotification(int id, NotificationCompat.Builder builder, NotificationManager manager, JSONObject rawNotification) throws JSONException {
        final String app = prettifyChannelName(rawNotification.getString("app"));
        final String title = rawNotification.getString("subject");
        final String text = rawNotification.getString("message");
        final String app_name = rawNotification.getString("app");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(app, app, NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }
        return   builder.setSmallIcon(iconByApp(app_name))
                .setContentTitle(title)
                .setAutoCancel(true)
                .setContentText(text);
    }
}