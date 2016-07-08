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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface LIGHT_TYPEFACE =
            Typeface.create("sans-serif-light", Typeface.NORMAL);


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
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final int DEFAULT_MAX = 1000;
        private static final int DEFAULT_MIN = -273;
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        private final String TAG = Engine.class.getSimpleName();
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mDatePaint;
        Paint minPaint;
        Paint maxPaint;
        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;
        int min;
        int max;
        Rect rect = new Rect();

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        BroadcastReceiver tempReceiver;
        BroadcastReceiver imageReceiver;
        Bitmap bitmap;
        Bitmap ambientBitmap;

        private void loadBitmap() {
            File cacheDir = getBaseContext().getCacheDir();
            File f = new File(cacheDir, "image.jpg");
            FileInputStream fis;
            try {
                fis = new FileInputStream(f);
                bitmap = BitmapFactory.decodeStream(fis);
                ambientBitmap = Utils.toGrayscale(bitmap);
                invalidate();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        private void setTemp(int min, int max) {
            PreferenceManager.getDefaultSharedPreferences(MyWatchFace.this).edit()
                    .putInt("MIN", min)
                    .putInt("MAX", max)
                    .apply();
        }

        private void loadTemp() {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MyWatchFace.this);
            min = preferences.getInt("MIN", DEFAULT_MIN);
            max = preferences.getInt("MAX", DEFAULT_MAX);
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            tempReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    min = intent.getIntExtra("MIN", DEFAULT_MIN);
                    max = intent.getIntExtra("MAX", DEFAULT_MAX);
                    setTemp(min, max);
                    invalidate();
                }
            };
            IntentFilter tempFilter = new IntentFilter(WeatherListenerService.ACTION_DATA);
            LocalBroadcastManager.getInstance(MyWatchFace.this).registerReceiver(tempReceiver, tempFilter);
            imageReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    loadBitmap();
                }
            };
            loadBitmap();
            IntentFilter imageFilter = new IntentFilter(WeatherListenerService.ACTION_IMAGE);
            LocalBroadcastManager.getInstance(MyWatchFace.this).registerReceiver(imageReceiver, imageFilter);
            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            loadTemp();
            mBackgroundPaint = new Paint();

            mHourPaint = new Paint();
            mHourPaint = createHourPaint(resources.getColor(R.color.digital_text));

            mMinutePaint = new Paint();
            mMinutePaint = createMinutePaint(resources.getColor(R.color.digital_text));

            mDatePaint = new Paint();
            mDatePaint = createDatePaint(resources.getColor(R.color.digital_text));

            maxPaint = new Paint();
            maxPaint = createMaxPaint(resources.getColor(R.color.digital_text));

            minPaint = new Paint();
            minPaint = createMinPaint(resources.getColor(R.color.digital_text));

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (imageReceiver != null)
                LocalBroadcastManager.getInstance(MyWatchFace.this).unregisterReceiver(imageReceiver);
            if (tempReceiver != null)
                LocalBroadcastManager.getInstance(MyWatchFace.this).unregisterReceiver(tempReceiver);
            super.onDestroy();
        }

        private Paint createHourPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(BOLD_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createMinutePaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            //60% alpha.
            paint.setAlpha(153);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createDatePaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            //60% alpha.
//            paint.setAlpha(153);
            paint.setTypeface(LIGHT_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createMaxPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            //60% alpha.
//            paint.setAlpha(153);
            paint.setTypeface(LIGHT_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createMinPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            //60% alpha.
            paint.setAlpha(153);
            paint.setTypeface(LIGHT_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }


        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
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
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            maxPaint.setTextSize(textSize);
            minPaint.setTextSize(textSize);
            textSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);
            mDatePaint.setTextSize(textSize);
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
                    mHourPaint.setAntiAlias(!inAmbientMode);
                    mMinutePaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    minPaint.setAntiAlias(!inAmbientMode);
                    maxPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            String hour = String.format(Locale.getDefault(), "%02d:", mCalendar.get(Calendar.HOUR));
            String minute = String.format(Locale.getDefault(), "%02d", mCalendar.get(Calendar.MINUTE));
            String dateText = String.format(Locale.getDefault(), "%1$tA | %1$tb %1$td, %1$tY", mCalendar);
            String min = "";
            String max = "";
            if (this.min != DEFAULT_MIN)
                min = this.min + "°";
            if (this.max != DEFAULT_MAX)
                max = this.max + "°";
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawColor(Color.parseColor(String.format(Locale.getDefault(), "#%02d%02d%02d", mCalendar.get(Calendar.HOUR), mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND))));
            }
            float yoffset = rect.bottom + mYOffset;
            mDatePaint.getTextBounds(dateText, 0, dateText.length(), rect);
            Resources resources = MyWatchFace.this.getResources();
            yoffset += rect.height() + resources.getDimension(R.dimen.date_margin_top);
            float x = 0;
            if (bitmap != null)
                x = canvas.getWidth() - bitmap.getWidth() - mXOffset;
            mHourPaint.getTextBounds(hour, 0, hour.length(), rect);
            float y = 0;
            if (bitmap != null)
                y = mYOffset - rect.height() / 2 - bitmap.getHeight() / 2 + 10;
            if (isInAmbientMode() && ambientBitmap != null) {
                canvas.drawBitmap(ambientBitmap, x, y, null);
            } else if (bitmap != null)
                canvas.drawBitmap(bitmap, x, y, null);
            canvas.drawText(hour, mXOffset, mYOffset, mHourPaint);
            canvas.drawText(minute, mXOffset + mHourPaint.measureText(hour), mYOffset, mMinutePaint);
            canvas.drawText(dateText, mXOffset, yoffset, mDatePaint);
            mDatePaint.getTextBounds(dateText, 0, dateText.length(), rect);
            yoffset += rect.height() + resources.getDimension(R.dimen.date_margin_top);
            mHourPaint.getTextBounds(max, 0, max.length(), rect);
            canvas.drawText(max, canvas.getWidth() / 2 - rect.width(), yoffset + rect.height() + 20, maxPaint);
            canvas.drawText(min, canvas.getWidth() / 2, yoffset + rect.height() + 20, minPaint);
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
    }

}
