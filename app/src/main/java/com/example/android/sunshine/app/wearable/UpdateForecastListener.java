package com.example.android.sunshine.app.wearable;

import android.icu.lang.UProperty;
import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.server.converter.StringToIntConverter;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Listener to update the forecast on wearable
 */
public class UpdateForecastListener extends WearableListenerService {
    private static final String LOG_TAG = UpdateForecastListener.class.getSimpleName();

    private static final String FORECAST_UPDATE_PATH = "/forecast_update";
    private static final String FORECAST_UPDATE_KEY =
            "com.example.android.sunshine.app.forecast_update";

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        for (DataEvent event : dataEventBuffer) {
            DataItem dataItem = event.getDataItem();
            String path = dataItem.getUri().getPath();
            if (FORECAST_UPDATE_PATH.equals(path)) {
                Log.d(LOG_TAG, "Sunshine sync has been called!");
                SunshineSyncAdapter.syncImmediately(getApplicationContext());
            }
        }
    }
}
