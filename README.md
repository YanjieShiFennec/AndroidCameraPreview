# Android Camera Preview

Implement android camera preview via `Camera2` and `CameraX`.

This project contains three modules: `app-Camera2-Matrix`, `app-Camera2-TextureView` and `app-CameraX`. Each of which realizes the function of android camera preview.

Using Camera2 may cause display distortion due to the mismatch between the aspect ratio of the output size from camera and the layout in xml (e.g. TextureView). This project provides two ways to solve this problem `app-Camera2-Matrix` and `app-Camera2-TextureView`.

## app-Camera2-Matrix

Solve display distortion by modifying the TextureView's transform matrix.

```java
textureView.setTransform(matrix);
```

## app-Camera2-TextureView

Solve display distortion by create a custom TextureVIew Class AutoFItTextureVIew. AutoFItTextureVIew is capable of resizing itself dynamically to match the aspect ratio of camera.

## app-CameraX

Implement android camera preview with CameraX.
