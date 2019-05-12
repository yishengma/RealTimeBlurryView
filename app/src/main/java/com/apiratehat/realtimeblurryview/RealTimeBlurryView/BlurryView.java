package com.apiratehat.realtimeblurryview.RealTimeBlurryView;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;

import com.apiratehat.realtimeblurryview.R;

/**
 * Created by ethanysma on 2019-04-28.
 *
 * 注意：因 RenderScript 版本限制，只支持 API >= 17
 * 对 API = 16 只做透明化出处理，若想支持 API 16 可添加
 * android.support.v8.renderscript 兼容包。
 */
public class BlurryView extends View {
    private static final String TAG = "BlurryView";
    private static final float DEFAULT_SCALE_FACTOR = 4;
    private static final int DEFAULT_OVERLAY_COLOR = 0x99000000; //api 17 及以上
    private static final int DEFAULT_OVERLAY_COLOR_API16 = 0xCC000000;// api 16
    private static final float DEFAULT_BLURRY_RADIUS = 10;
    private static final float MAX_BLURRY_RADIUS = 25;
    private static final float MIN_BLURRY_RADIUS = 0;

    private float mScaleFactor;// 缩放因子(缩小后对模糊效果影响小但能提高性能)
    private int mOverlayColor; //覆盖颜色
    private float mBlurryRadius; //模糊半径（模糊程度）

    private Bitmap mSourceBitmap; //源 bitmap
    private Bitmap mBlurredBitmap; //模糊后 的 bitmap

    private RenderScript mRenderScript; //高性能计算脚本类
    private ScriptIntrinsicBlur mScriptIntrinsicBlur;//高斯模糊计算的脚本类

    //内核计算分配的内存
    private Allocation mBlurInputAllocation; //输入分配
    private Allocation mBlurOutputAllocation; //输出分配

    private static int RENDERING_COUNT;//限制只有一个 View 模糊，不能叠加
    private boolean mIsRendering; //是否正在渲染
    private Paint mPaint;//背景色的画笔
    private Rect mRectSrc;
    private Rect mRectDst;
    private View mDecorView;// Activity 的 decorView
    private boolean mDirty;
    private Canvas mBlurringCanvas;


    public BlurryView(Context context) {
        this(context,null);
    }

