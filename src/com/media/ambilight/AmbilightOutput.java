package com.media.ambilight;

import com.media.ambilight.ble.BluetoothLeService;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("NewApi")
public interface AmbilightOutput {

    public void create();
    public void destroy();
    public void update(final int[] colors);
    public void update(final int[][] colors);
    public void setColor(int color);
    public void setBrightness(int value);
    public void setOnOff(boolean off);
    public boolean isSupported();

    public class ViewsOutput implements AmbilightOutput {
        private FrameLayout mFrame;
        private Context mContext;

        private LinearLayout mLeft;
        private LinearLayout mRight;

        private ArrayList<AmbilightView> mViews;

        public static boolean isSupportedDebug() {
            return true;
        }

        @SuppressLint("RtlHardcoded")
        public ViewsOutput(Context context, View rootView) {
            mFrame = (FrameLayout) rootView;
            mContext = context;

            if (mFrame != null) {
                FrameLayout.LayoutParams paramsLeft = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
                paramsLeft.gravity = Gravity.LEFT;
                FrameLayout.LayoutParams paramsRight = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
                paramsRight.gravity = Gravity.RIGHT;

                mLeft = new LinearLayout(context);
                mLeft.setOrientation(LinearLayout.VERTICAL);
                mLeft.setLayoutParams(paramsLeft);

                mRight = new LinearLayout(context);
                mRight.setOrientation(LinearLayout.VERTICAL);
                mRight.setLayoutParams(paramsRight);

                mFrame.post(new Runnable() {
                    @Override
                    public void run() {
                        mFrame.addView(mLeft);
                        mFrame.addView(mRight);
                    }
                });
            }
        }

