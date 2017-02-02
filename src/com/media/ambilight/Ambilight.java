package com.media.ambilight;

import android.content.Context;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.concurrent.Callable;

public class Ambilight implements AmbilightGLRenderer.AmbilightGLRendererCallback, SurfaceHolder.Callback,
    SurfaceGL.SurfaceGLCallback {
    private static final String TAG = Ambilight.class.getSimpleName();

    private static Ambilight sInstance = null;

    private SurfaceGL mSurface;

    private ArrayList<AmbilightOutput> mOutputs = new ArrayList<AmbilightOutput>();
    private AmbilightLogic mLogic = new AmbilightLogic();
    private SurfaceHolder.Callback mCallback;
    private View mRootView = null;

    private boolean mEnableOutput = true;
    private boolean mEnableOutputExternal = false;

    private Runnable mDestroyer = new Runnable() {
        @Override
        public void run() {
            if (mSurface != null) {
                mSurface.destroy();
                mSurface = null;
            }
        }
    };

    public static Runnable getInstance(View view, Context context,
            SurfaceHolder.Callback callback, Callable<Boolean> preparedCallback, Boolean debug) {

        sInstance = new Ambilight();

        if (AmbilightSettings.AMBILIGHT_VIDEO_PLAYER_SURFACE) {
            sInstance.mSurface = SurfaceGL.init(context, sInstance, sInstance);
        }
        //sInstance.mContext = context;
        sInstance.mCallback = callback;
        sInstance.mRootView = view;

        if (sInstance.mRootView != null && sInstance.mSurface != null && sInstance.mRootView instanceof FrameLayout) {
            try {
                ((FrameLayout) sInstance.mRootView).addView(sInstance.mSurface.getView());
            } catch (Exception ignored) {
            }
        }

        if (debug && AmbilightOutput.ViewsOutput.isSupportedDebug()) {
            sInstance.mOutputs.add(new AmbilightOutput.ViewsOutput(context, view));
        }

        if (AmbilightOutput.BluetoothLEDOutput.isSupportedBLE(context)) {
            sInstance.mOutputs.add(new AmbilightOutput.BluetoothLEDOutput(context, preparedCallback));
        }

        return sInstance.mDestroyer;
    }

    public static Ambilight getInstance() {
        return sInstance;
    }

    public static Surface getSurface() {
        return sInstance != null && sInstance.mSurface != null ? sInstance.mSurface.getSurface() : null;
    }

    public static View getView() {
        return sInstance != null && sInstance.mSurface != null ? sInstance.mSurface.getView() : null;
    }

    public static void setState(Boolean enableOutput, Boolean enableOutputExternal) {
        Log.i(TAG, "setState " + enableOutput + " " + enableOutputExternal);
        Ambilight self = getInstance();
        if (self != null) {
            boolean currentOff = !self.mEnableOutput && !self.mEnableOutputExternal;

            self.mEnableOutput = enableOutput;
            self.mEnableOutputExternal = enableOutputExternal;

            boolean nextOff = !self.mEnableOutput && !self.mEnableOutputExternal;
            if (currentOff != nextOff) {
                for (AmbilightOutput out : self.mOutputs) {
                    out.setOnOff(nextOff);
                }
            }
        }
    }

    public static void setColor(Integer color) {
        Ambilight self = getInstance();
        if (self != null && self.mEnableOutputExternal) {
            for (AmbilightOutput out : self.mOutputs) {
                out.setColor(color);
            }
        }
    }

    public static void setBrightness(Integer brightness) {
        Ambilight self = getInstance();
        if (self != null && self.mEnableOutputExternal) {
            for (AmbilightOutput out : self.mOutputs) {
                out.setBrightness(brightness);
            }
        }
    }

    @Override
    public void onGLSurfaceChanged(int width, int height) {
        Log.i(TAG, "onGLSurfaceChanged");
        if (mSurface != null) {
            mSurface.setExternalRenderer(new AmbilightGLRenderer(width, height, mSurface.getTextureID(), this));
        }
    }

    @Override
    public void onAmbilightCreated() {
        Log.i(TAG, "onAmbilightCreated");
        for (AmbilightOutput out : mOutputs) {
            out.create();
        }
    }

    @Override
    public void onAmbilightColors(final int[] colors) {
        if (!mEnableOutput) {
            return;
        }

        int[] newColors = mLogic.update(colors);
        for (AmbilightOutput out : mOutputs) {
            out.update(newColors);
        }
    }

    @Override
    public void onAmbilightColorsVariants(int[][] colors) {
        for (AmbilightOutput out : mOutputs) {
            out.update(colors);
        }
    }

    @Override
    public void onAmbilightDestroyed() {
        Log.i(TAG, "onAmbilightDestroyed");
        for (AmbilightOutput out : mOutputs) {
            out.destroy();
        }
        mOutputs.clear();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "surfaceChanged");
        if (mCallback != null) {
            mCallback.surfaceChanged(holder, format, width, height);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated");
        if (mCallback != null) {
            mCallback.surfaceCreated(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");
        if (mCallback != null) {
            mCallback.surfaceDestroyed(holder);
        }
    }
}
