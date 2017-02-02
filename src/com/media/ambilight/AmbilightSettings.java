package com.media.ambilight;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class AmbilightSettings {
    public static final int AMBILIGHT_PRESET_DOUBLE_ONE = 0;
    public static final int AMBILIGHT_PRESET_DOUBLE_TWO = 1;
    public static final int AMBILIGHT_PRESET_QUAD_ONE = 2;
    public static final int AMBILIGHT_PRESET_QUAD_TWO = 3;

    public static final int AMBILIGHT_BORDER_WIDTH = 2;

    public static final int AMBILIGHT_UPDATE_FPS = 5;
    public static final int AMBILIGHT_BUFFERED_TIME_MS = 400;

    public static final boolean AMBILIGHT_VIDEO_PLAYER_SURFACE = true;
    public static final boolean AMBILIGHT_TEST = false;
    public static final boolean AMBILIGHT_USE_DOMINANT_COLORS = false;

    public static final int AMBILIGHT_TEST_COLORS[] = { Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.CYAN, Color.MAGENTA };
    public static ArrayList<String> AMBILIGHT_MACS = new ArrayList<String>();

    public static int AMBILIGHT_PRESET = AMBILIGHT_PRESET_DOUBLE_TWO;
    public static int AMBILIGHT_CHANNELS = 2;
    public static int AMBILIGHT_SUB_CHANNELS = 1;
    public static int AMBILIGHT_TOTAL_CHANNELS = AMBILIGHT_CHANNELS * AMBILIGHT_SUB_CHANNELS;

    @SuppressLint("SdCardPath")
    private static final String FILE_PATH_SETTINGS = "/mnt/sdcard/ambilight.txt";

    static {
        final String leftBottom = "08:7C:BE:2E:EF:82";
        final String leftTop = "08:7C:BE:2F:A2:49";
        final String rightTop = "08:7C:BE:2E:EF:F3";
        final String rightBottom = "08:7C:BE:2F:A1:D5";

        readSettings();

        if (AMBILIGHT_PRESET == AMBILIGHT_PRESET_DOUBLE_ONE
            || AMBILIGHT_PRESET == AMBILIGHT_PRESET_DOUBLE_TWO) {
            AMBILIGHT_CHANNELS = 2;
            AMBILIGHT_SUB_CHANNELS = 1;
            AMBILIGHT_TOTAL_CHANNELS = AMBILIGHT_CHANNELS * AMBILIGHT_SUB_CHANNELS;

            if (AMBILIGHT_PRESET == AMBILIGHT_PRESET_DOUBLE_ONE) {
                AMBILIGHT_MACS.add(leftBottom);
                AMBILIGHT_MACS.add(rightTop);
            } else {
                AMBILIGHT_MACS.add(leftTop);
                AMBILIGHT_MACS.add(rightBottom);
            }
        } else if (AMBILIGHT_PRESET == AMBILIGHT_PRESET_QUAD_ONE
                || AMBILIGHT_PRESET == AMBILIGHT_PRESET_QUAD_TWO) {

            AMBILIGHT_CHANNELS = 2;
            AMBILIGHT_SUB_CHANNELS = 2;
            AMBILIGHT_TOTAL_CHANNELS = AMBILIGHT_CHANNELS * AMBILIGHT_SUB_CHANNELS;

            if (AMBILIGHT_PRESET == AMBILIGHT_PRESET_QUAD_ONE) {
                AMBILIGHT_MACS.add(leftBottom);
                AMBILIGHT_MACS.add(leftTop);
                AMBILIGHT_MACS.add(rightTop);
                AMBILIGHT_MACS.add(rightBottom);
            } else {
                AMBILIGHT_MACS.add(leftBottom);
                AMBILIGHT_MACS.add(rightBottom);
                AMBILIGHT_MACS.add(leftTop);
                AMBILIGHT_MACS.add(rightTop);
            }
        }
    }

    private static void readSettings() {
        BufferedReader br = null;
        try {
            Log.i("AmbilightSettings", "readSettings from " + FILE_PATH_SETTINGS);
            br = new BufferedReader(new FileReader(FILE_PATH_SETTINGS));
            if (br != null) {
                String line = br.readLine();
                if (!TextUtils.isEmpty(line)) {
                    int preset = Integer.parseInt(line);
                    Log.i("AmbilightSettings", "new preset = " + preset);
                    AMBILIGHT_PRESET = preset;
                }
            }
        } catch (Exception e) {
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                }
                br = null;
            }
        }
    }
}
