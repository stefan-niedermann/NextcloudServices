package com.polar.nextcloudservices.Notification.Processors;

import static com.polar.nextcloudservices.Notification.NotificationEvent.NOTIFICATION_EVENT_FASTREPLY;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.graphics.drawable.IconCompat;

import com.polar.nextcloudservices.API.NextcloudAbstractAPI;
import com.polar.nextcloudservices.Config;
import com.polar.nextcloudservices.Notification.AbstractNotificationProcessor;
import com.polar.nextcloudservices.Notification.NotificationController;
import com.polar.nextcloudservices.Notification.NotificationEvent;
import com.polar.nextcloudservices.R;
import com.polar.nextcloudservices.Services.Settings.ServiceSettings;
import com.polar.nextcloudservices.Utils.CommonUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class NextcloudTalkProcessor implements AbstractNotificationProcessor {
    public final int priority = 2;
    private static final String TAG = "Notification.Processors.NextcloudTalkProcessor";
    private static final String KEY_TEXT_REPLY = "key_text_reply";

    @SuppressLint("UnspecifiedImmutableFlag")
    static private PendingIntent getReplyIntent(Context context,
                                                @NonNull JSONObject rawNotification) throws JSONException {
        Intent intent = new Intent();
        int notification_id = rawNotification.getInt("notification_id");
        intent.setAction(Config.NotificationEventAction);
        intent.putExtra("notification_id", rawNotification.getInt("notification_id"));
        intent.putExtra("notification_event", NOTIFICATION_EVENT_FASTREPLY);
        String[] link = rawNotification.getString("link").split("/"); // use provided link to extract talk chatroom id
        intent.putExtra("talk_chatroom", link[link.length-1]);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return PendingIntent.getBroadcast(
                    context,
                    notification_id,
                    intent,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
        }else{
            return PendingIntent.getBroadcast(
                    context,
                    notification_id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }
    }

    @NonNull
    private Person getPersonFromNotification(@NonNull NotificationController controller,
                                             @NonNull JSONObject rawNotification) throws Exception {
        Person.Builder builder = new Person.Builder();
        if(rawNotification.getJSONObject("subjectRichParameters").has("user")){
            JSONObject user = rawNotification.getJSONObject("subjectRichParameters")
                    .getJSONObject("user");
            final String name = user.getString("name");
            final String id = user.getString("id");
            builder.setKey(id).setName(name);
            Bitmap image = controller.getAPI().getUserAvatar(id);
            IconCompat compat = IconCompat.createWithAdaptiveBitmap(image);
            builder.setIcon(compat);
            return builder.build();
        }else {
            final String key = rawNotification.getString("object_id");
            builder.setKey(key);
            final String name = rawNotification.getJSONObject("subjectRichParameters")
                    .getJSONObject("call").getString("name");
            //NOTE:Nextcloud Talk does not seem to provide ability for setting avatar for calls
            //     so it is not fetched here
            return builder.setName(name).build();
        }
    }

    private NotificationCompat.Builder setCustomTabsIntent(Context context,
                                                           NotificationCompat.Builder builder,
                                                           String link) {
        CustomTabsIntent browserIntent = new CustomTabsIntent.Builder()
                .setUrlBarHidingEnabled(true)
                .setShowTitle(false)
                .setStartAnimations(context, android.R.anim.fade_in, android.R.anim.fade_out)
                .setExitAnimations(context, android.R.anim.fade_in, android.R.anim.fade_out)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build();
        browserIntent.intent.setData(Uri.parse(link));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return builder.setContentIntent(PendingIntent.getActivity(context, 0,
                    browserIntent.intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }else{
            return builder.setContentIntent(PendingIntent.getActivity(context, 0,
                    browserIntent.intent, PendingIntent.FLAG_UPDATE_CURRENT));
        }
    }

    private NotificationCompat.Builder setTalkOpenIntent(Context context,
                                                         NotificationCompat.Builder builder){
        PackageManager pm = context.getPackageManager();
        if (!CommonUtil.isPackageInstalled("com.nextcloud.talk2", pm)) {
            Log.w(TAG, "Expected to find com.nextcloud.talk2 installed, but package was not found");
            return builder;
        }
        Log.d(TAG, "Setting up talk notification open intent");

        Intent intent = pm.getLaunchIntentForPackage("com.nextcloud.talk2");
        PendingIntent pending_intent;
        pending_intent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return  builder.setContentIntent(pending_intent);
    }

    private NotificationCompat.Builder setOpenIntent(NotificationController controller,
                                                     NotificationCompat.Builder builder,
                                                     Context context, String link){
        ServiceSettings settings = controller.getServiceSettings();
        if(settings.getSpreedOpenedInBrowser()){
            return setCustomTabsIntent(context, builder, link);
        } else {
            PackageManager pm = context.getPackageManager();
            if (!CommonUtil.isPackageInstalled("com.nextcloud.talk2", pm)) {
                Log.w(TAG, "Expected to find com.nextcloud.talk2 installed, but package was not found");
                return setCustomTabsIntent(context, builder, link);
            }
            return setTalkOpenIntent(context, builder);
        }
    }


    @SuppressLint("UnspecifiedImmutableFlag")
    @Override
    public NotificationCompat.Builder updateNotification(int id, NotificationCompat.Builder builder,
                                                         NotificationManager manager,
                                                         @NonNull JSONObject rawNotification,
                                                         Context context, NotificationController controller) throws Exception {

        if (!rawNotification.getString("app").equals("spreed")) {
            return builder;
        }

        Log.d(TAG, "Setting up talk notification");

        if (rawNotification.has("object_type")) {
            if (rawNotification.getString("object_type").equals("chat")) {
                Log.d(TAG, "Talk notification of chat type, adding fast reply button");
                String replyLabel = "Reply"; //FIXME: get text from resources
                RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                        .setLabel(replyLabel)
                        .build();
                PendingIntent replyPendingIntent = getReplyIntent(context, rawNotification);
                final String fastreply_title = context.getString(R.string.talk_fast_reply);
                NotificationCompat.Action action =
                        new NotificationCompat.Action.Builder(R.drawable.ic_reply_icon,
                                fastreply_title, replyPendingIntent)
                                .addRemoteInput(remoteInput)
                                .setAllowGeneratedReplies(true)
                                .build();
                builder.addAction(action);
                final String title = rawNotification.getJSONObject("subjectRichParameters")
                        .getJSONObject("call").getString("name");
                Person chat = getPersonFromNotification(controller, rawNotification);
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+00:00'");
                final String dateStr = rawNotification.getString("datetime");
                long unixTime = 0;
                try {
                    Date date = format.parse(dateStr);
                    if (date == null) {
                        throw new ParseException("Date was not parsed: result is null", 0);
                    }
                    unixTime = date.getTime();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                if (rawNotification.getString("messageRich").equals("{file}") && rawNotification
                        .getJSONObject("messageRichParameters")
                        .getJSONObject("file")
                        .getString("mimetype").startsWith("image/")) {
                    Bitmap imagePreview = controller.getAPI().getImagePreview(rawNotification
                            .getJSONObject("messageRichParameters")
                            .getJSONObject("file").getString("id"));
                    builder.setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(imagePreview));
                } else {
                    builder.setStyle(new NotificationCompat.MessagingStyle(chat)
                            .setConversationTitle(title)
                            .addMessage(rawNotification.getString("message"), unixTime, chat));
                }
            }
        }
        builder = setOpenIntent(controller, builder, context,
                rawNotification.getString("link"));
        return builder;
    }

    @Override
    public void onNotificationEvent(NotificationEvent event, Intent intent,
                                    NotificationController controller) {
        if (event == NOTIFICATION_EVENT_FASTREPLY) {
            final String chatroom = intent.getStringExtra("talk_chatroom"); // the string send by spreed is chatroomid/
            final int notification_id = intent.getIntExtra("notification_id", -1);
            if (notification_id < 0) {
                Log.wtf(TAG, "Bad notification id: " + notification_id);
                return;
            }
            Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
            if (remoteInput == null) {
                Log.e(TAG, "Reply event has null reply text");
                return;
            }
            final String reply = remoteInput.getCharSequence(KEY_TEXT_REPLY).toString();
            NextcloudAbstractAPI api = controller.getAPI();
            Thread thread = new Thread(() -> {
                try {
                    api.sendTalkReply(chatroom, reply);
                    api.removeNotification(notification_id);
                    controller.removeNotification(notification_id);
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }
            });
            thread.start();

        }
    }

    @Override
    public int getPriority() {
        return priority;
    }
}
