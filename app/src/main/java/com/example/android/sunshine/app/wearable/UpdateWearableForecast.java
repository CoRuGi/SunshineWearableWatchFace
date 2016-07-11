package com.example.android.sunshine.app.wearable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.example.android.sunshine.app.R;
import com.example.android.sunshine.app.Utility;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


/**
 * With this class we can update the forecast information on the wearable
 */
public class UpdateWearableForecast implements
        DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    public final String LOG_TAG = UpdateWearableForecast.class.getSimpleName();

    private static final String TIMESTAMP_KEY =
            "com.example.android.sunshine.app.wearable.timestamp";
    private static final String WEATHERID_KEY =
            "com.example.android.sunshine.app.wearable.weatherid";
    private static final String HIGHTEMP_KEY =
            "com.example.android.sunshine.app.wearable.hightemp";
    private static final String LOWTEMP_KEY =
            "com.example.android.sunshine.app.wearable.lowtemp";

    private GoogleApiClient mGoogleApiClient;
    private Context mContext;

    public UpdateWearableForecast(Context context) {
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mContext = context;
    }

    /**
     * Put the data in a DataItem and wait for the system to send it to the wearable
     *
     * @param weatherId Integer The ID of Today's weather
     * @param highTemp  Integer The high temperature of Today
     * @param lowTemp   Inteder The low temperature of Today
     */
    public void UpdateForecast(Integer weatherId, Double highTemp, Double lowTemp) {
        Log.d(LOG_TAG, "Update Forecast has been called");
        mGoogleApiClient.connect();

        String highString = Utility.formatTemperature(mContext, highTemp);
        String lowString = Utility.formatTemperature(mContext, lowTemp);

        Bitmap bitmap = null;
        Asset asset = null;
        if ( Utility.usingLocalGraphics(mContext) ) {
            bitmap = BitmapFactory.decodeResource(mContext.getResources(),
                    Utility.getArtResourceForWeatherCondition(weatherId));
        } else {
            try {
                // Use weather art image
                bitmap = Glide.with(mContext)
                        .load(Utility.getArtUrlForWeatherCondition(mContext, weatherId))
                        .asBitmap()
                        .error(Utility.getArtResourceForWeatherCondition(weatherId))
                        .into(100, 100)
                        .get();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error while loading bitmap: " + e.getMessage());
            }
        }

        if (null != bitmap) {
            asset = toAsset(bitmap);
        }

        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/forecast");
        putDataMapRequest.getDataMap().putLong(TIMESTAMP_KEY, System.currentTimeMillis());
        putDataMapRequest.getDataMap().putAsset(WEATHERID_KEY, asset);
        putDataMapRequest.getDataMap().putString(HIGHTEMP_KEY, highString);
        putDataMapRequest.getDataMap().putString(LOWTEMP_KEY, lowString);
        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
        Log.d(LOG_TAG, "About to send data");
        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                        Log.d(LOG_TAG, "Tried to send data");
                        if (!dataItemResult.getStatus().isSuccess()) {
                            Log.d(LOG_TAG, "Failed to send Forecast update!");
                        } else {
                            Log.d(LOG_TAG, "Successfully sent the Forecast update");
                        }
                    }
                });
        Log.d(LOG_TAG, "Send commando executed");
    }

    public void disconnectApiConnection(){
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(LOG_TAG, "Google API Client was connected");
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(LOG_TAG, "Connection to Google API client has failed");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

    }

    private static Asset toAsset(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            return Asset.createFromBytes(byteArrayOutputStream.toByteArray());
        } finally {
            if (null != byteArrayOutputStream) {
                try {
                    byteArrayOutputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
