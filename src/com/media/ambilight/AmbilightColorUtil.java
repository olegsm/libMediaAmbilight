
package com.media.ambilight;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.v7.graphics.Palette;

public class AmbilightColorUtil {
    public static int[] generateRainbow(float saturation, float brightness, int size,
            boolean black, boolean white, boolean gray) {
        final int[] result = new int[size];

        int start = 0;
        if (black)
            result[start++] = Color.BLACK;
        if (white)
            result[start++] = Color.WHITE;
        if (gray)
            result[start++] = Color.GRAY;

        float[] hsv = new float[3];
        hsv[0] = 1f;
        hsv[1] = saturation;
        hsv[2] = brightness;

        for (int i = start; i < result.length; i++) {
            hsv[0] = 360 * (float) (i - start) / (size - start);
            result[i] = Color.HSVToColor(hsv);
        }
        return result;
    }

    static double getDistance(float[] c1, float[] c2, boolean compareThirdComponent) {
        float result = (float) (Math.pow(c1[0] / 360 - c2[0] / 360, 2d) + Math.pow(c1[1] - c2[1], 2d));

        if (compareThirdComponent)
            result += Math.pow(c1[2] - c2[2], 2d);

        return result;
    }

    public static int getDominantColor(int colors[], int binNumber) {
        final int[] baseColors = generateRainbow(1f, 1f, binNumber, false, true, true);

        final float[][] colorsHSV = new float[colors.length][];
        final float[][] baseColorsHSV = new float[binNumber][];

        for (int i = 0; i < colors.length; i++) {
            colorsHSV[i] = new float[3];
            Color.colorToHSV(colors[i], colorsHSV[i]);
        }
        for (int i = 0; i < baseColors.length; i++) {
            baseColorsHSV[i] = new float[3];
            Color.colorToHSV(baseColors[i], baseColorsHSV[i]);
        }

        final int[] bins = new int[binNumber];

        for (float[] colorHsv : colorsHSV) {
            double minDist = getDistance(colorHsv, baseColorsHSV[0], true);
            int minInd = 0;

            for (int ind = 1; ind < baseColorsHSV.length; ind++) {
                final double dist = getDistance(colorHsv, baseColorsHSV[ind], true);

                if (dist < minDist) {
                    minDist = dist;
                    minInd = ind;
                }
            }
            bins[minInd]++;
        }

        int max = bins[0];
        int maxInd = 0;

        for (int i = 1; i < bins.length; i++) {
            final int v = bins[i];

            if (v > max) {
                max = v;
                maxInd = i;
            }
        }
        return baseColors[maxInd];
    }

    public static int computeAverageQuadColor(byte[] pixelData, int rectX, int rectY, int rectWidth, int rectHeight,
            int bytesPerPixel, int bytesPerRow) {
        int rSum = 0;
        int gSum = 0;
        int bSum = 0;

        for (int y = 0; y < rectHeight; y++) {
            int rowDataOffset = bytesPerRow * (rectY + y) + bytesPerPixel * rectX;
            for (int x = 0; x < rectWidth; x += 1) {
                int pixelDataOffset = rowDataOffset + bytesPerPixel * x;

                rSum += Math.pow(pixelData[pixelDataOffset + 0] & 0xFF, 2);
                gSum += Math.pow(pixelData[pixelDataOffset + 1] & 0xFF, 2);
                bSum += Math.pow(pixelData[pixelDataOffset + 2] & 0xFF, 2);
            }
        }
        int numberOfPixels = rectWidth * rectHeight;

        int r = (int) Math.sqrt((rSum / numberOfPixels)) & 0xFF;
        int g = (int) Math.sqrt((gSum / numberOfPixels)) & 0xFF;
        int b = (int) Math.sqrt((bSum / numberOfPixels)) & 0xFF;

        return Color.rgb(r, g, b);
    }

    public static int computeAverageColor(byte[] pixelData, int rectX, int rectY, int rectWidth, int rectHeight,
            int bytesPerPixel, int bytesPerRow) {
        int rSum = 0;
        int gSum = 0;
        int bSum = 0;

        for (int y = 0; y < rectHeight; y++) {
            int rowDataOffset = bytesPerRow * (rectY + y) + bytesPerPixel * rectX;
            for (int x = 0; x < rectWidth; x += 1) {
                int pixelDataOffset = rowDataOffset + bytesPerPixel * x;
                rSum += pixelData[pixelDataOffset + 0] & 0xFF;
                gSum += pixelData[pixelDataOffset + 1] & 0xFF;
                bSum += pixelData[pixelDataOffset + 2] & 0xFF;
            }
        }
        int numberOfPixels = rectWidth * rectHeight;
        int r = (rSum / numberOfPixels) & 0xFF;
        int g = (gSum / numberOfPixels) & 0xFF;
        int b = (bSum / numberOfPixels) & 0xFF;

        return Color.rgb(r, g, b);
    }

