package com.media.ambilight;

import android.graphics.Color;

import java.util.ArrayList;

public class AmbilightLogic {
    private static final int UPDATE_FPS = AmbilightSettings.AMBILIGHT_UPDATE_FPS;
    private static final int SET_TIME_MS = AmbilightSettings.AMBILIGHT_BUFFERED_TIME_MS;
    private static final int GET_TIME_MS = SET_TIME_MS / UPDATE_FPS;
    private static final int COLOR_FLUCTUATE_PERCENTS = 10;

    private long mTimestampMs = 0;
    private long mTimestampColorMs = 0;
    private int mColorIndex = 0;

    private ArrayList<ColorMetric> mMetrics = new ArrayList<ColorMetric>(AmbilightSettings.AMBILIGHT_TOTAL_CHANNELS);
    private int[] mColors = new int[AmbilightSettings.AMBILIGHT_TOTAL_CHANNELS];

    public AmbilightLogic() {
        for (int i = 0; i < AmbilightSettings.AMBILIGHT_TOTAL_CHANNELS; ++i) {
            mMetrics.add(new ColorMetric());
        }
    }

    public int[] update(final int[] colors) {
        long timestampMs = System.currentTimeMillis();
        if (mTimestampMs == 0 || timestampMs - mTimestampMs >= SET_TIME_MS) {
            mTimestampColorMs = mTimestampMs = timestampMs;
            mColorIndex = 0;

            for (int i = 0; i < colors.length; ++i) {
                mMetrics.get(i).update(colors[i]);
            }
        }

        for (int i = 0; i < colors.length; ++i) {
            mColors[i] = getColor(i);
        }

        return mColors;
    }

    public int getColor(int index) {
        long timestampMs = System.currentTimeMillis();
        if (timestampMs - mTimestampColorMs >= GET_TIME_MS) {
            mTimestampColorMs = timestampMs;
            mColorIndex++;
        }
        return mMetrics.get(index).getColor(mColorIndex);
    }

    private class ColorMetric {
        private int mColorPrevious = 0;
        private int mColorCurrent = 0;

        ArrayList<Integer> mGradient = new ArrayList<Integer>(UPDATE_FPS);

        public ColorMetric() {
            initilaize();
        }

        public void update(int color) {
            mColorPrevious = mColorCurrent;
            mColorCurrent = color;

            int r = Color.red(mColorPrevious);
            int g = Color.green(mColorPrevious);
            int b = Color.blue(mColorPrevious);

            int rd = Color.red(mColorCurrent) - r;
            int gd = Color.green(mColorCurrent) - g;
            int bd = Color.blue(mColorCurrent) - b;

            if (rd < COLOR_FLUCTUATE_PERCENTS) {
                rd = 0;
            }

            if (gd < COLOR_FLUCTUATE_PERCENTS) {
                gd = 0;
            }

            if (bd < COLOR_FLUCTUATE_PERCENTS) {
                bd = 0;
            }

            for (int i = 0; i < UPDATE_FPS; ++i) {
                int ri = r + (rd * i / (UPDATE_FPS - 1));
                int gi = g + (gd * i / (UPDATE_FPS - 1));
                int bi = b + (bd * i / (UPDATE_FPS - 1));

                mGradient.set(i, Color.rgb(ri, gi, bi));
            }
        }

        public int getColor(int index) {
            return index < UPDATE_FPS ? mGradient.get(index) : mGradient.get(mGradient.size() - 1);
        }

        private void initilaize() {
            for (int i = 0; i < UPDATE_FPS; ++i) {
                mGradient.add(i, 0);
            }
        }
    }
}
