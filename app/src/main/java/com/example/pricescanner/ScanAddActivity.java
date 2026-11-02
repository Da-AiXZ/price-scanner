package com.example.pricescanner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.hardware.camera2.CaptureRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ScanAddActivity extends AppCompatActivity {

    public static final String EXTRA_BARCODE = "extra_barcode";

    private static final String TAG = "ScanAdd";

    private PreviewView previewView;
    private ScannerOverlayView scannerOverlay;
    private TextView hintView;
    private ImageButton torchButton;

    private BarcodeScanner barcodeScanner;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private Camera camera;
    private ToneGenerator toneGenerator;
    private ExecutorService cameraExecutor;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean cameraStarting;
    private boolean detected;
    private boolean torchOn;
    private boolean destroyed;

    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        }
        setContentView(R.layout.activity_scan_add);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            View root = findViewById(R.id.scan_add_root);
            ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

        previewView = findViewById(R.id.sa_preview);
        scannerOverlay = findViewById(R.id.sa_overlay);
        hintView = findViewById(R.id.sa_hint);
        torchButton = findViewById(R.id.sa_torch);

        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        scannerOverlay.setMode(ScannerOverlayView.MODE_SCANNING);

        findViewById(R.id.sa_back).setOnClickListener(v -> finish());
        torchButton.setOnClickListener(v -> toggleTorch());
        previewView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                focusAt(event.getX(), event.getY());
                scannerOverlay.showFocusRing(event.getX(), event.getY());
                v.performClick();
            }
            return true;
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
        barcodeScanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_ITF,
                        Barcode.FORMAT_CODE_39)
                .build());

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startCamera();
                    } else {
                        Toast.makeText(
                                this,
                                R.string.toast_permission_denied,
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });

        if (hasCameraPermission()) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        if (!hasCameraPermission() || cameraStarting || camera != null) {
            return;
        }
        cameraStarting = true;
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            cameraStarting = false;
            try {
                cameraProvider = future.get();

                Preview.Builder previewBuilder = new Preview.Builder()
                        .setTargetResolution(new Size(1920, 1080));
                new Camera2Interop.Extender<>(previewBuilder)
                        .setCaptureRequestOption(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                Preview preview = previewBuilder.build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new Size(1280, 720))
                        .build();

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                if (torchOn && camera.getCameraInfo().hasFlashUnit()) {
                    camera.getCameraControl().enableTorch(true);
                }
                previewView.postDelayed(
                        () -> focusAt(
                                previewView.getWidth() / 2f,
                                previewView.getHeight() / 2f),
                        600);
            } catch (Exception e) {
                Log.e(TAG, "Unable to start camera", e);
                hintView.setText(R.string.status_no_camera);
                Toast.makeText(
                        ScanAddActivity.this,
                        R.string.toast_camera_error,
                        Toast.LENGTH_SHORT).show();
                handler.postDelayed(ScanAddActivity.this::finish, 1200);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (detected) {
            imageProxy.close();
            return;
        }
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }
        InputImage inputImage = InputImage.fromMediaImage(
                mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        barcodeScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (detected || destroyed) {
                        return;
                    }
                    String value = BarcodePicker.pickInFrame(
                            barcodes,
                            imageProxy,
                            previewView.getWidth(),
                            previewView.getHeight(),
                            scannerOverlay.getFrameRect());
                    if (value != null) {
                        onDetected(value);
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Barcode processing failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onDetected(String barcode) {
        detected = true;
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }
        playSuccessTone();
        vibrate();
        scannerOverlay.playSuccess();
        hintView.setText(getString(R.string.scan_add_success, barcode));
        hintView.setTextColor(0xFF6EE7B7);
        handler.postDelayed(() -> {
            if (destroyed) {
                return;
            }
            Intent data = new Intent();
            data.putExtra(EXTRA_BARCODE, barcode);
            setResult(Activity.RESULT_OK, data);
            finish();
        }, 550);
    }

    private void toggleTorch() {
        if (camera == null) {
            Toast.makeText(this, R.string.toast_camera_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!camera.getCameraInfo().hasFlashUnit()) {
            Toast.makeText(this, R.string.toast_no_flash, Toast.LENGTH_SHORT).show();
            return;
        }
        torchOn = !torchOn;
        camera.getCameraControl().enableTorch(torchOn);
        torchButton.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                this, torchOn ? R.color.mint : R.color.white)));
    }

    private void focusAt(float x, float y) {
        if (camera == null) {
            return;
        }
        try {
            MeteringPointFactory factory = previewView.getMeteringPointFactory();
            MeteringPoint point = factory.createPoint(x, y);
            FocusMeteringAction action = new FocusMeteringAction.Builder(
                    point, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(5, TimeUnit.SECONDS)
                    .build();
            camera.getCameraControl().startFocusAndMetering(action);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to focus camera", e);
        }
    }

    private void playSuccessTone() {
        try {
            if (toneGenerator == null) {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            }
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to play success tone", e);
        }
    }

    private void vibrate() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        18, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(18);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to vibrate", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!detected && imageAnalysis != null) {
            imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
        }
    }

    @Override
    protected void onPause() {
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        super.onDestroy();
    }
}
