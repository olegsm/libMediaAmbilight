package com.media.ambilight;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

@SuppressLint("NewApi")
public class SurfaceGL {

    private static final String TAG = SurfaceGL.class.getSimpleName();

    private Handler mUIHandler = null;

    private GLSurfaceView mView;
    private GLRender mRenderer;
    private SurfaceHolder.Callback mCallback;
    private int mWidth = 0;
    private int mHeight = 0;

    private Runnable mCreatedCallback = new Runnable() {
        @Override
        public void run() {
            if (mCallback != null) {
                mCallback.surfaceCreated(mView.getHolder());
            }
        }
    };

    private Runnable mDestroyedCallback = new Runnable() {
        @Override
        public void run() {
            if (mCallback != null) {
                mCallback.surfaceDestroyed(mView.getHolder());
            }
        }
    };

    private Runnable mChangedCallback = new Runnable() {
        @Override
        public void run() {
            if (mCallback != null) {
                mCallback.surfaceChanged(mView.getHolder(), PixelFormat.RGB_565, mWidth, mHeight);
            }
        }
    };

    // call this in OpenGL thread only
    private SurfaceGLCallback mGLCallback = null;

    public static final SurfaceGL init(Context context, SurfaceHolder.Callback callback, SurfaceGLCallback glCallback) {
        return new SurfaceGL(context, callback, glCallback);
    }

    public SurfaceGL(Context context, SurfaceHolder.Callback callback, SurfaceGLCallback glCallback) {
        if (!isOpenGLES20Supported(context)) {
            return;
        }

        mUIHandler = new Handler(Looper.getMainLooper());

        mCallback = callback;
        mGLCallback = glCallback;

        mView = new GLSurfaceView(context);

        mView.setEGLContextClientVersion(2);
        if (VideoConfig.SET_CHOOSER) {
            // GLSurfaceView uses RGB_5_6_5 by default.
            mView.setEGLConfigChooser(8, 8, 8, 8, 8, 8);
        }
        mRenderer = new GLRender();
        mView.setRenderer(mRenderer);
    }

    public Surface getSurface() {
        Log.v(TAG, "getSurface");
        return mRenderer != null ? mRenderer.getSurface() : null;
    }

    public int getFPS() {
        return mRenderer != null ? mRenderer.getFPS() : 0;
    }

    public void setExternalRenderer(GLRenderer renderer) {
        Log.v(TAG, "setExternalRenderer");
        if (mRenderer != null) {
            mRenderer.setExternalRenderer(renderer);
        }
    }

    public GLRenderer getExternalRenderer() {
        Log.v(TAG, "getExternalRenderer");
        return mRenderer != null ? mRenderer.getExternalRenderer() : null;
    }

    public int getTextureID() {
        return mRenderer != null ? mRenderer.getTextureID() : 0;
    }

    public void destroy() {
        Log.v(TAG, "destroy");
        if (mRenderer != null) {
            mRenderer.release();
            mRenderer = null;
        }
    }

    public View getView() {
        Log.v(TAG, "getView");
        return mView;
    }

    private boolean isOpenGLES20Supported(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo info = am.getDeviceConfigurationInfo();
        return (info.reqGlEsVersion >= 0x20000);
    }

    private void runInUIThread(Runnable runnable) {
        if (runnable != null && mUIHandler != null) {
            mUIHandler.post(runnable);
        }
    }

    public class VideoConfig {
        // So far, glReadPixels only supports two (format, type) combinations
        // GL_RGB GL_UNSIGNED_SHORT_5_6_5 16 bits per pixel (default)
        // GL_RGBA GL_UNSIGNED_BYTE 32 bits per pixel
        public static final int PIXEL_FORMAT = GLES20.GL_RGBA;
        public static final int BYTES_PER_PIXEL = 4;
        public static final boolean SET_CHOOSER = true;
    }

    public static class GLShaders {
        private static final String TAG = "GLShaders";

        public static final String VERTEX_SHADER =
                  "uniform mat4 uMVPMatrix;\n"
                + "uniform mat4 uSTMatrix;\n"
                + "attribute vec4 aPosition;\n"
                + "attribute vec4 aTextureCoord;\n"
                + "varying vec2 vTextureCoord;\n"
                + "void main() {\n"
                + "  gl_Position = uMVPMatrix * aPosition;\n"
                + "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n"
                + "}\n";

        public static final String FRAGMENT_SHADER =
                  "#extension GL_OES_EGL_image_external : require\n"
                + "precision mediump float;\n"
                + "varying vec2 vTextureCoord;\n"
                + "uniform samplerExternalOES sTexture;\n"
                + "void main() {\n"
                + "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
                + "}\n";

