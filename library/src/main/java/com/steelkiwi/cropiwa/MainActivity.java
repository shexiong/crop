package com.steelkiwi.cropiwa;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.steelkiwi.cropiwa.config.CropIwaSaveConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static android.os.Build.VERSION_CODES.M;

/**
 * @author Administrator
 */
public class MainActivity extends AppCompatActivity {
    private CropIwaView mCropIwaView;
    public final static String IMAGE_PATH = "image_path";
    public final static String OUTPUT_URI = "output_uri";
    private static final int ROTATE_WIDGET_SENSITIVITY_COEFFICIENT = 42;
    private static final int RESULT_CODE = 1101;
    private float rotateAngle = 0;
    private String path;
    private Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= M) {
            //申请权限
            checkStoragePermission(this);
            checkPhotoPermission(this);
        }
        mCropIwaView = (CropIwaView) findViewById(R.id.crop_view);
        loadImage(0);
        mCropIwaView.setCropSaveCompleteListener(new CropIwaView.CropSaveCompleteListener() {
            @Override
            public void onCroppedRegionSaved(final Uri bitmapUri) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent();
                        intent.putExtra(OUTPUT_URI,bitmapUri);
                        setResult(RESULT_CODE, intent);
                        finish();
                    }
                });
            }
        });
        mCropIwaView.setErrorListener(new CropIwaView.ErrorListener() {
            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }
        });
        setupRotateWidget();
    }

    /**
     * 描述:检查Storage权限
     */
    public static final int REQUEST_STORAGE_PERMISSIONS = 1;
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean checkStoragePermission(Activity context) {
        List<String> permissionsList = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (permissionsList.size() > 0) {
            context.requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                    REQUEST_STORAGE_PERMISSIONS);
            return true;
        } else {
            return false;
        }
    }

    /**
     * @author $黎佳$
     * @time 2017/5/17 16:36
     * 描述:检查拍照的权限
     */
    public static void checkPhotoPermission(Activity context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            //申请WRITE_EXTERNAL_STORAGE权限
            ActivityCompat.requestPermissions(context, new String[]{Manifest.permission.CAMERA},
                    1);
        }
    }

    private void loadImage(float rotate) {
        if(TextUtils.isEmpty(path)){
            path = getIntent().getStringExtra(IMAGE_PATH);
            // path = "file://" + new File(Environment.getExternalStorageDirectory(), "a.jpg").getAbsolutePath();
            if(TextUtils.isEmpty(path)){
                return;
            }
        }
        Glide.with(this).load(Uri.parse(path))
                .asBitmap().diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true).transform(new RotateTransformation(this, rotate))
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                        MainActivity.this.bitmap = resource;
                        mCropIwaView.setImage(resource);
                    }

                    @Override
                    public void onLoadFailed(Exception e, Drawable errorDrawable) {
                        super.onLoadFailed(e, errorDrawable);
                        e.printStackTrace();
                    }
                });
    }

    private void setupRotateWidget() {
        ((HorizontalProgressWheelView) findViewById(R.id.rotate_scroll_wheel))
                .setScrollingListener(new HorizontalProgressWheelView.ScrollingListener() {
                    @Override
                    public void onScroll(float delta, float totalDistance) {
                    }

                    @Override
                    public void onScrollEnd(float delta) {
                        rotateAngle -= delta / ROTATE_WIDGET_SENSITIVITY_COEFFICIENT;
                        loadImage(rotateAngle);
                    }

                    @Override
                    public void onScrollStart() {
                    }
                });
        findViewById(R.id.wrapper_reset_rotate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rotateAngle = 0;
                loadImage(rotateAngle);
            }
        });
        findViewById(R.id.wrapper_rotate_by_angle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rotateAngle += 90;
                loadImage(rotateAngle);
            }
        });
        findViewById(R.id.rl_crop_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.rl_crop_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                crop();
            }
        });
    }

    private void crop() {
        String localImagePath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "cjhms";
        File fileDirectory = new File(localImagePath);
        if (!fileDirectory.exists()) {
            fileDirectory.mkdirs();
        }
        File outputFile = new File(fileDirectory, System.currentTimeMillis() + ".jpg");

        mCropIwaView.crop(new CropIwaSaveConfig.Builder(Uri.fromFile(outputFile))
                .setCompressFormat(Bitmap.CompressFormat.JPEG)
                .setQuality(80)
                .build(), bitmap, fileDirectory, outputFile);
    }
}
