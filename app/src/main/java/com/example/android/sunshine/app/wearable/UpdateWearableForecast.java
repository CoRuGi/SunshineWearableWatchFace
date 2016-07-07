package com.example.android.sunshine.app.wearable;

import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;


/**
 * With this class we can update the forecast information on the wearable
 */
public class UpdateWearableForecast implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String WEATHERID_KEY =
            "com.example.android.sunshine.app.wearable.weatherid";
    private static final String HIGHTEMP_KEY =
            "com.example.android.sunshine.app.wearable.hightemp";
    private static final String LOWTEMP_KEY =
            "com.example.android.sunshine.app.wearable.lowtemp";


    private GoogleApiClient mGoogleApiClient;

    public UpdateWearableForecast(Context context) {
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    /**
     * Put the data in a DataItem and wait for the system to send it to the wearable
     *
     * @param weatherId Integer The ID of Today's weather
     * @param highTemp  Integer The high temperature of Today
     * @param lowTemp   Inteder The low temperature of Today
     */
    public void UpdateForecast(Integer weatherId, Integer highTemp, Integer lowTemp) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/forecast");
        putDataMapRequest.getDataMap().putInt(WEATHERID_KEY, weatherId);
        putDataMapRequest.getDataMap().putInt(HIGHTEMP_KEY, highTemp);
        putDataMapRequest.getDataMap().putInt(LOWTEMP_KEY, lowTemp);
        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}