    public BlurryView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs,0);
    }

    public BlurryView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context,attrs);
    }

    public void initView(Context context, AttributeSet attrs) {
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){
           TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BlurryView);
           mBlurryRadius = a.getDimension(R.styleable.BlurryView_blurry_radius,
                   TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_BLURRY_RADIUS, context.getResources().getDisplayMetrics()));
           mScaleFactor = a.getFloat(R.styleable.BlurryView_blurry_factor, DEFAULT_SCALE_FACTOR);
           mOverlayColor = a.getColor(R.styleable.BlurryView_blurry_color, DEFAULT_OVERLAY_COLOR);
           a.recycle();
           mPaint = new Paint();
           mRectSrc = new Rect();
           mRectDst = new Rect();
       }else {
           setBackgroundColor(DEFAULT_OVERLAY_COLOR_API16);
       }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private ViewTreeObserver.OnPreDrawListener mOnPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)){
                return true;
            }
            final int[] locations = new int[2];
            Bitmap oldBmp = mBlurredBitmap;
            View decor = mDecorView;
            if (decor != null && isShown() && prepare()) {
                boolean redrawBitmap = mBlurredBitmap != oldBmp;//判断是否和上一个视图是否发生改变
                decor.getLocationOnScreen(locations);//获取左上角相对于整个屏幕的位置
                int x = -locations[0];
                int y = -locations[1];
                getLocationOnScreen(locations);
                x += locations[0];
                y += locations[1];
                mSourceBitmap.eraseColor(mOverlayColor & 0xFFFFFF);//大小不变 ，重复使用，设置为透明
                int rc = mBlurringCanvas.save();//保存状态
                mIsRendering = true;
                mBlurringCanvas.scale(1.0f * mSourceBitmap.getWidth() / getWidth(), 1.0f * mSourceBitmap.getHeight() / getHeight());
                mBlurringCanvas.translate(-x, -y);
                //获取 decorView 当前的 画布，就可以获取到需要模糊的 View 的 bitmap
                if (decor.getBackground() != null) {
                    decor.getBackground().draw(mBlurringCanvas);
                }
                decor.draw(mBlurringCanvas);
                mBlurringCanvas.restoreToCount(rc);//恢复状态
                mIsRendering = false;
                //对 Bitmap 进行模糊处理
                blur(mSourceBitmap, mBlurredBitmap);
                //重新绘制
                if (redrawBitmap) {
                    invalidate();
                }
            }
            return true;

        }
    };

    private View getActivityDecorView() {
        Context context = getContext();
        if (context instanceof Activity) {
            return ((Activity) context).getWindow().getDecorView();
        } else {
            return null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mDecorView = getActivityDecorView();
            if (mDecorView != null) {
                mDecorView.getViewTreeObserver().addOnPreDrawListener(mOnPreDrawListener);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){
            if (mDecorView != null) {
                mDecorView.getViewTreeObserver().removeOnPreDrawListener(mOnPreDrawListener);
            }
            release();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void draw(Canvas canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){
            if (!mIsRendering) {
                super.draw(canvas);
            }
        }else {
            super.draw(canvas);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){
            //将模糊后的bitmap 进行显示
            drawBlurredBitmap(canvas, mBlurredBitmap, mOverlayColor);
        }
    }

    /**
     * 计算前检查是否变量合法，设置参数，缩放等等
     *
     * @return 是否合法
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private boolean prepare() {
        if (mBlurryRadius <= TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MIN_BLURRY_RADIUS, getResources().getDisplayMetrics())
                || mBlurryRadius > TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MAX_BLURRY_RADIUS, getResources().getDisplayMetrics())) {
            release();
            return false;
        }
        float scaleFactor = mScaleFactor;
        float radius = mBlurryRadius / scaleFactor;
        if (mDirty || mRenderScript == null) {
            if (mRenderScript == null) {
                //初始化计算脚本和高斯计算脚本类
                mRenderScript = RenderScript.create(getContext());
                mScriptIntrinsicBlur = ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4(mRenderScript));
            }
            mScriptIntrinsicBlur.setRadius(radius);//模糊半径，范围 (0.0f,25.0f]
            mDirty = false;
        }
        int width = getWidth();
        int height = getHeight();
        int scaledWidth = Math.max(1, (int) (width / scaleFactor));
        int scaledHeight = Math.max(1, (int) (height / scaleFactor));
        //对 bitmap 进行初始化，并创建画布，分配需要的内核区域
        if (mBlurringCanvas == null || mBlurredBitmap == null
                || mBlurredBitmap.getWidth() != scaledWidth
                || mBlurredBitmap.getHeight() != scaledHeight) {
            releaseBitmap();
            mSourceBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            mBlurredBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            if (mBlurredBitmap == null || mSourceBitmap == null) {
                return false;
            }
            mBlurringCanvas = new Canvas(mSourceBitmap);
            mBlurInputAllocation = Allocation.createFromBitmap(mRenderScript, mSourceBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
            mBlurOutputAllocation = Allocation.createTyped(mRenderScript, mBlurInputAllocation.getType());
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void blur(Bitmap sourceBitmap, Bitmap blurredBitmap) {
        mBlurInputAllocation.copyFrom(sourceBitmap); //把  源 bitmap 拷贝到 内核内存中
        mScriptIntrinsicBlur.setInput(mBlurInputAllocation); //设置高斯计算的输入内存
        mScriptIntrinsicBlur.forEach(mBlurOutputAllocation); //进行计算，并将计算结果输出到输出内存中
        mBlurOutputAllocation.copyTo(blurredBitmap); //将输出内存的的 bitmap 拷贝给 blurredBitmap 即模糊后的 bitMap

    }

    private void drawBlurredBitmap(Canvas canvas, Bitmap blurredBitmap, int overlayColor) {
        //截取需要显示的模糊区域的大小
        if (blurredBitmap != null) {
            mRectSrc.right = blurredBitmap.getWidth();
            mRectSrc.bottom = blurredBitmap.getHeight();
            mRectDst.right = getWidth();
            mRectDst.bottom = getHeight();
            canvas.drawBitmap(blurredBitmap, mRectSrc, mRectDst, null);
        }
        //再对模糊后的画布做一个覆盖色处理，默认为黑色透明
        mPaint.setColor(overlayColor);
        canvas.drawRect(mRectDst, mPaint);
    }

    private void release() {
        releaseBitmap();
        releaseScript();
    }

    private void releaseBitmap() {
        if (mBlurInputAllocation != null) {
            mBlurInputAllocation.destroy();
            mBlurInputAllocation = null;
        }
        if (mBlurOutputAllocation != null) {
            mBlurOutputAllocation.destroy();
            mBlurOutputAllocation = null;
        }
        if (mSourceBitmap != null) {
            mSourceBitmap.recycle();
            mSourceBitmap = null;
        }
        if (mBlurredBitmap != null) {
            mBlurredBitmap.recycle();
            mBlurredBitmap = null;
        }
    }

    private void releaseScript() {
        if (mRenderScript != null) {
            mRenderScript.destroy();
            mRenderScript = null;
        }
        if (mScriptIntrinsicBlur != null) {
            mScriptIntrinsicBlur.destroy();
            mScriptIntrinsicBlur = null;
        }
    }

}
