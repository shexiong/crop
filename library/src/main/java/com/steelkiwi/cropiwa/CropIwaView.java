package com.steelkiwi.cropiwa;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import com.steelkiwi.cropiwa.compressor.Compressor;
import com.steelkiwi.cropiwa.config.ConfigChangeListener;
import com.steelkiwi.cropiwa.config.CropIwaImageViewConfig;
import com.steelkiwi.cropiwa.config.CropIwaOverlayConfig;
import com.steelkiwi.cropiwa.config.CropIwaSaveConfig;
import com.steelkiwi.cropiwa.image.CropArea;
import com.steelkiwi.cropiwa.shape.CropIwaShapeMask;
import com.steelkiwi.cropiwa.util.CropIwaUtils;
import com.steelkiwi.cropiwa.util.ThreadPoolFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by yarolegovich on 02.02.2017.
 */
public class CropIwaView extends FrameLayout {

    private CropIwaImageView imageView;
    private CropIwaOverlayView overlayView;

    private CropIwaOverlayConfig overlayConfig;
    private CropIwaImageViewConfig imageConfig;

    private CropIwaImageView.GestureProcessor gestureDetector;

    private ErrorListener errorListener;
    private CropSaveCompleteListener cropSaveCompleteListener;

    public CropIwaView(Context context) {
        super(context);
        init(null);
    }

    public CropIwaView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public CropIwaView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CropIwaView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        imageConfig = CropIwaImageViewConfig.createFromAttributes(getContext(), attrs);
        initImageView();

        overlayConfig = CropIwaOverlayConfig.createFromAttributes(getContext(), attrs);
        overlayConfig.addConfigChangeListener(new ReInitOverlayOnResizeModeChange());
        initOverlayView();
    }

    private void initImageView() {
        if (imageConfig == null) {
            throw new IllegalStateException("imageConfig must be initialized before calling this method");
        }
        imageView = new CropIwaImageView(getContext(), imageConfig);
        imageView.setBackgroundColor(Color.BLACK);
        gestureDetector = imageView.getImageTransformGestureDetector();
        addView(imageView);
    }

    private void initOverlayView() {
        if (imageView == null || overlayConfig == null) {
            throw new IllegalStateException("imageView and overlayConfig must be initialized before calling this method");
        }
        overlayView = overlayConfig.isDynamicCrop() ?
                new CropIwaDynamicOverlayView(getContext(), overlayConfig) :
                new CropIwaOverlayView(getContext(), overlayConfig);
        overlayView.setNewBoundsListener(imageView);
        imageView.setImagePositionedListener(overlayView);
        addView(overlayView);
    }

    @Override
    @SuppressWarnings("RedundantIfStatement")
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        //I think this "redundant" if statements improve code readability
        try {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            gestureDetector.onDown(ev);
            return false;
        }
        if (overlayView.isResizing() || overlayView.isDraggingCropArea()) {
            return false;
        }
        return true;
        } catch (IllegalArgumentException e) {
            //e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            gestureDetector.onTouchEvent(event);
            return super.onTouchEvent(event);
        } catch (IllegalArgumentException e) {
            //e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        imageView.measure(widthMeasureSpec, heightMeasureSpec);
        overlayView.measure(
                imageView.getMeasuredWidthAndState(),
                imageView.getMeasuredHeightAndState());
        imageView.notifyImagePositioned();
        setMeasuredDimension(
                imageView.getMeasuredWidthAndState(),
                imageView.getMeasuredHeightAndState());
    }

    @Override
    public void invalidate() {
        imageView.invalidate();
        overlayView.invalidate();
    }

    public CropIwaOverlayConfig configureOverlay() {
        return overlayConfig;
    }

    public CropIwaImageViewConfig configureImage() {
        return imageConfig;
    }

    public void setImage(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
        overlayView.setDrawOverlay(true);
    }

    public void crop(final CropIwaSaveConfig saveConfig, final Bitmap bitmap, final File fileDirectory, final File newFile){
        final CropArea cropArea = CropArea.create(
                imageView.getImageRect(),
                imageView.getImageRect(),
                overlayView.getCropRect());
        final CropIwaShapeMask mask = overlayConfig.getCropShape().getMask();

        ThreadPoolFactory.getInstance().startRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    if (bitmap == null) {
                        return;
                    }
                    Bitmap cropped = cropArea.applyCropTo(bitmap);
                    cropped = mask.applyMaskTo(cropped);

                    File oldFile = new File(fileDirectory, System.currentTimeMillis() + ".jpg");
                    FileOutputStream os = new FileOutputStream(oldFile);
                    cropped.compress(saveConfig.getCompressFormat(), saveConfig.getQuality(), os);
                    CropIwaUtils.closeSilently(os);

                    new Compressor()
                            .setMaxWidth(1080)
                            .setMaxHeight(640)
                            .setQuality(85)
                            .setCompressFormat(Bitmap.CompressFormat.JPEG)
                            .compressToFile(oldFile, newFile.getAbsolutePath());

                    bitmap.recycle();
                    cropped.recycle();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if(CropIwaView.this.cropSaveCompleteListener != null){
                        CropIwaView.this.cropSaveCompleteListener.onCroppedRegionSaved(saveConfig.getDstUri());
                    }
                }
            }
        });
    }

    public void setErrorListener(ErrorListener errorListener) {
        this.errorListener = errorListener;
    }

    public void setCropSaveCompleteListener(CropSaveCompleteListener cropSaveCompleteListener) {
        this.cropSaveCompleteListener = cropSaveCompleteListener;
    }

    private class ReInitOverlayOnResizeModeChange implements ConfigChangeListener {

        @Override
        public void onConfigChanged() {
            if (shouldReInit()) {
                overlayConfig.removeConfigChangeListener(overlayView);
                boolean shouldDrawOverlay = overlayView.isDrawn();
                removeView(overlayView);

                initOverlayView();
                overlayView.setDrawOverlay(shouldDrawOverlay);

                invalidate();
            }
        }

        private boolean shouldReInit() {
            return overlayConfig.isDynamicCrop() != (overlayView instanceof CropIwaDynamicOverlayView);
        }
    }

    public interface CropSaveCompleteListener {
        void onCroppedRegionSaved(Uri fileUri);
    }

    public interface ErrorListener {
        void onError(Throwable e);
    }
}
