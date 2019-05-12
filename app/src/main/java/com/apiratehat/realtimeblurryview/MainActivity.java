package com.apiratehat.realtimeblurryview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private TextView mRect;

    private static final String TAG = "MainActivity";

    Bitmap bitmap ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rect);
        mRect = findViewById(R.id.rect_view);
        Drawable drawable = mRect.getBackground();
        bitmap = drawableToBitmap(drawable);
       new Thread(new Runnable() {
           @Override
           public void run() {
               BlurryUtil.blurryWithAverage(bitmap);
               runOnUiThread(new Runnable() {
                   @Override
                   public void run() {
                       ((ImageView)findViewById(R.id.imv)).setImageBitmap(bitmap);
                   }
               });
           }
       }).start();

    }

    private void showPixel(Bitmap bitmap){
        int cX = bitmap.getWidth()/2;
        int cY = bitmap.getHeight()/2;

        int pixel = bitmap.getPixel(2, 2);// ARGB
        int red = Color.red(pixel);
        int green = Color.green(pixel);
        int blue = Color.blue(pixel);
        int alpha = Color.alpha(pixel);
        Log.e(TAG,  "\nA:"+Integer.toHexString(alpha) + "\nR:"+Integer.toHexString(red)
                +"\nG:"+ Integer.toHexString(green) + "\nB:"+Integer.toHexString(blue));
        pixel = bitmap.getPixel(cX, cY);// ARGB
        red = Color.red(pixel);
        green = Color.green(pixel);
        blue = Color.blue(pixel);
        alpha = Color.alpha(pixel);

        Log.e(TAG,  "\nA:"+Integer.toHexString(alpha) + "\nR:"+Integer.toHexString(red)
                +"\nG:"+ Integer.toHexString(green) + "\nB:"+Integer.toHexString(blue));
    }

    public Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(),
                drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }

}