        public static int loadShader(int shaderType, String source) {
            int shader = GLES20.glCreateShader(shaderType);
            if (shader != 0) {
                GLES20.glShaderSource(shader, source);
                GLES20.glCompileShader(shader);
                int[] compiled = new int[1];
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
                if (compiled[0] == 0) {
                    Log.e(TAG, "Could not compile shader " + shaderType + ":");
                    Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                    GLES20.glDeleteShader(shader);
                    shader = 0;
                }
            }
            return shader;
        }

        public static int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }

            int program = GLES20.glCreateProgram();
            if (program != 0) {
                GLES20.glAttachShader(program, vertexShader);
                checkGlError("glAttachShader");
                GLES20.glAttachShader(program, pixelShader);
                checkGlError("glAttachShader");
                GLES20.glLinkProgram(program);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] != GLES20.GL_TRUE) {
                    Log.e(TAG, "Could not link program: " + GLES20.glGetProgramInfoLog(program));
                    GLES20.glDeleteProgram(program);
                    program = 0;
                }
            }
            return program;
        }

        public static void checkGlError(String op) {
            int error = GLES20.GL_NO_ERROR;
            while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
                Log.e(TAG, op + ": glError " + error);
                throw new RuntimeException(op + ": glError " + error);
            }
        }
    }

    public static class FPSCounter {
        public static final int SURFACE_VIDEO_FPS = 24;

        private int mFPSCounter = 0;
        private int mFrameCount = 0;
        private long mTimestampMs = 0;
        private int mFPS = -1;

        public synchronized int getFPS() {
            return mFPS;
        }

        public synchronized int getFrameCount() {
            return mFrameCount;
        }

        public void newFrame(long timestampMs) {
            if (mTimestampMs == 0 || timestampMs < mTimestampMs) {
                mTimestampMs = timestampMs;
                return;
            }

            if (mFrameCount++ == SURFACE_VIDEO_FPS) {
                mFrameCount = 0;
            }
            if (mFPSCounter++ >= Integer.MAX_VALUE) {
                mFPSCounter = 0;
            }

            long diffMs = timestampMs - mTimestampMs;
            if (diffMs >= 1000) {
                mTimestampMs = timestampMs;
                synchronized (this) {
                    mFPS = mFPSCounter;
                }
                mFPSCounter = 0;
            }
        }
    }

    public static class PixelReader {
        private static final String TAG = "PixelReader";

        public static final int PIXELS_WIDTH = 16;
        public static final int PIXELS_HEIGHT = 16;

        protected ByteBuffer mBuffer;

        public PixelReader() {
            if (PIXELS_WIDTH > 0 && PIXELS_HEIGHT > 0) {
                mBuffer = ByteBuffer.allocateDirect(PIXELS_WIDTH * PIXELS_HEIGHT * VideoConfig.BYTES_PER_PIXEL);
                mBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }
        }

        public int getWidth() {
            return PIXELS_WIDTH;
        }

        public int getHeight() {
            return PIXELS_HEIGHT;
        }

        public void fill() {
            if (mBuffer != null) {
                mBuffer.rewind();
                GLES20.glReadPixels(0, 0, PIXELS_WIDTH, PIXELS_HEIGHT, VideoConfig.PIXEL_FORMAT, GLES20.GL_UNSIGNED_BYTE, mBuffer);
            }
        }

        public void dump() {
            if (mBuffer != null) {
                String msg = new String("|");
                byte[] pixelData = mBuffer.array();
                for (int i = 0; i < pixelData.length; ++i) {
                    msg += (String.valueOf(pixelData[i] & 0xFF) + ((i + 1) % 4 == 0 ? "|" : " "));
                }
                Log.v(TAG, "PixelFrame: " + pixelData.length + " " + msg);
            }
        }

        public void dumpFirstLastColor() {
            byte[] pixelData = mBuffer.array();

            int rSum = pixelData[0 + 0] & 0xFF;
            int gSum = pixelData[0 + 1] & 0xFF;
            int bSum = pixelData[0 + 2] & 0xFF;
            dumpColor(rSum, gSum, bSum);

            rSum = pixelData[pixelData.length - 4 + 0] & 0xFF;
            gSum = pixelData[pixelData.length - 4 + 1] & 0xFF;
            bSum = pixelData[pixelData.length - 4 + 2] & 0xFF;
            dumpColor(rSum, gSum, bSum);
        }

        public void dumpColor(int r, int g, int b) {
            Log.v(TAG, " " + r  + ":" + g + ":" + b + ": color:" + Color.rgb(r, g, b));
        }

        public void dumpToFile() {
            File f = new File("/mnt/sdcard", "image.png");
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Bitmap bitmap = Bitmap.createBitmap(PIXELS_WIDTH, PIXELS_HEIGHT, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(mBuffer);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(CompressFormat.PNG, 0, bos);
            byte[] bitmapdata = bos.toByteArray();

            FileOutputStream fos;
            try {
                fos = new FileOutputStream(f);
                fos.write(bitmapdata);
                fos.flush();
                fos.close();
            } catch (Exception ignored) {
            }
        }

        public int[] getColors() {
            return null;
        }

        public int[][] getVariantColors() {
            return null;
        }
    }

    public static class GLRenderer {
        public static final String TAG = "GLRenderer";

        protected static final int FLOAT_SIZE_BYTES = 4;
        protected static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
        protected static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
        protected static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
        // Magic key GLES11Ext.GL_TEXTURE_EXTERNAL_OES needed for rendering MediaPlayer
        protected static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;
        protected static final int TARGET_TEXTURE_ID = GL_TEXTURE_EXTERNAL_OES;

        protected final static float[] DEFAULT_TRIANGLE_VERTEXES_DATA = {
                // X, Y, Z, U, V
                -1.0f,  -1.0f,  0, 0.f, 0.f,
                1.0f,   -1.0f,  0, 1.f, 0.f,
                -1.0f,  1.0f,   0, 0.f, 1.f,
                1.0f,   1.0f,   0, 1.f, 1.f,
        };

        protected float[] mTriangleVerticesData;

        protected int[] mTextures;

        protected FloatBuffer mTriangleVertices;

        protected float[] mMVPMatrix = new float[16];
        protected float[] mSTMatrix = new float[16];

        protected int mProgram;
        protected int mTextureID;
        protected int muMVPMatrixHandle;
        protected int muSTMatrixHandle;
        protected int maPositionHandle;
        protected int maTextureHandle;

        protected PixelReader mPixelReader = null;
        protected AtomicInteger mEnableRendering = new AtomicInteger(1);

        public GLRenderer() {
            init(DEFAULT_TRIANGLE_VERTEXES_DATA);
        }

        public void init(float[] defaultTriangleVerticesData) {
            mTriangleVerticesData = defaultTriangleVerticesData;
            mTriangleVertices = ByteBuffer.allocateDirect(
                    mTriangleVerticesData.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();

            mTriangleVertices.put(mTriangleVerticesData).position(0);
            Matrix.setIdentityM(mSTMatrix, 0);
        }

        public void create(String fragmentSource) {
            mTextures = new int[1];
            GLES20.glGenTextures(1, mTextures, 0);

            mTextureID = mTextures[0];

            GLES20.glBindTexture(TARGET_TEXTURE_ID, mTextureID);
            checkGlError("glBindTexture mTextureID " + TARGET_TEXTURE_ID);

            make(fragmentSource);

            // Can't do mipmapping with mediaplayer source
            GLES20.glTexParameterf(TARGET_TEXTURE_ID, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameterf(TARGET_TEXTURE_ID, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            // Clamp to edge is the only option
            GLES20.glTexParameteri(TARGET_TEXTURE_ID, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(TARGET_TEXTURE_ID, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            checkGlError("glTexParameteri mTextureID");
        }

        public void release() {
            if (mTextures != null) {
                GLES20.glDeleteTextures(1, mTextures, 0);
                checkGlError("glDeleteTextures");
            }
        }

        public void draw() {
            drawSetup(TARGET_TEXTURE_ID, mTextureID);
            drawSetupMatrix();
            drawImpl();
        }

        public void setEnableRendering(boolean enable) {
            mEnableRendering.set(enable ? 1 : 0);
        }

        public void make(String fragmentSource) {
            mProgram = GLShaders.createProgram(GLShaders.VERTEX_SHADER, fragmentSource);
            if (mProgram == 0) {
                return;
            }
            maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
            checkGlError("glGetAttribLocation aPosition");
            if (maPositionHandle == -1) {
                throw new RuntimeException("Could not get attrib location for aPosition");
            }
            maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
            checkGlError("glGetAttribLocation aTextureCoord");
            if (maTextureHandle == -1) {
                throw new RuntimeException("Could not get attrib location for aTextureCoord");
            }

            muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
            checkGlError("glGetUniformLocation uMVPMatrix");
            if (muMVPMatrixHandle == -1) {
                throw new RuntimeException("Could not get attrib location for uMVPMatrix");
            }

            muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
            checkGlError("glGetUniformLocation uSTMatrix");
            if (muSTMatrixHandle == -1) {
                throw new RuntimeException("Could not get attrib location for uSTMatrix");
            }
        }

        protected void drawSetup(int targetId, int textureId) {
            // Initial clear.
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
            checkGlError("glClear");

            // Load the program, which is the basics rules to draw the vertexes
            // and textures.
            GLES20.glUseProgram(mProgram);
            checkGlError("glUseProgram");

            // Activate the texture.
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(targetId, textureId);
            checkGlError("glBindTexture");

            // Load the vertexes coordinates. Simple here since it only draw a
            // rectangle
            // that fits the whole screen.
            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
            GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT,
                    false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
                    mTriangleVertices);
            checkGlError("glVertexAttribPointer maPosition");
            GLES20.glEnableVertexAttribArray(maPositionHandle);
            checkGlError("glEnableVertexAttribArray maPositionHandle");

            // Load the texture coordinates, which is essentially a rectangle
            // that fits
            // the whole video frame.
            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
            //FIXME!!!
            GLES20.glVertexAttribPointer(maTextureHandle, 3, GLES20.GL_FLOAT,
                    false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
                    mTriangleVertices);
            checkGlError("glVertexAttribPointer maTextureHandle");

            GLES20.glEnableVertexAttribArray(maTextureHandle);
            checkGlError("glEnableVertexAttribArray maTextureHandle");
        }

        protected void drawSetupMatrix() {
            // Set up the GL matrices.
            Matrix.setIdentityM(mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
            checkGlError("glUniformMatrix4fv");
        }

        protected void drawImpl() {
            // Draw a rectangle and render the video frame as a texture on it.
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            checkGlError("glDrawArrays");

            fillPixels();
        }

        protected void fillPixels() {
            if (mPixelReader != null) {
                mPixelReader.fill();
            }
        }

        protected void checkGlError(String op) {
            int error = GLES20.GL_NO_ERROR;
            while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
                String msg = op + ": glError " + error + " : " + GLU.gluErrorString(error);
                //Log.e(TAG, msg);
                throw new RuntimeException(msg);
            }
        }
    }

    private class GLRender implements GLSurfaceView.Renderer,
            SurfaceTexture.OnFrameAvailableListener {
        private static final String TAG = "GLRender";

        private GLRenderer mRendererVideo = new GLRenderer();
        private GLRenderer mRendererExternal = null;
        private FPSCounter mFPS = new FPSCounter();

        private SurfaceTexture mSurfaceTexture;
        private Surface mSurface;
        private boolean mUpdateSurface = false;

        private boolean DEBUG = false;

        @Override
        public void onDrawFrame(GL10 g) {
            boolean isNewFrame = false;
            synchronized (this) {
                if (mUpdateSurface) {
                    isNewFrame = true;

                    mSurfaceTexture.updateTexImage();
                    mSurfaceTexture.getTransformMatrix(mRendererVideo.mSTMatrix);

                    mUpdateSurface = false;
                }
            }

            if (mRendererExternal != null) {
                mRendererExternal.draw();
            }

            mRendererVideo.draw();

            if (DEBUG && isNewFrame) {
                mFPS.newFrame(System.currentTimeMillis());
                Log.v(TAG, "FPS: " + mFPS.getFPS());
            }
        }

        public int getFPS() {
            return mFPS.getFPS();
        }

        public int getTextureID() {
            return mRendererVideo.mTextureID;
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            if (DEBUG) {
                Log.d(TAG, "onSurfaceChanged: " + width + "x" + height);
            }

            mWidth = width;
            mHeight = height;

            if (mGLCallback != null) {
                mGLCallback.onGLSurfaceChanged(width, height);
            }

            runInUIThread(mChangedCallback);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            if (DEBUG) {
                Log.d(TAG, "onSurfaceCreated");
            }

            mRendererVideo.create(GLShaders.FRAGMENT_SHADER);

            mSurfaceTexture = new SurfaceTexture(getTextureID());
            mSurfaceTexture.setOnFrameAvailableListener(this);

            synchronized (this) {
                mUpdateSurface = false;
            }

            runInUIThread(mCreatedCallback);
        }

        @Override
        public void onFrameAvailable(SurfaceTexture texture) {
            synchronized (this) {
                mUpdateSurface = true;
            }
        }

        public Surface getSurface() {
            if (mSurface == null && mSurfaceTexture != null) {
                mSurface = new Surface(mSurfaceTexture);
            }
            return mSurface;
        }

        public void release() {
            if (mSurface != null) {
                if (mRendererExternal != null) {
                    mRendererExternal.release();
                }

                if (mRendererVideo != null) {
                    mRendererVideo.release();
                }
                mSurface.release();
            }
            mSurface = null;
            mSurfaceTexture = null;

            runInUIThread(mDestroyedCallback);
        }

        public void setExternalRenderer(GLRenderer renderer) {
            mRendererExternal = renderer;
        }

        public GLRenderer getExternalRenderer() {
            return mRendererExternal;
        }
    }

    public interface SurfaceGLCallback {
        public void onGLSurfaceChanged(int width, int height);
    }
}
