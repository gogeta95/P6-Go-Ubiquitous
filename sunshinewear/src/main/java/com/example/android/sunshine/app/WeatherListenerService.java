package com.example.android.sunshine.app;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Saurabh on 08-07-2016.
 */

public class WeatherListenerService extends WearableListenerService {
    public static final String ACTION_DATA = "ACTiondata";
    public static final String DATA_ITEM_RECEIVED_PATH = "WEATHER";
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
            DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
//            Log.d(TAG, "onDataChanged: MIN"+dataMapItem.getDataMap().getInt("MIN"));
//            Log.d(TAG, "onDataChanged: MAX"+dataMapItem.getDataMap().getInt("MAX"));
            Intent intent = new Intent(ACTION_DATA);
            intent.putExtra("MIN", dataMapItem.getDataMap().getInt("MIN"));
            intent.putExtra("MAX", dataMapItem.getDataMap().getInt("MAX"));
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }
}
