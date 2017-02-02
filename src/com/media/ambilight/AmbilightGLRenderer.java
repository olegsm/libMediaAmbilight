package com.media.ambilight;

import com.media.ambilight.SurfaceGL.GLRenderer;
import com.media.ambilight.SurfaceGL.GLShaders;
import com.media.ambilight.SurfaceGL.PixelReader;
import com.media.ambilight.SurfaceGL.VideoConfig;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public class AmbilightGLRenderer extends GLRenderer {

    public static final String VERTEX_SHADER =
            "attribute vec4 aPosition;\n"
          + "attribute vec4 aTextureCoord;\n"
          + "varying vec2 vTextureCoord;\n"
          + "void main() {\n"
          + "  gl_Position = aPosition;\n"
          + "  vTextureCoord = aTextureCoord.xy;\n"
          + "}\n";

    public static final String FRAGMENT_AMBILIGHT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n"
          + "precision mediump float;\n"
          + "precision lowp int;\n"
          + "varying vec2 vTextureCoord;\n"
          + "uniform samplerExternalOES sTexture;\n"
          + "void main() {\n"
          + "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
          + "}\n";

    public interface AmbilightGLRendererCallback {
        public void onAmbilightColorsVariants(final int[][] colors);
        public void onAmbilightColors(final int[] colors);
        public void onAmbilightCreated();
        public void onAmbilightDestroyed();
    }

    private int[] mFrameBuffer;
    private IntBuffer mTextureBuffer;
    private int mTextureIDExternal = 0;
    private AmbilightGLRendererCallback mCallback;

    public AmbilightGLRenderer(int width, int height, int textureIDExternal, AmbilightGLRendererCallback callback) {
        if (width == 0 || height == 0) {
            return;
        }

        final float lenX = (1.0f / width) * PixelReader.PIXELS_WIDTH * 2;
        final float lenY = (1.0f / height) * PixelReader.PIXELS_HEIGHT * 2;

        final float[] defaultTriangleVerticesDataFB = {
                -1.0f,         -1.0f,         0, 0.f, 0.f,
                -1.0f + lenX,  -1.0f,         0, 1.f, 0.f,
                -1.0f,         -1.0f + lenY,  0, 0.f, 1.f,
                -1.0f + lenX,  -1.0f + lenY,  0, 1.f, 1.f,
        };

        init(defaultTriangleVerticesDataFB);

        mTextureIDExternal = textureIDExternal;
        mCallback = callback;
        mPixelReader = new AmbilightPixelReader();

        create(AmbilightGLRenderer.FRAGMENT_AMBILIGHT_SHADER);
    }

    @Override
    public void create(String fragmentSource) {
        mTextures = new int[1];
        GLES20.glGenTextures(1, mTextures, 0);
        checkGlError("glGenTextures");

        mTextureID = mTextures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID);
        checkGlError("glBindTexture mTextureID " + GLES20.GL_TEXTURE_2D);

        int h = mPixelReader.getHeight();
        int w = mPixelReader.getWidth();

        mTextureBuffer = ByteBuffer.allocateDirect(w * h * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asIntBuffer();

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, w, h,
                0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, mTextureBuffer);
        checkGlError("glTexImage2D");

        mFrameBuffer = new int[1];
        GLES20.glGenFramebuffers(1, mFrameBuffer, 0);
        checkGlError("glGenFramebuffers");

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
        checkGlError("glBindFramebuffer");

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mTextureID, 0);
        checkGlError("glFramebufferTexture2D");

        make(fragmentSource);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        checkGlError("glBindFramebuffer");

        if (mCallback != null) {
            mCallback.onAmbilightCreated();
        }
    }

    @Override
    public void release() {
        if (mFrameBuffer != null) {
            GLES20.glDeleteRenderbuffers(1, mFrameBuffer, 0);
            checkGlError("glDeleteRenderbuffers");
        }
        super.release();

        if (mCallback != null) {
            mCallback.onAmbilightDestroyed();
        }
    }

    @Override
    public void draw() {
        if (mEnableRendering.get() == 0) {
            return;
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            return;
        }

        drawSetup(TARGET_TEXTURE_ID, mTextureIDExternal);
        drawImpl();

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        checkGlError("glBindFramebuffer0");

        if (mCallback != null) {
            mCallback.onAmbilightColors(mPixelReader.getColors());
            //mCallback.onAmbilightColorsVariants(mPixelReader.getVariantsColors());
        }
    }

    @Override
    public void make(String fragmentSource) {
        mProgram = GLShaders.createProgram(VERTEX_SHADER, fragmentSource);
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
    }

    private class AmbilightPixelReader extends PixelReader {
        private int[] mAvergeColors = new int[AmbilightSettings.AMBILIGHT_TOTAL_CHANNELS];
        private int[][] mVariantColors = new int[AmbilightSettings.AMBILIGHT_TOTAL_CHANNELS][];

        @Override
        public void fill() {
            super.fill();

            int border = AmbilightSettings.AMBILIGHT_BORDER_WIDTH;
            if (AmbilightSettings.AMBILIGHT_CHANNELS == 2 && AmbilightSettings.AMBILIGHT_SUB_CHANNELS == 1) {
                if (AmbilightSettings.AMBILIGHT_TEST) {
                    mAvergeColors[0] = AmbilightSettings.AMBILIGHT_TEST_COLORS[0];
                    mAvergeColors[1] = AmbilightSettings.AMBILIGHT_TEST_COLORS[1];
                } else {
                    mAvergeColors[0] = computeColor(0, 0, border, PIXELS_HEIGHT);
                    mAvergeColors[1] = computeColor(PIXELS_WIDTH - border, 0, border, PIXELS_HEIGHT);
                    //mVariantColors[0] = computeColors(0, 0, border, PIXELS_HEIGHT);
                    //mVariantColors[1] = computeColors(PIXELS_WIDTH - border, 0, border, PIXELS_HEIGHT);
                }
            } else if (AmbilightSettings.AMBILIGHT_CHANNELS == 2 && AmbilightSettings.AMBILIGHT_SUB_CHANNELS == 2) {
                if (AmbilightSettings.AMBILIGHT_TEST) {
                    mAvergeColors[0] = AmbilightSettings.AMBILIGHT_TEST_COLORS[0];
                    mAvergeColors[1] = AmbilightSettings.AMBILIGHT_TEST_COLORS[1];
                    mAvergeColors[2] = AmbilightSettings.AMBILIGHT_TEST_COLORS[2];
                    mAvergeColors[3] = AmbilightSettings.AMBILIGHT_TEST_COLORS[3];
                } else {
                    mAvergeColors[0] = computeColor(0, PIXELS_HEIGHT / 2, border, PIXELS_HEIGHT / 2);
                    mAvergeColors[1] = computeColor(0, 0, border, PIXELS_HEIGHT / 2);
                    mAvergeColors[2] = computeColor(PIXELS_WIDTH - border, 0, border, PIXELS_HEIGHT / 2);
                    mAvergeColors[3] = computeColor(PIXELS_WIDTH - border, PIXELS_HEIGHT / 2, border, PIXELS_HEIGHT / 2);
                }
            }
        }

        @Override
        public int[] getColors() {
            return mAvergeColors;
        }

        @Override
        public int[][] getVariantColors() {
            return mVariantColors;
        }

        protected int computeColor(int rectX, int rectY, int rectWidth, int rectHeight) {
            int bytesPerRow = PIXELS_WIDTH * VideoConfig.BYTES_PER_PIXEL;
            if (AmbilightSettings.AMBILIGHT_USE_DOMINANT_COLORS) {
                return AmbilightColorUtil.computeDominantColor(mBuffer.array(), rectX, rectY, rectWidth, rectHeight,
                        VideoConfig.BYTES_PER_PIXEL, bytesPerRow);
            }
            return AmbilightColorUtil.computeAverageGainedColors(mBuffer.array(), rectX, rectY, rectWidth, rectHeight,
                    VideoConfig.BYTES_PER_PIXEL, bytesPerRow);
        }

        protected int[] computeColors(int rectX, int rectY, int rectWidth, int rectHeight) {
            return AmbilightColorUtil.computeDominantColors(mBuffer.array(), rectX, rectY, rectWidth, rectHeight,
                    VideoConfig.BYTES_PER_PIXEL, PIXELS_WIDTH * VideoConfig.BYTES_PER_PIXEL);
        }
    }
}
