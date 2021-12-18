package com.shvmsaini.vision;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaActionSound;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceLandmark;
import com.shvmsaini.vision.databinding.ActivityMainBinding;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final String TAG = MainActivity.this.getClass().getName();
    private final int REQUEST_CODE_PERMISSIONS = 10;
    private final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private final MediaActionSound sound = new MediaActionSound();
    private ImageCapture imageCapture;
    private ActivityMainBinding activityMainBinding;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private CameraSelector lensFacing = CameraSelector.DEFAULT_BACK_CAMERA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        super.onCreate(savedInstanceState);
        setContentView(activityMainBinding.getRoot());
//        setContentView(R.layout.activity_main);
        // Request camera permissions
        if (allPermissionsGranted()) {
            try {
                startCamera();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        } else
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        // Set up the listener for take photo button
        activityMainBinding.cameraCaptureButton.setOnClickListener(v -> {
            takePhoto();
            sound.play(MediaActionSound.SHUTTER_CLICK);
        });
        activityMainBinding.cameraFlip.setOnClickListener(v -> {
            try {
                flipCamera();
                Log.d(TAG, "Camera Flipping Success");
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera Flipping Failed");
                e.printStackTrace();
            }

        });
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void takePhoto() {
        String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
        File photoFile = new File(getOutputDirectory(),
                new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Log.d(TAG, "Image capture success and saved at " + Uri.fromFile(photoFile));
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(
                            MainActivity.this, "Image Saved successfully", Toast.LENGTH_SHORT).show();
                    FaceDetector detector = FaceDetection.getClient();
                    Task<List<Face>> result = null;
                    try {
                        result = detector.process(InputImage.fromFilePath(MainActivity.this, Uri.fromFile(photoFile)))
                                .addOnSuccessListener(faces -> Log.d(TAG, "onCaptureSuccess: Success"))
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "onCaptureSuccess: Failure");
                                    e.printStackTrace();
                                });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    assert result != null;
                    result.addOnSuccessListener(faces -> {
                        Log.d(TAG, "onSuccess: " + faces);
                        Toast.makeText(
                                MainActivity.this, faces.size() + " Face Detected", Toast.LENGTH_SHORT).show();

                    });


                });
            }


            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "image capture failed");
                exception.printStackTrace();
            }
        });
    }

    private void startCamera() throws ExecutionException, InterruptedException {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    void bindPreview(ProcessCameraProvider cameraProvider) {
        // Preview
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(activityMainBinding.viewFinder.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder().setTargetRotation(
                this.getWindowManager().getDefaultDisplay().getRotation()).build();
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().build();
        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            int rotationDegrees = image.getImageInfo().getRotationDegrees();
            @SuppressLint("UnsafeOptInUsageError") InputImage i =  InputImage.fromMediaImage(image.getImage(),rotationDegrees);
            Log.d(TAG, "rotation = " + rotationDegrees);
            // insert your code here.
        });
        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll();
            // Bind use cases to camera
            cameraProvider.bindToLifecycle(this, lensFacing, preview, imageCapture, imageAnalysis);
            Log.d(TAG, "Use case binding success");
        } catch (Exception exc) {
            Log.e(TAG, "Use case binding failed");
            exc.printStackTrace();
        }

    }

    private void flipCamera() throws ExecutionException, InterruptedException {
        activityMainBinding.cameraFlip.animate();
        if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA)
            lensFacing = CameraSelector.DEFAULT_BACK_CAMERA;
        else if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA)
            lensFacing = CameraSelector.DEFAULT_FRONT_CAMERA;
        startCamera();
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private File getOutputDirectory() {
        File mediaDir = null;
        if (getExternalMediaDirs()[0] != null) {
            mediaDir = new File(String.valueOf(getExternalMediaDirs()[0]));
        }
        if (mediaDir != null) {
            if (mediaDir.exists())
                return mediaDir;
            else if (mediaDir.mkdir())
                return mediaDir;
            else Toast.makeText(this, "Could not make a directory", Toast.LENGTH_SHORT).show();
        }
        return getFilesDir();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                try {
                    startCamera();
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private static class myAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            @SuppressLint("UnsafeOptInUsageError") Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image =
                        InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                // Pass image to an ML Kit Vision API
                // ...
            }
        }
    }

//    void getDetails(List<Face> faces) {
//        for (Face face : faces) {
//            Rect bounds = face.getBoundingBox();
//            float rotY = face.getHeadEulerAngleY();  // Head is rotated to the right rotY degrees
//            float rotZ = face.getHeadEulerAngleZ();  // Head is tilted sideways rotZ degrees
//
//            // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
//            // nose available):
//            FaceLandmark leftEar = face.getLandmark(FaceLandmark.LEFT_EAR);
//            if (leftEar != null) {
//                PointF leftEarPos = leftEar.getPosition();
//            }
//
//            // If contour detection was enabled:
//            List<PointF> leftEyeContour =
//                    face.getContour(FaceContour.LEFT_EYE).getPoints();
//            List<PointF> upperLipBottomContour =
//                    face.getContour(FaceContour.UPPER_LIP_BOTTOM).getPoints();
//
//            // If classification was enabled:
//            if (face.getSmilingProbability() != null) {
//                float smileProb = face.getSmilingProbability();
//            }
//            if (face.getRightEyeOpenProbability() != null) {
//                float rightEyeOpenProb = face.getRightEyeOpenProbability();
//            }
//
//            // If face tracking was enabled:
//            if (face.getTrackingId() != null) {
//                int id = face.getTrackingId();
//            }
//        }
//    }


}