        @Override
        public void create() {
            mViews = new ArrayList<AmbilightView>(AmbilightSettings.AMBILIGHT_TOTAL_CHANNELS);

            mFrame.post(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < AmbilightSettings.AMBILIGHT_SUB_CHANNELS; i++) {
                        mViews.add(new AmbilightView(mContext, mLeft));
                        //mViews.add(new AmbilightView(mContext, mLeft));
                        //mViews.add(new AmbilightView(mContext, mLeft));

                        //mViews.add(new AmbilightView(mContext, mRight));
                        //mViews.add(new AmbilightView(mContext, mRight));
                        mViews.add(new AmbilightView(mContext, mRight));
                    }
                }
            });
        }

        @Override
        public void update(final int[] colors) {
            if (mFrame == null) {
                return;
            }
            mFrame.post(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < colors.length; i++) {
                        mViews.get(i).update(colors[i]);
                    }
                }
            });
        }

        @Override
        public void update(final int[][] colors) {
            if (mFrame == null) {
                return;
            }
            mFrame.post(new Runnable() {
                @Override
                public void run() {
                    int l = colors[0].length;
                    for (int i = 0; i < colors.length; i++) {
                        for (int j = 0; j < l; j++) {
                            mViews.get(i * l + j).update(colors[i][j]);
                        }
                    }
                }
            });
        }

        @Override
        public boolean isSupported() {
            return true;
        }

        private class AmbilightView {
            public static final int DEFAULT_VIEW_WIDTH = 100;
            public static final int DEFAULT_VIEW_HEIGHT = 100;

            private View mView;
            private GradientDrawable mGradient;

            public AmbilightView(Context context, LinearLayout view) {
                if (view == null) {
                    return;
                }

                mView = new View(context);
                mView.setLayoutParams(new FrameLayout.LayoutParams(DEFAULT_VIEW_WIDTH, DEFAULT_VIEW_HEIGHT));

                mGradient = new GradientDrawable();
                mGradient.setShape(GradientDrawable.RECTANGLE);
                mGradient.setStroke(8, Color.BLACK);

                view.addView(mView);
            }

            public void update(int color) {
                mGradient.setColor(color);
                mView.setBackground(mGradient);
            }
        }

        @Override
        public void setColor(int color) {
            if (mFrame == null) {
                return;
            }

            final int c = color;
            mFrame.post(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < AmbilightSettings.AMBILIGHT_TOTAL_CHANNELS; ++i) {
                        mViews.get(i).update(c);
                    }
                }
            });
        }

        @Override
        public void destroy() {
        }

        @Override
        public void setBrightness(int value) {
        }

        @Override
        public void setOnOff(boolean off) {
        }
    }

    @SuppressLint("InlinedApi")
    public class BluetoothLEDOutput implements AmbilightOutput {
        private static final String TAG = BluetoothLEDOutput.class.getSimpleName();

        private static final float WHITE_BALANCE_R = 1.0f;
        private static final float WHITE_BALANCE_G = 0.9f;
        private static final float WHITE_BALANCE_B = 0.75f;

        private static final int COMMAND_COLOR = 1;
        private static final int COMMAND_BRIGHTNESS = 2;
        private static final int COMMAND_OFF = 3;

        private boolean mUseAsService = false;

        private Handler mHandler = new Handler();
        private int mConnectedCounter = 0;
        private boolean mSupported = false;
        private Context mContext;
        //private int mFPSCounter = 0;
        //private LEDAnimation mLEDAnimation = new LEDAnimation();

        private int[] mLastColors = new int[AmbilightSettings.AMBILIGHT_TOTAL_CHANNELS];
        private int mLastBrightness = 0;
        private boolean mLastOff = true;
        private int mLastCommand = COMMAND_COLOR;

        private BluetoothLeService mBluetoothLeService = null;

        private ExecutorService mService = Executors.newSingleThreadExecutor();
        //private BlockingQueue<byte[]> mServiceQueue = new LinkedBlockingQueue<>(2);

        private BluetoothLeService.Callback mBluetoothLeServiceCallback = new BluetoothLeService.Callback() {
            @Override
            public void OnConnected() {
                mConnectedCounter++;

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setLastValue();
                    }
                }, 1000);

                Log.v(TAG, "OnConnected count=" + mConnectedCounter);
            }

            @Override
            public void OnDisconnected() {
                mConnectedCounter--;
                if (mConnectedCounter < 0) {
                    mConnectedCounter = 0;
                }
                Log.v(TAG, "OnDisconnected count=" + mConnectedCounter);
            }
        };

        private final ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder service) {
                start(((BluetoothLeService.LocalBinder) service).getService());
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                stop();
            }
        };

        public static boolean isSupportedBLE(Context context) {
            return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        }

        public BluetoothLEDOutput(Context context, Callable<Boolean> preparedCallback) {
            if (context == null || !isSupportedBLE(context)) {
                return;
            }
            mContext = context;

            final BluetoothManager bluetoothManager =
                    (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager.getAdapter() == null) {
                Log.v(TAG, "Bluetoouth disconnected!");
                return;
            }

            mSupported = true;

            //mUseAsService = preparedCallback == null;

            if (mUseAsService) {
                Intent gattServiceIntent = new Intent(mContext, BluetoothLeService.class);
                mContext.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
            } else {
                start(new BluetoothLeService(mContext, mBluetoothLeServiceCallback, preparedCallback));
            }
        }

        @Override
        public void create() {

        }

        @Override
        public void update(final int[] colors) {
            if (isEmpty()) {
                return;
            }

            //if (mLEDAnimation.update(colors)) {
            //    return;
            //}

            for (int i = 0; i < colors.length; ++i) {
                int color = colors[i];

                if (!AmbilightSettings.AMBILIGHT_TEST) {
                    if (mLastColors[i] != color) {
                        setLEDColor(i, color);
                        mLastColors[i] = color;
                    }
                } else {
                    setLEDColor(i, color);
                }
            }
            mLastCommand = COMMAND_COLOR;
        }

        @Override
        public void update(final int[][] colors) {
        }

        @Override
        public void destroy() {
            if (mUseAsService) {
                if (mServiceConnection != null) {
                    mContext.unbindService(mServiceConnection);
                }
            } else {
                stop();
            }
        }

        @Override
        public void setColor(int color) {
            if (!isEmpty()) {
                setLEDColorAll(color);
            }

            for (int i = 0; i < mLastColors.length; ++i) {
                mLastColors[i] = color;
            }
            mLastCommand = COMMAND_COLOR;
        }

        @Override
        public void setBrightness(int value) {
            if (!isEmpty()) {
                setLEDBrightnessAll(value);
            }
            mLastBrightness = value;
            mLastCommand = COMMAND_BRIGHTNESS;
        }

        @Override
        public void setOnOff(boolean off) {
            if (!isEmpty()) {
                if (off) {
                    setLEDBrightnessAll(0);
                }
                setLEDOnOffAll(off);
            }
            mLastOff = off;
            mLastCommand = COMMAND_OFF;
        }

        @Override
        public boolean isSupported() {
            return mSupported;
        }

        protected void start(BluetoothLeService service) {
            Log.v(TAG, "start!");
            if (service == null) {
                return;
            }

            mBluetoothLeService = service;
            mBluetoothLeService.initialize();
        }

        protected void stop() {
            Log.v(TAG, "stop!");
            if (mBluetoothLeService != null) {
                mBluetoothLeService.close();
                mBluetoothLeService = null;
            }
        }

        protected void setLastValue() {
            if (mLastCommand == COMMAND_BRIGHTNESS) {
                Log.v(TAG, "setLastValue last brighness=" + mLastBrightness);
                setBrightness(mLastBrightness);
            } else if (mLastCommand == COMMAND_OFF) {
                Log.v(TAG, "setLastValue last off=" + mLastOff);
                setOnOff(mLastOff);
            } else if (mLastCommand == COMMAND_COLOR) {
                Log.v(TAG, "setLastValue last color=" + mLastColors[0]);
                setColor(mLastColors[0]);
            }
        }

        protected void setLEDColor(int index, int color) {
            if (isEmpty()) {
                return;
            }

            int r = correctWhiteBalanceR(color);
            int g = correctWhiteBalanceG(color);
            int b = correctWhiteBalanceB(color);
            setLEDColor(index, r, g, b);
        }

        protected void setLEDColorAll(int color) {
            if (isEmpty()) {
                return;
            }

            int r = correctWhiteBalanceR(color);
            int g = correctWhiteBalanceG(color);
            int b = correctWhiteBalanceB(color);
            setLEDColorAll(r, g, b);
        }

        protected void setLEDColor(int index, int r, int g, int b) {
            if (isEmpty()) {
                return;
            }

            String data = "$COL," + r + "," + g +"," + b + "?";
            //Log.v(TAG, "setLEDColor: " + index + " : " + data);
            sendData(index, data.getBytes());
        }

        protected void setLEDBrightnessAll(int value) {
            Log.v(TAG, "setLEDBrightnessAll: " + value);
            for (int i = 0; i < mConnectedCounter; ++i) {
                setLEDBrightness(i, value);
            }
        }

        private void setLEDOnOffAll(boolean off) {
            Log.v(TAG, "setLEDOnOffAll: " + off);
            for (int i = 0; i < mConnectedCounter; ++i) {
                setLEDOnOff(i, off);
            }
        }

        private void setLEDColorAll(int r, int g, int b) {
            for (int i = 0; i < mConnectedCounter; ++i) {
                setLEDColor(i, r, g, b);
            }
        }

        private void setLEDBrightness(int index, int value) {
            if (isEmpty()) {
                return;
            }

            if (value > 100) {
                value = 100;
            }
            if (value < 0) {
                value = 0;
            }
            String data = "$BRI," + value + "," + value + "?";
            sendData(index, data.getBytes());
        }

        private void setLEDOnOff(int index, boolean off) {
            if (isEmpty()) {
                return;
            }

            String data = off ? "$GOF?" : "$GON?";
            Log.v(TAG, "setLEDOnOff: " + data);
            sendData(index, data.getBytes());
        }

        private int correctWhiteBalanceR(int color) {
            return (int) (Color.red(color) * WHITE_BALANCE_R);
        }

        private int correctWhiteBalanceG(int color) {
            return (int) (Color.green(color) * WHITE_BALANCE_G);
        }

        private int correctWhiteBalanceB(int color) {
            return (int) (Color.blue(color) * WHITE_BALANCE_B);
        }

        private boolean isEmpty() {
            return mBluetoothLeService == null || mConnectedCounter == 0;
        }

        private void sendData(final int index, final byte[] data) {
            mService.submit(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLeService.sendCharacteristic(index, data);
                }
            });
        }
