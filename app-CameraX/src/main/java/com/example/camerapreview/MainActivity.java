package com.example.camerapreview;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    public static final int PERMISSIONS_REQUEST_CAMERA = 100;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private boolean isBind = false;
    private boolean isFirstStart = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        previewView = findViewById(R.id.previewView);
        Button unbindButton = findViewById(R.id.unbind);
        unbindButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isBind) {
                    unbind();
                }
            }
        });
        Button bindButton = findViewById(R.id.bind);
        bindButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isBind) {
                    bindPreview();
                }
            }
        });

        checkPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isFirstStart) {
            isFirstStart = false;
        } else {
            if (!isBind) {
                bindPreview();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBind) {
            unbind();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void unbind() {
        cameraProvider.unbindAll();
        isBind = false;
    }

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
        } else {
            initCameraProviderFuture();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCameraProviderFuture();
            } else {
                Toast.makeText(MainActivity.this, "Camera permission is required to take a photo.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initCameraProviderFuture() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    bindPreview();
                } catch (ExecutionException | InterruptedException e) {
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview() {
        // 选择 16:9 宽高比
        AspectRatioStrategy aspectRatioStrategy = new AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO);
        ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(aspectRatioStrategy)
                .build();
        Preview preview = new Preview.Builder()
                .setResolutionSelector(resolutionSelector).build();
        // 选择居中缩放使 Preview 适配 PreviewView
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        // 将 Preview 连接到 PreviewView
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // 选择前置摄像头
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        // 绑定
        Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview);
        isBind = true;
    }
}


