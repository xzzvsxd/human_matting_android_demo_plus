package com.paddle.demo.matting.visual;

import android.graphics.Bitmap;

import com.baidu.paddle.lite.Tensor;

public class Visualize {
    public Bitmap draw(Bitmap inputImage, Tensor outputTensor, Bitmap bg) {
        float[] output = outputTensor.getFloatData();
        long[] outputShape = outputTensor.shape();
        int width = (int) outputShape[3];
        int height = (int) outputShape[2];

        Bitmap mALPHA_IMAGE = floatArrayToBitmap(output, width, height);
        Bitmap alpha = Bitmap.createScaledBitmap(mALPHA_IMAGE, inputImage.getWidth(), inputImage.getHeight(), true);
        Bitmap bgImg = Bitmap.createScaledBitmap(bg, inputImage.getWidth(), inputImage.getHeight(), true);

        return synthetizeBitmap(inputImage, bgImg, alpha, true);
    }

    private Bitmap floatArrayToBitmap(float[] floatArray, int width, int height) {
        float max = Float.NEGATIVE_INFINITY;
        float min = Float.POSITIVE_INFINITY;
        for (float value : floatArray) {
            if (value > max) max = value;
            if (value < min) min = value;
        }
        float delta = max - min + 1e-8f;

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        for (int i = 0; i < floatArray.length; i++) {
            int grayValue = (int) (((floatArray[i] - min) / delta) * 255);
            pixels[i] = 0xFF000000 | (grayValue << 16) | (grayValue << 8) | grayValue;
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height);
        return bmp;
    }

    private Bitmap synthetizeBitmap(Bitmap front, Bitmap background, Bitmap alpha, boolean ignoreBackground) {
        int width = front.getWidth();
        int height = front.getHeight();
        int[] frontPixels = new int[width * height];
        int[] alphaPixels = new int[width * height];
        front.getPixels(frontPixels, 0, width, 0, 0, width, height);
        alpha.getPixels(alphaPixels, 0, width, 0, 0, width, height);

        int[] backgroundPixels = new int[width * height];
        if (!ignoreBackground) {
            background.getPixels(backgroundPixels, 0, width, 0, 0, width, height);
        }

        int[] resultPixels = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            int frontPixel = frontPixels[i];
            float alphaValue = (alphaPixels[i] & 0xFF) / 255f;

            if (ignoreBackground) {
                // 使用透明背景
                int r = (int) (((frontPixel >> 16) & 0xFF) * alphaValue);
                int g = (int) (((frontPixel >> 8) & 0xFF) * alphaValue);
                int b = (int) ((frontPixel & 0xFF) * alphaValue);
                int a = (int) (255 * alphaValue);

                resultPixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
            } else {
                // 使用提供的背景
                int backgroundPixel = backgroundPixels[i];

                int r = (int) (((frontPixel >> 16) & 0xFF) * alphaValue + ((backgroundPixel >> 16) & 0xFF) * (1 - alphaValue));
                int g = (int) (((frontPixel >> 8) & 0xFF) * alphaValue + ((backgroundPixel >> 8) & 0xFF) * (1 - alphaValue));
                int b = (int) ((frontPixel & 0xFF) * alphaValue + (backgroundPixel & 0xFF) * (1 - alphaValue));

                resultPixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(resultPixels, 0, width, 0, 0, width, height);
        return result;
    }
}