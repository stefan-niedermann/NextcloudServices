package com.polar.nextcloudservices;

/*
 * Implements API for accounts imported from nextcloud.
 */

import android.net.Uri;
import android.util.Log;

import com.nextcloud.android.sso.aidl.NextcloudRequest;
import com.nextcloud.android.sso.api.NextcloudAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class NextcloudSSOAPI implements NextcloudAbstractAPI {
    private NextcloudAPI API;
    final private String TAG="NextcloudSSOAPI";

    public NextcloudSSOAPI(NextcloudAPI mNextcloudAPI) {
        API = mNextcloudAPI;
    }

    @Override
    public JSONObject getNotifications(NotificationService service) {
        NextcloudRequest request = new NextcloudRequest.Builder().setMethod("GET")
                .setUrl(Uri.encode("/ocs/v2.php/apps/notifications/api/v2/notifications", "/"))
                .build();
        StringBuilder buffer = new StringBuilder("");
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(API.performNetworkRequest(request)));

            String line = "";
            while ((line = in.readLine()) != null) {
                buffer.append(line);
            }
            in.close();
        }catch(Exception e){
            service.status = "Disconnected: "+e.getLocalizedMessage();
            e.printStackTrace();
            return null;
        }

        try{
            JSONObject response = new JSONObject(buffer.toString());
            service.onPollFinished(response);
            return response;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON");
            e.printStackTrace();
            service.status = "Disconnected: server has sent bad response: " + e.getLocalizedMessage();
            return null;
        }
    }
}
