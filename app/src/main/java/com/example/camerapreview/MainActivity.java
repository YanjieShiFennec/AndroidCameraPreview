package com.example.camerapreview;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Collections;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Camera2Preview";
    private TextureView textureView;
    private CameraDevice mCameraDevice;
    private Size imageDimension;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSession;
    private Surface surface;
    private Button bindButton;
    private Button unbindButton;
    private boolean isBind = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        initPermission(); // 权限申请

        bindButton = findViewById(R.id.bind);
        unbindButton = findViewById(R.id.unbind);
        bindButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isBind) return;

                initTextureView();
                openCamera();
            }
        });
        unbindButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isBind) {
                    if (captureRequestBuilder != null) {
                        captureRequestBuilder.removeTarget(surface);
                        captureRequestBuilder = null;
                    }
                    if (surface != null) {
                        surface.release();
                        surface = null;
                    }
                    if (cameraCaptureSession != null) {
                        try {
                            cameraCaptureSession.stopRepeating();
                            cameraCaptureSession.abortCaptures();
                        } catch (CameraAccessException e) {
                            throw new RuntimeException(e);
                        }
                        cameraCaptureSession = null;
                    }
                    if (mCameraDevice != null) {
                        mCameraDevice.close();
                        mCameraDevice = null;
                    }
                    isBind = false;
                }
            }
        });

        initTextureView();
    }

    private void initTextureView(){
        textureView = findViewById(R.id.texture_view);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                Log.i(TAG, "onSurfaceTextureAvailable");
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                Log.i(TAG, "onSurfaceTextureSizeChanged");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                Log.i(TAG, "onSurfaceTextureDestroyed");
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                // Log.i(TAG, "onSurfaceTextureUpdated");
            }
        });
    }

    // 相机状态监听
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            // 相机打开时执行
            Log.i(TAG, "onOpened");
            mCameraDevice = camera;
            // 创建相机预览会话
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            // 相机断开
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

        }
    };

    private void openCamera() {
        // 获取 CameraManager 实例
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // 获取第一个相机 ID
            String cameraId = cameraManager.getCameraIdList()[0];
            // 通过 cameraId 获取 Camera 参数
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            // 获取支持的分辨率
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.

                // requestPermissions(new String[]{Manifest.permission.CAMERA}, 0);
                return;
            }
            cameraManager.openCamera(cameraId, stateCallback, null);
            isBind = true;
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean initPermission() {
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.CAMERA}, 1);
        // 高版本 Android SDK 时使用如下代码
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
                return false;
            }
        }
        return true;
    }

    private void createCameraPreviewSession() {
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        assert surfaceTexture != null;

        surfaceTexture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
        // 预览的输出画面
        surface = new Surface(surfaceTexture);

        try {
            // 预览请求
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            // 自动聚焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 创建会话
            mCameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        // 会话的状态监听
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (mCameraDevice == null) {
                                return;
                            }

                            cameraCaptureSession = session;
                            try {
                                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                            } catch (CameraAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }
}