/*
        private class LEDAnimation {
            private static final int LED_UPDATE_FPS = 5;
            private static final long STANDBY_DELAY_MS = 30 * 1000;
            private static final int STANDBY_COLOR_MAX = 25;

            private long mStandByStartTimeMs = 0;
            private int mAnimationCounter = 0;

            public boolean isSupported() {
                return false;
            }

            public boolean update(final int[] colors) {
                if (!isSupported()) {
                    return false;
                }

                boolean black = true;
                for (int color : colors) {
                    if (Color.BLACK != color) {
                        black = false;
                    }
                }

                if (!black) {
                    mStandByStartTimeMs = 0;
                    return false;
                }

                long now = System.currentTimeMillis();
                if (mStandByStartTimeMs == 0) {
                    mStandByStartTimeMs = now;
                    return false;
                }

                if (mStandByStartTimeMs > 0 && now - mStandByStartTimeMs > STANDBY_DELAY_MS) {
                    startAnimation();
                    return true;
                }
                return false;
            }

            protected void startAnimation() {
                if (mFPSCounter++ < LED_UPDATE_FPS) {
                    return;
                }

                mFPSCounter = 0;

                int r = STANDBY_COLOR_MAX;
                int g = STANDBY_COLOR_MAX;
                int b = STANDBY_COLOR_MAX;

                if (mAnimationCounter++ < STANDBY_COLOR_MAX) {
                    r = Math.min(mAnimationCounter, STANDBY_COLOR_MAX);
                    g = STANDBY_COLOR_MAX;
                    b = 0;
                } else if (mAnimationCounter < 2 * STANDBY_COLOR_MAX) {
                    r = STANDBY_COLOR_MAX;
                    g = Math.min(mAnimationCounter, STANDBY_COLOR_MAX);
                    b = 0;
                } else if (mAnimationCounter < 3 * STANDBY_COLOR_MAX) {
                    r = 0;
                    g = STANDBY_COLOR_MAX;
                    b = Math.min(mAnimationCounter - 2 * STANDBY_COLOR_MAX, STANDBY_COLOR_MAX);
                } else {
                    mAnimationCounter = 0;
                    return;
                }
                setLEDColorAll(r, g, b);
            }
        }*/
    }
}
