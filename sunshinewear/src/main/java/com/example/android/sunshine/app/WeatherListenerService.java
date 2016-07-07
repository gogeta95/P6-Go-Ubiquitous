package com.example.android.sunshine.app;

import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Saurabh on 08-07-2016.
 */

public class WeatherListenerService extends WearableListenerService {
    private static final String TAG = WeatherListenerService.class.getSimpleName();

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onDataChanged: " + dataEventBuffer);
        }
        final List<DataEvent> events = FreezableUtils
                .freezeIterable(dataEventBuffer);

        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult =
                googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent event : events) {
            Uri uri = event.getDataItem().getUri();

            // Get the node id from the host value of the URI
            String nodeId = uri.getHost();
            // Set the data of the message to be the bytes of the URI
            byte[] payload = uri.toString().getBytes();

            // Send the RPC
//            Wearable.MessageApi.sendMessage(googleApiClient, nodeId,
//                    DATA_ITEM_RECEIVED_PATH, payload);
            Log.d(TAG, "DAta received: " + Arrays.toString(payload));
        }
    }
}