    public static int computeAverageGainedColors(byte[] pixelData, int rectX, int rectY, int rectWidth, int rectHeight,
            int bytesPerPixel, int bytesPerRow) {

        int color = computeAverageColor(pixelData, rectX, rectY, rectWidth, rectHeight,
                bytesPerPixel, bytesPerRow);

        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        // don't gain grayed colors
        if (Math.abs(Math.abs(r - g) - Math.abs(g - b)) <= (int)(r * 0.01)) {
            return color;
        }

        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);

        hsv[1] *= 4.0f;

        if (hsv[2] > 0.1)
            hsv[2] *= 2.0f;

        if (hsv[1] > 1f)
            hsv[1] = 1f;
        if (hsv[2] > 1f)
            hsv[2] = 1f;

        return Color.HSVToColor(hsv);
    }

    public static int computeDominantColor(byte[] pixelData, int rectX, int rectY, int rectWidth, int rectHeight,
            int bytesPerPixel, int bytesPerRow) {

        int[] colors = getRGBColors(pixelData, rectX, rectY, rectWidth, rectHeight, bytesPerPixel, bytesPerRow);

        Bitmap b = Bitmap.createBitmap(colors, rectWidth, rectHeight, Bitmap.Config.ARGB_8888);
        Palette palette = Palette.from(b).generate();

        int color = Color.BLACK;

        Palette.Swatch sw = palette.getLightVibrantSwatch();
        int populataion = 0;
        if (sw != null) {
            int p = sw.getPopulation();
            if (p > populataion) {
                populataion = p;
                color = sw.getRgb();
            }
        }

        sw = palette.getVibrantSwatch();
        if (sw != null) {
            int p = sw.getPopulation();
            if (p > populataion) {
                populataion = p;
                color = sw.getRgb();
            }
        }

        sw = palette.getDarkVibrantSwatch();
        if (sw != null) {
            int p = sw.getPopulation();
            if (p > populataion) {
                populataion = p;
                color = sw.getRgb();
            }
        }

        sw = palette.getLightMutedSwatch();
        if (sw != null) {
            int p = sw.getPopulation() / 2;
            if (p > populataion) {
                populataion = p;
                color = sw.getRgb();
            }
        }

        if (color != Color.BLACK) {
            return color;
        }

        return computeAverageColor(pixelData, rectX, rectY, rectWidth, rectHeight,
            bytesPerPixel, bytesPerRow);
    }

    public static int[] computeDominantColors(byte[] pixelData, int rectX, int rectY, int rectWidth, int rectHeight,
            int bytesPerPixel, int bytesPerRow) {
        int[] colors = getRGBColors(pixelData, rectX, rectY, rectWidth, rectHeight, bytesPerPixel, bytesPerRow);

        Bitmap b = Bitmap.createBitmap(colors, rectWidth, rectHeight, Bitmap.Config.ARGB_8888);
        Palette palette = Palette.from(b).maximumColorCount(6).generate();

        int[] variants = new int[3];

        Palette.Swatch sw = palette.getLightVibrantSwatch();
        if (sw != null) {
            variants[0] = sw.getRgb();
        }

        sw = palette.getVibrantSwatch();
        if (sw != null) {
            variants[1] = sw.getRgb();
        }

        sw = palette.getDarkVibrantSwatch();
        if (sw != null) {
            variants[2] = sw.getRgb();
        }
        return variants;
    }

    private static int[] getRGBColors(byte[] pixelData, int rectX, int rectY, int rectWidth, int rectHeight,
            int bytesPerPixel, int bytesPerRow) {

        int[] colors = new int[rectHeight * rectWidth];
        for (int y = 0; y < rectHeight; y++) {
            int rowDataOffset = bytesPerRow * (rectY + y) + bytesPerPixel * rectX;
            for (int x = 0; x < rectWidth; x += 1) {
                int pixelDataOffset = rowDataOffset + bytesPerPixel * x;
                int r = pixelData[pixelDataOffset + 0] & 0xFF;
                int g = pixelData[pixelDataOffset + 1] & 0xFF;
                int b = pixelData[pixelDataOffset + 2] & 0xFF;
                colors[rectWidth * y + x] = Color.rgb(r, g, b);
            }
        }
        return colors;
    }

}
