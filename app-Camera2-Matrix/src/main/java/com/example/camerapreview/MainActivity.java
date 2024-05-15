package com.example.camerapreview;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Camera2Preview";
    public static final int PERMISSIONS_REQUEST_CAMERA = 100;
    private TextureView textureView;
    private CameraDevice mCameraDevice;
    private Size imageDimension;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSession;
    private Surface surface;
    private int textureViewWidth;
    private int textureViewHeight;
    private boolean isBind = false;
    private boolean isFirstStart = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        initPermission(); // 权限申请
        initTextureView();

        Button bindButton = findViewById(R.id.bind);
        Button unbindButton = findViewById(R.id.unbind);
        bindButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isBind) {
                    openCamera();
                }
            }
        });
        unbindButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isBind) {
                    releasePreview();
                }
            }
        });
    }

    private void initPermission() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera();
                } else {
                    Toast.makeText(MainActivity.this, "Camera permission is required to take a photo.", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isFirstStart) {
            // 打开应用时不启动相机
            isFirstStart = false;
        } else {
            if (!isBind) {
                openCamera();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePreview();
    }

    private void initTextureView() {
        textureView = findViewById(R.id.texture_view);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                Log.i(TAG, "onSurfaceTextureAvailable");
                textureViewWidth = width;
                textureViewHeight = height;
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

    private void releasePreview() {
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
            Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
            imageDimension = getOptimalSize(outputSizes);

            int viewWidth = imageDimension.getWidth();
            int viewHeight = imageDimension.getHeight();
            if (viewWidth > viewHeight) {
                // swap
                viewWidth ^= viewHeight;
                viewHeight ^= viewWidth;
                viewWidth ^= viewHeight;
            }

            float widthRatio = (float) viewWidth / textureViewWidth;
            float heightRatio = (float) viewHeight / textureViewHeight;
            if (widthRatio > heightRatio) {
                // outputView 放大时宽先占满屏幕或缩小时高先占满屏幕
                viewHeight = textureViewWidth * viewHeight / viewWidth;
                viewWidth = textureViewWidth;
            } else {
                // outputView 放大时高先占满屏幕或缩小时宽先占满屏幕
                viewWidth = textureViewHeight * viewWidth / viewHeight;
                viewHeight = textureViewHeight;
            }

            Matrix matrix = new Matrix();
            // 将 outputView 和 textureView 中心点重合
            matrix.preTranslate((float) (textureViewWidth - viewWidth) / 2,
                    (float) (textureViewHeight - viewHeight) / 2);
            // 缩放
            matrix.preScale((float) viewWidth / textureViewWidth,
                    (float) viewHeight / textureViewHeight);
            // 设置要与此纹理视图关联的转换。指定的转换适用于基础表面纹理，不会影响视图本身的大小或位置，仅影响其内容。
            textureView.setTransform(matrix);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            cameraManager.openCamera(cameraId, stateCallback, null);

            isBind = true;
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Size getOptimalSize(Size[] outputSizes) {
        Size tempSize;
        List<Size> sizes = new ArrayList<>();
        for (Size outputSize : outputSizes) {
            if (textureViewWidth > textureViewHeight) {
                // 横屏
                if (outputSize.getHeight() > textureViewHeight && outputSize.getWidth() > textureViewWidth) {
                    sizes.add(outputSize);
                }
            } else {
                // 竖屏
                if (outputSize.getWidth() > textureViewHeight && outputSize.getHeight() > textureViewWidth) {
                    sizes.add(outputSize);
                }
            }
        }

        if (!sizes.isEmpty()) {
            // 如果有多个符合条件找到一个差距最小的，最接近预览分辨率的
            tempSize = sizes.get(0);
            int minnum = 999999;
            for (Size size : sizes) {
                int num = size.getHeight() * size.getHeight() - textureViewWidth * textureViewHeight;
                if (num < minnum) {
                    minnum = num;
                    tempSize = size;
                }
            }
        } else {
            // outputView 分辨率小于 textureView 时选取最大的 size
            Comparator<Size> comparator = Comparator.comparingInt(size -> size.getWidth() * size.getHeight());
            tempSize = Collections.max(Arrays.asList(outputSizes), comparator);
        }
        return tempSize;
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


