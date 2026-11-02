package com.example.pricescanner;

import android.Manifest;
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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
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

    private static final String TAG = "ScanAdd";

    private static final Interpolator EASE =
            new PathInterpolator(0.22f, 1f, 0.36f, 1f);

    private View rootView;
    private LinearLayout topBar;
    private PreviewView previewView;
    private ScannerOverlayView scannerOverlay;
    private TextView hintView;
    private ImageButton torchButton;

    private View sheetMask;
    private FrameLayout sheetContainer;
    private View sheetPanel;
    private ScrollView sheetScroll;
    private LinearLayout sheetBody;

    private BarcodeScanner barcodeScanner;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private Camera camera;
    private ToneGenerator toneGenerator;
    private ExecutorService cameraExecutor;
    private ExecutorService ioExecutor;
    private ProductDbHelper dbHelper;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean cameraStarting;
    private boolean detected;
    private boolean sheetOpen;
    private boolean torchOn;
    private boolean destroyed;

    private String ignoreBarcode;
    private long ignoreUntil;

    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        }
        setContentView(R.layout.activity_scan_add);

        rootView = findViewById(R.id.scan_add_root);
        topBar = findViewById(R.id.sa_top);
        previewView = findViewById(R.id.sa_preview);
        scannerOverlay = findViewById(R.id.sa_overlay);
        hintView = findViewById(R.id.sa_hint);
        torchButton = findViewById(R.id.sa_torch);
        sheetMask = findViewById(R.id.sa_sheet_mask);
        sheetContainer = findViewById(R.id.sa_sheet_container);
        sheetPanel = findViewById(R.id.sa_sheet_panel);
        sheetScroll = findViewById(R.id.sa_sheet_scroll);
        sheetBody = findViewById(R.id.sa_sheet_body);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setupInsets();
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        scannerOverlay.setFrameStyle(ScannerOverlayView.FRAME_WIDE);
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
        sheetMask.setOnClickListener(v -> closeSheet());

        dbHelper = new ProductDbHelper(this);
        ioExecutor = Executors.newSingleThreadExecutor();
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

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            topBar.setPadding(
                    dpInt(16), bars.top + dpInt(8), dpInt(16), dpInt(8));

            int hintMargin = bars.bottom + dpInt(44);
            ViewGroup.MarginLayoutParams hintParams =
                    (ViewGroup.MarginLayoutParams) hintView.getLayoutParams();
            if (hintParams.bottomMargin != hintMargin) {
                hintParams.bottomMargin = hintMargin;
                hintView.setLayoutParams(hintParams);
            }

            int sheetBottom = Math.max(bars.bottom, ime.bottom);
            if (sheetContainer.getPaddingBottom() != sheetBottom) {
                sheetContainer.setPadding(0, 0, 0, sheetBottom);
                sheetContainer.post(() -> {
                    if (sheetOpen) {
                        fitSheetHeight();
                        if (ime.bottom > 0) {
                            view.postDelayed(this::ensureFocusedFieldVisible, 80);
                        }
                    }
                });
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(rootView);
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
        if (detected || sheetOpen) {
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
                    if (detected || sheetOpen || destroyed) {
                        return;
                    }
                    String value = BarcodePicker.pickInFrame(
                            barcodes,
                            imageProxy,
                            previewView.getWidth(),
                            previewView.getHeight(),
                            scannerOverlay.getFrameRect());
                    if (value == null) {
                        return;
                    }
                    if (value.equals(ignoreBarcode)
                            && System.currentTimeMillis() < ignoreUntil) {
                        return;
                    }
                    ignoreBarcode = null;
                    onDetected(value);
                })
                .addOnFailureListener(e -> Log.w(TAG, "Barcode processing failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onDetected(String barcode) {
        detected = true;
        ignoreBarcode = barcode;
        ignoreUntil = System.currentTimeMillis() + 1800;
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }
        playSuccessTone();
        vibrate();
        scannerOverlay.playSuccess();
        hintView.setText(getString(R.string.scan_add_success, barcode));
        hintView.setTextColor(0xFF6EE7B7);
        handler.postDelayed(() -> {
            if (destroyed || isFinishing()) {
                return;
            }
            loadProduct(barcode);
        }, 450);
    }

    private void loadProduct(String barcode) {
        ioExecutor.execute(() -> {
            Product existing = dbHelper.findProduct(barcode);
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                showProductSheet(barcode, existing);
            });
        });
    }

    private void showProductSheet(String barcode, Product existing) {
        sheetPanel.animate().cancel();
        sheetMask.animate().cancel();
        sheetBody.removeAllViews();
        ViewGroup.LayoutParams panelParams = sheetPanel.getLayoutParams();
        panelParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        sheetPanel.setLayoutParams(panelParams);

        View content = LayoutInflater.from(this)
                .inflate(R.layout.sheet_product, sheetBody, false);
        sheetBody.addView(content);

        TextView title = content.findViewById(R.id.sheet_title);
        TextView sub = content.findViewById(R.id.sheet_sub);
        EditText barcodeInput = content.findViewById(R.id.edit_barcode);
        EditText nameInput = content.findViewById(R.id.edit_name);
        EditText priceInput = content.findViewById(R.id.edit_price);
        EditText categoryInput = content.findViewById(R.id.edit_category);
        TextView deleteButton = content.findViewById(R.id.btn_delete);

        final boolean isNew = existing == null;
        title.setText(isNew ? R.string.sheet_new_product : R.string.sheet_edit_product);
        sub.setVisibility(View.VISIBLE);
        sub.setText(isNew
                ? R.string.sheet_scan_product_sub
                : R.string.sheet_product_sub);

        barcodeInput.setText(barcode);
        barcodeInput.setEnabled(false);
        barcodeInput.setTextColor(ContextCompat.getColor(this, R.color.ink3));
        if (existing != null) {
            nameInput.setText(existing.name);
            nameInput.setSelection(nameInput.getText().length());
            priceInput.setText(MoneyUtils.formatCents(existing.priceCents));
            categoryInput.setText(existing.category);
        }
        deleteButton.setVisibility(isNew ? View.GONE : View.VISIBLE);

        content.findViewById(R.id.btn_cancel).setOnClickListener(v -> closeSheet());
        content.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String category = categoryInput.getText().toString().trim();
            if (name.isEmpty()) {
                nameInput.setError(getString(R.string.error_name));
                return;
            }
            Long priceCents = MoneyUtils.parseYuanToCents(
                    priceInput.getText().toString());
            if (priceCents == null) {
                priceInput.setError(getString(R.string.error_price));
                return;
            }
            saveProduct(barcode, name, category, priceCents, existing, isNew);
        });
        deleteButton.setOnClickListener(v -> {
            if (existing != null) {
                confirmDeleteProduct(existing);
            }
        });

        sheetOpen = true;
        sheetMask.setVisibility(View.VISIBLE);
        sheetMask.setAlpha(0f);
        sheetMask.animate().alpha(1f).setDuration(220).start();
        sheetPanel.setTranslationY(dp(600));
        sheetPanel.setAlpha(0f);
        sheetContainer.setVisibility(View.VISIBLE);
        sheetContainer.post(() -> {
            fitSheetHeight();
            sheetPanel.post(() -> {
                sheetPanel.setTranslationY(sheetPanel.getHeight());
                sheetPanel.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(400)
                        .setInterpolator(EASE)
                        .start();
            });
        });
    }

    private void saveProduct(
            String barcode, String name, String category,
            long priceCents, Product existing, boolean isNew) {
        ioExecutor.execute(() -> {
            if (isNew && dbHelper.findProduct(barcode) != null) {
                runOnUiThread(() -> {
                    if (!destroyed && !isFinishing()) {
                        Toast.makeText(this, R.string.error_barcode_exists,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                return;
            }
            dbHelper.saveProduct(
                    barcode,
                    name,
                    existing != null ? existing.brand : "",
                    existing != null ? existing.spec : "",
                    priceCents,
                    "manual",
                    category);
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                closeSheet();
                Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void confirmDeleteProduct(Product product) {
        new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                .setTitle(R.string.delete_product_title)
                .setMessage(getString(R.string.delete_product_message, product.name))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        ioExecutor.execute(() -> {
                            dbHelper.deleteProduct(product.barcode);
                            runOnUiThread(() -> {
                                if (destroyed || isFinishing()) {
                                    return;
                                }
                                closeSheet();
                                Toast.makeText(
                                        this,
                                        getString(
                                                R.string.toast_product_deleted,
                                                product.name),
                                        Toast.LENGTH_SHORT).show();
                            });
                        }))
                .show();
    }

    private void fitSheetHeight() {
        if (!sheetOpen || sheetBody.getChildCount() == 0) {
            return;
        }
        View content = sheetBody.getChildAt(0);
        int width = sheetBody.getWidth();
        if (width <= 0) {
            width = rootView.getWidth() - dpInt(32);
        }
        if (width <= 0) {
            return;
        }
        content.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int natural = content.getMeasuredHeight() + dpInt(49);
        int available = rootView.getHeight()
                - sheetContainer.getPaddingBottom()
                - dpInt(16);
        if (available <= 0) {
            return;
        }
        ViewGroup.LayoutParams params = sheetPanel.getLayoutParams();
        params.height = natural <= available
                ? ViewGroup.LayoutParams.WRAP_CONTENT
                : available;
        sheetPanel.setLayoutParams(params);
    }

    private void ensureFocusedFieldVisible() {
        View focus = getCurrentFocus();
        if (focus == null || sheetScroll == null || !sheetScroll.isAttachedToWindow()) {
            return;
        }
        int[] focusLocation = new int[2];
        focus.getLocationInWindow(focusLocation);
        int[] scrollLocation = new int[2];
        sheetScroll.getLocationInWindow(scrollLocation);
        int offset = focusLocation[1] - scrollLocation[1];
        int target = sheetScroll.getScrollY() + offset - dpInt(28);
        sheetScroll.smoothScrollTo(0, Math.max(0, target));
    }

    private void closeSheet() {
        if (!sheetOpen) {
            return;
        }
        sheetOpen = false;
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
            focus.clearFocus();
        }
        sheetPanel.animate()
                .translationY(sheetPanel.getHeight())
                .setDuration(300)
                .setInterpolator(EASE)
                .withEndAction(() -> {
                    sheetContainer.setVisibility(View.GONE);
                    sheetBody.removeAllViews();
                    resumeScanning();
                })
                .start();
        sheetMask.animate().alpha(0f).setDuration(220)
                .withEndAction(() -> sheetMask.setVisibility(View.GONE))
                .start();
    }

    private void resumeScanning() {
        if (destroyed) {
            return;
        }
        detected = false;
        hintView.setText(R.string.scan_add_hint);
        hintView.setTextColor(ContextCompat.getColor(this, R.color.white));
        scannerOverlay.setMode(ScannerOverlayView.MODE_SCANNING);
        ignoreUntil = System.currentTimeMillis() + 1500;
        if (imageAnalysis != null) {
            imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
        }
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
    public void onBackPressed() {
        if (sheetOpen) {
            closeSheet();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!detected && !sheetOpen && imageAnalysis != null) {
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
        if (ioExecutor != null) {
            ioExecutor.shutdown();
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        super.onDestroy();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int dpInt(float value) {
        return (int) (dp(value) + 0.5f);
    }
}
