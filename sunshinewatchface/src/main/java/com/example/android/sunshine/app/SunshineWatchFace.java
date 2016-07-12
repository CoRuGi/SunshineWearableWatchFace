/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    public static final String LOG_TAG = SunshineWatchFace.class.getSimpleName();

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface THIN_TYPEFACE =
            Typeface.create("sans-serif-thin", Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    public class Engine extends CanvasWatchFaceService.Engine implements
            DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mSecondTextPaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mColonPaint;
        Paint mWeatherIdPaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;
        float mDateHeight = 15f;
        float mForecastHeight = 20f;

        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        // Data Layer items
        private static final String FORECAST_PATH = "/forecast";

        private static final String WEATHERID_KEY =
                "com.example.android.sunshine.app.wearable.weatherid";
        private static final String HIGHTEMP_KEY =
                "com.example.android.sunshine.app.wearable.hightemp";
        private static final String LOWTEMP_KEY =
                "com.example.android.sunshine.app.wearable.lowtemp";

        private String mHighTemp;
        private String mLowTemp;
        private Integer mWeatherId;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .setHotwordIndicatorGravity(Gravity.END | Gravity.TOP)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mHourPaint = createTextPaint(getColor(R.color.digital_text), BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(getColor(R.color.digital_text));
            mSecondPaint = createTextPaint(getColor(R.color.digital_text));
            mColonPaint = createTextPaint(getColor(R.color.digital_text));
            mSecondTextPaint = createTextPaint(getColor(R.color.secondary_text));
            mWeatherIdPaint = createTextPaint(getColor(R.color.digital_text));
            mHighTempPaint = createTextPaint(getColor(R.color.digital_text), BOLD_TYPEFACE);
            mLowTempPaint = createTextPaint(getColor(R.color.secondary_text));

            mTime = new Time();

            //mGoogleApiClient.connect();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int defaultInteractiveColor) {
            return createTextPaint(defaultInteractiveColor, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mGoogleApiClient.connect();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
                Wearable.DataApi.removeListener(mGoogleApiClient, this);
                mGoogleApiClient.disconnect();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);

            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mSecondPaint.setTextSize(textSize);
            mColonPaint.setTextSize(textSize);

            DisplayMetrics metrics = resources.getDisplayMetrics();
            mSecondTextPaint.setTextSize(mDateHeight * metrics.density);
            mWeatherIdPaint.setTextSize(mForecastHeight * metrics.density);
            mHighTempPaint.setTextSize(mForecastHeight * metrics.density);
            mLowTempPaint.setTextSize(mForecastHeight * metrics.density);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunshineWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO Change background color on tap
//                    mTapCount++;
//                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
//                            R.color.background : R.color.background2));
//                    break;
            }
//            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(getColor(R.color.ambient_background));
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Update the time
            mTime.setToNow();

            mSecondTextPaint.setAlpha(100);

            // Set the strings
            String hourString = String.format("%02d:", mTime.hour);
            String minuteString = String.format("%02d", mTime.minute);
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM dd yyyy", Locale.US);
            String dateString = dateFormat.format(System.currentTimeMillis());
            String separatorString = getString(R.string.watch_face_separator);

            // Calculate the offsets
            float x = mXOffset;
            float y = mYOffset;
            float centerScreen = bounds.width() / 2;
            float hourOffset = centerScreen - (mHourPaint.measureText(hourString)
                    - (mHourPaint.measureText(getString(R.string.time_separator))) / 2);
            float minutesOffset = centerScreen +
                    (mHourPaint.measureText(getString(R.string.time_separator)) / 2);
            float dateXOffset = centerScreen - (mSecondTextPaint.measureText(dateString) / 2);
            float separatorXOffset = centerScreen - (mSecondTextPaint.measureText(separatorString) / 2);

            float timeYOffset = (canvas.getHeight() / 5) * 2;

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            float dateYOffset = timeYOffset + (mDateHeight * metrics.density);
            float separatorYOffset = dateYOffset + (20f * metrics.density);

            // Draw the time
            canvas.drawText(hourString, hourOffset, timeYOffset, mHourPaint);
            canvas.drawText(minuteString, minutesOffset, timeYOffset, mMinutePaint);

            // Draw the date
            canvas.drawText(dateString, dateXOffset, dateYOffset, mSecondTextPaint);

            // Draw the separator
            canvas.drawText(separatorString, separatorXOffset, separatorYOffset, mSecondTextPaint);

            // Check if the forecast data has been set
            if (mWeatherId != null && mHighTemp != null && mLowTemp != null) {

                String forecastString = mWeatherId.toString() + " " + mHighTemp + " " + mLowTemp;
                float forecastXOffset = centerScreen -
                        (mHighTempPaint.measureText(forecastString) / 2);
                float forecastYOffset = separatorYOffset + (canvas.getHeight() / 5);

                // Draw the Bitmap
                Integer iconId = Utility.getIconResourceForWeatherCondition(mWeatherId);
                Bitmap icon = BitmapFactory.decodeResource(getApplicationContext().getResources(),
                        iconId);
                canvas.drawBitmap(icon, hourOffset - 10, forecastYOffset - 45, null);

                // Draw the HighTemp
                float forecastHighTempXOffset = centerScreen -
                        (mHighTempPaint.measureText(mHighTemp) / 2);
                canvas.drawText(mHighTemp, forecastHighTempXOffset, forecastYOffset, mHighTempPaint);

                // Draw the LowTemp
                float forecastLowTempXOffset = forecastHighTempXOffset +
                        mHighTempPaint.measureText(mHighTemp) + 15;
                canvas.drawText(mLowTemp, forecastLowTempXOffset, forecastYOffset, mLowTempPaint);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(LOG_TAG, "Data changed!");

            // Loop through the events
            for (DataEvent event : dataEventBuffer) {
                DataItem dataItem = event.getDataItem();
                String path = dataItem.getUri().getPath();
                if (FORECAST_PATH.equals(path)) {
                    DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                    updateForecast(
                            dataMap.getInt(WEATHERID_KEY),
                            dataMap.getString(HIGHTEMP_KEY),
                            dataMap.getString(LOWTEMP_KEY)
                    );
                }
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

        public void updateForecast(Integer weatherId, String highTemp, String lowTemp) {
            mWeatherId = weatherId;
            mHighTemp = highTemp;
            mLowTemp = lowTemp;

            invalidate();
        }
    }
}
