package com.apiratehat.realtimeblurryview;

import android.graphics.Bitmap;
import android.util.Log;

public class BlurryUtil {
    private static final String TAG = "BlurryUtil";

    public static void blurryWithAverage(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[][] fromPixels = new int[width + 80][height + 80]; //在 3X3 的矩阵中+2 补边缘数值
        int[][] toPixels = new int[width][height];

        for (int i = 0; i < 40; i++) {
            for (int j = 0; j < 40; j++) {
                fromPixels[i][j] = bitmap.getPixel(0, 0);
                fromPixels[i][height + 40+j] = bitmap.getPixel(0, height-1);
                fromPixels[width +40+i][j] = bitmap.getPixel(width - 1, 0);
                fromPixels[width + 40+i][height + 40+j] = bitmap.getPixel(width - 1, height - 1);
            }
        }


        for (int j = 40; j < height + 80 - 40; j++) {
            for (int i = 0; i < 40; i++) {
                fromPixels[i][j] = bitmap.getPixel(0, j - 40);
                fromPixels[width  +40+i][j] = bitmap.getPixel(width - 1, j - 40);
            }
        }
        for (int i = 40; i < width + 80 - 40; i++) {
            for (int j = 0; j < 40; j++) {
                fromPixels[i][j] = bitmap.getPixel(i - 40, 0);
                fromPixels[i][height + 40 +j] = bitmap.getPixel(i - 40, height - 1);
            }
        }
        for (int i = 40; i < width + 80 - 40; i++) {
            for (int j = 40; j < height + 80 - 40; j++) {
                fromPixels[i][j] = bitmap.getPixel(i - 40, j - 40);
            }
        }
        for (int i = 40; i < width + 80 - 40; i++) {
            for (int j = 40; j <  height + 80 - 40; j++) {
                toPixels[i-40][j-40] = caculate(fromPixels,i,j);
            }
        }
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                bitmap.setPixel(i,j,toPixels[i][j]);
            }
        }
    }

    private static int caculate(int[][] pixel,int x,int y){
        long sum = 0;
        for (int i = -40; i < 40; i++) {
            for (int j = -40; j < 40; j++) {
                sum += pixel[x+i][y+j];
            }
        }
        return (int) (sum*1.0/6400);
    }

}
