package com.example.pricescanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PriceScanner";

    private static final int STATE_IDLE = 0;
    private static final int STATE_LOOKUP = 1;
    private static final int STATE_PRODUCT = 2;
    private static final int STATE_EMPTY = 3;
    private static final int STATE_PERMISSION = 4;

    private PreviewView previewView;
    private ScannerOverlayView scannerOverlay;
    private ImageButton btnTorch;
    private ImageButton btnManual;
    private TextView idleTitle;
    private TextView idleSubtitle;
    private TextView productName;
    private TextView productBarcode;
    private TextView productPrice;
    private TextView emptyBarcode;
    private View panelIdle;
    private View panelProduct;
    private View panelEmpty;
    private View panelPermission;

    private BarcodeScanner barcodeScanner;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private Camera camera;
    private ToneGenerator toneGenerator;
    private ExecutorService cameraExecutor;
    private ExecutorService ioExecutor;
    private ProductDbHelper dbHelper;

    private static final int REQUIRED_STABLE_FRAMES = 2;

    private volatile boolean scanning;
    private boolean cameraStarting;
    private boolean torchOn;
    private int state = STATE_IDLE;
    private String currentBarcode;
    private Product currentProduct;
    private String pendingBarcode;
    private int pendingFrames;

    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        bindViews();
        applyWindowInsets();

        cameraExecutor = Executors.newSingleThreadExecutor();
        ioExecutor = Executors.newSingleThreadExecutor();
        dbHelper = new ProductDbHelper(this);
        barcodeScanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128)
                .build());

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startCamera();
                    } else {
                        showPermissionPanel();
                    }
                });

        setupClickListeners();
        setupBackNavigation();
        scannerOverlay.setScanning(false);

        if (hasCameraPermission()) {
            showIdlePanel();
            startCamera();
        } else {
            state = STATE_PERMISSION;
            showPermissionPanel();
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void bindViews() {
        previewView = findViewById(R.id.preview_view);
        scannerOverlay = findViewById(R.id.scanner_overlay);
        btnTorch = findViewById(R.id.btn_torch);
        btnManual = findViewById(R.id.btn_manual);
        idleTitle = findViewById(R.id.idle_title);
        idleSubtitle = findViewById(R.id.idle_subtitle);
        productName = findViewById(R.id.product_name);
        productBarcode = findViewById(R.id.product_barcode);
        productPrice = findViewById(R.id.product_price);
        emptyBarcode = findViewById(R.id.empty_barcode);
        panelIdle = findViewById(R.id.panel_idle);
        panelProduct = findViewById(R.id.panel_product);
        panelEmpty = findViewById(R.id.panel_empty);
        panelPermission = findViewById(R.id.panel_permission);
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.root);
        View topBar = findViewById(R.id.top_bar);
        View bottomCard = findViewById(R.id.bottom_card);
        int topPadding = topBar.getPaddingTop();
        int margin = (int) (16 * getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            topBar.setPaddingRelative(
                    topBar.getPaddingStart(),
                    topPadding + bars.top,
                    topBar.getPaddingEnd(),
                    topBar.getPaddingBottom());

            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) bottomCard.getLayoutParams();
            params.leftMargin = margin + bars.left;
            params.rightMargin = margin + bars.right;
            params.bottomMargin = margin + bars.bottom;
            bottomCard.setLayoutParams(params);
            return windowInsets;
        });
    }

    private void setupClickListeners() {
        btnTorch.setOnClickListener(v -> toggleTorch());
        btnManual.setOnClickListener(v -> showManualInputDialog());
        findViewById(R.id.btn_rescan).setOnClickListener(v -> startScanning());
        findViewById(R.id.btn_rescan_empty).setOnClickListener(v -> startScanning());
        findViewById(R.id.btn_edit).setOnClickListener(v -> {
            if (currentProduct != null) {
                showProductDialog(currentProduct);
            }
        });
        findViewById(R.id.btn_add).setOnClickListener(v -> {
            if (currentBarcode != null) {
                showProductDialog(null);
            }
        });
        findViewById(R.id.btn_permission).setOnClickListener(v -> requestCameraPermission());
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (state == STATE_PRODUCT || state == STATE_EMPTY) {
                    startScanning();
                } else {
                    finish();
                }
            }
        });
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        if (hasCameraPermission()) {
            startCamera();
            return;
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            openAppSettings();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_SHORT).show();
        }
    }

    private void startCamera() {
        if (!hasCameraPermission() || cameraProvider != null || cameraStarting) {
            return;
        }
        cameraStarting = true;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            cameraStarting = false;
            try {
                cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);

                if (torchOn && camera.getCameraInfo().hasFlashUnit()) {
                    camera.getCameraControl().enableTorch(true);
                }

                if (state == STATE_IDLE || state == STATE_PERMISSION) {
                    startScanning();
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to start camera", e);
                Toast.makeText(
                        MainActivity.this, R.string.toast_camera_error, Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void startScanning() {
        if (!hasCameraPermission()) {
            showPermissionPanel();
            return;
        }
        if (imageAnalysis == null) {
            startCamera();
            return;
        }
        scanning = true;
        pendingBarcode = null;
        pendingFrames = 0;
        state = STATE_IDLE;
        showIdlePanel();
        scannerOverlay.setScanning(true);
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
    }

    private void pauseScanning() {
        scanning = false;
        pendingBarcode = null;
        pendingFrames = 0;
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }
        scannerOverlay.setScanning(false);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (!scanning) {
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
                    if (!scanning) {
                        return;
                    }
                    String value = pickBarcodeInFrame(barcodes, imageProxy);
                    if (value == null) {
                        pendingBarcode = null;
                        pendingFrames = 0;
                        return;
                    }
                    if (value.equals(pendingBarcode)) {
                        pendingFrames++;
                    } else {
                        pendingBarcode = value;
                        pendingFrames = 1;
                    }
                    if (pendingFrames >= REQUIRED_STABLE_FRAMES) {
                        pendingBarcode = null;
                        pendingFrames = 0;
                        onBarcodeDetected(value);
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Barcode processing failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    /**
     * 只接受完整落在绿色取景框内的条码；框内有多个时取面积最大（离得最近、最完整）的一个。
     */
    private String pickBarcodeInFrame(
            java.util.List<Barcode> barcodes, ImageProxy imageProxy) {
        RectF frame = scannerOverlay.getFrameRect();
        if (frame.isEmpty()) {
            return null;
        }

        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        float imageWidth = imageProxy.getWidth();
        float imageHeight = imageProxy.getHeight();
        if (rotation == 90 || rotation == 270) {
            float swap = imageWidth;
            imageWidth = imageHeight;
            imageHeight = swap;
        }
        float viewWidth = previewView.getWidth();
        float viewHeight = previewView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return null;
        }

        // PreviewView 默认 FILL_CENTER：等比放大铺满并居中裁切。
        float scale = Math.max(viewWidth / imageWidth, viewHeight / imageHeight);
        float offsetX = (viewWidth - imageWidth * scale) / 2f;
        float offsetY = (viewHeight - imageHeight * scale) / 2f;

        String bestValue = null;
        float bestArea = 0;
        for (Barcode barcode : barcodes) {
            String raw = barcode.getRawValue();
            if (raw == null) {
                continue;
            }
            String value = raw.trim();
            if (!isScannableBarcode(barcode, value)) {
                continue;
            }
            android.graphics.Rect box = barcode.getBoundingBox();
            if (box == null || box.width() <= 0 || box.height() <= 0) {
                continue;
            }
            RectF mapped = new RectF(
                    box.left * scale + offsetX,
                    box.top * scale + offsetY,
                    box.right * scale + offsetX,
                    box.bottom * scale + offsetY);
            if (mapped.left < frame.left
                    || mapped.top < frame.top
                    || mapped.right > frame.right
                    || mapped.bottom > frame.bottom) {
                continue;
            }
            float area = mapped.width() * mapped.height();
            if (area > bestArea) {
                bestArea = area;
                bestValue = value;
            }
        }
        return bestValue;
    }

    private void onBarcodeDetected(String barcode) {
        if (!scanning) {
            return;
        }
        scanning = false;
        lookupBarcode(barcode);
    }

    private boolean isScannableBarcode(Barcode barcode, String value) {
        switch (barcode.getFormat()) {
            case Barcode.FORMAT_EAN_13:
                return value.length() == 13
                        && isDigits(value)
                        && hasValidCheckDigit(value, false);
            case Barcode.FORMAT_EAN_8:
                return value.length() == 8
                        && isDigits(value)
                        && hasValidCheckDigit(value, true);
            case Barcode.FORMAT_UPC_A:
                return value.length() == 12
                        && isDigits(value)
                        && hasValidCheckDigit(value, true);
            case Barcode.FORMAT_UPC_E:
                return value.length() == 8 && isDigits(value);
            case Barcode.FORMAT_CODE_128:
                return value.length() >= 6;
            default:
                return false;
        }
    }

    private boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private boolean hasValidCheckDigit(String code, boolean weightThreeFirst) {
        int sum = 0;
        for (int i = 0; i < code.length() - 1; i++) {
            int digit = code.charAt(i) - '0';
            boolean useThree = (i % 2 == 0) == weightThreeFirst;
            sum += digit * (useThree ? 3 : 1);
        }
        int expected = (10 - (sum % 10)) % 10;
        return expected == code.charAt(code.length() - 1) - '0';
    }

    private void lookupBarcode(String barcode) {
        scanning = false;
        pendingBarcode = null;
        pendingFrames = 0;
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }
        scannerOverlay.setScanning(false);
        playSuccessTone();

        currentBarcode = barcode;
        currentProduct = null;
        state = STATE_LOOKUP;
        showLookupPanel(barcode);

        ioExecutor.execute(() -> {
            Product product = dbHelper.findProduct(barcode);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (product != null) {
                    showProduct(product);
                } else {
                    showEmpty(barcode);
                }
            });
        });
    }

    private void showProduct(Product product) {
        currentProduct = product;
        currentBarcode = product.barcode;
        state = STATE_PRODUCT;
        pauseScanning();

        productName.setText(product.name);
        productBarcode.setText(getString(R.string.barcode_prefix, product.barcode));
        productPrice.setText(formatPrice(product.price));
        showOnly(panelProduct);
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

    private void showEmpty(String barcode) {
        currentProduct = null;
        currentBarcode = barcode;
        state = STATE_EMPTY;
        pauseScanning();

        emptyBarcode.setText(getString(R.string.barcode_prefix, barcode));
        showOnly(panelEmpty);
    }

    private void showIdlePanel() {
        idleTitle.setText(R.string.status_waiting);
        idleSubtitle.setText(R.string.status_waiting_sub);
        showOnly(panelIdle);
    }

    private void showLookupPanel(String barcode) {
        idleTitle.setText(R.string.status_lookup);
        idleSubtitle.setText(getString(R.string.barcode_prefix, barcode));
        showOnly(panelIdle);
    }

    private void showPermissionPanel() {
        state = STATE_PERMISSION;
        pauseScanning();
        showOnly(panelPermission);
    }

    private void showOnly(View panel) {
        panelIdle.setVisibility(panel == panelIdle ? View.VISIBLE : View.GONE);
        panelProduct.setVisibility(panel == panelProduct ? View.VISIBLE : View.GONE);
        panelEmpty.setVisibility(panel == panelEmpty ? View.VISIBLE : View.GONE);
        panelPermission.setVisibility(panel == panelPermission ? View.VISIBLE : View.GONE);
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
        btnTorch.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, torchOn ? R.color.accent : R.color.text_primary)));
    }

    private void showProductDialog(Product existing) {
        if (currentBarcode == null) {
            return;
        }
        pauseScanning();
        final String barcode = currentBarcode;
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_product, null, false);
        EditText nameInput = content.findViewById(R.id.edit_name);
        EditText priceInput = content.findViewById(R.id.edit_price);
        TextView barcodeView = content.findViewById(R.id.dialog_barcode);
        barcodeView.setText(getString(R.string.barcode_prefix, barcode));

        if (existing != null) {
            nameInput.setText(existing.name);
            nameInput.setSelection(nameInput.getText().length());
            priceInput.setText(formatPrice(existing.price));
        }
        nameInput.requestFocus();

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                .setTitle(existing == null ? R.string.dialog_add_title : R.string.dialog_edit_title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();

        dialog.setOnShowListener(shown -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty()) {
                    nameInput.setError(getString(R.string.error_name));
                    return;
                }

                BigDecimal price = parsePrice(priceInput.getText().toString());
                if (price == null) {
                    priceInput.setError(getString(R.string.error_price));
                    return;
                }

                Product product = new Product(barcode, name, price.doubleValue());
                ioExecutor.execute(() -> {
                    dbHelper.upsertProduct(product);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        dialog.dismiss();
                        Toast.makeText(
                                MainActivity.this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                        showProduct(product);
                    });
                });
            });
        });

        dialog.show();
    }

    private void showManualInputDialog() {
        boolean wasScanning = scanning;
        pauseScanning();

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_barcode, null, false);
        EditText barcodeInput = content.findViewById(R.id.edit_barcode);
        boolean[] submitted = {false};

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                .setTitle(R.string.dialog_manual_title)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_manual_query, null)
                .create();

        dialog.setOnShowListener(shown -> {
            Button query = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            query.setOnClickListener(v -> {
                String code = barcodeInput.getText().toString().trim();
                if (code.length() < 4) {
                    barcodeInput.setError(getString(R.string.error_barcode));
                    return;
                }
                submitted[0] = true;
                dialog.dismiss();
                lookupBarcode(code);
            });
        });

        dialog.setOnDismissListener(ignored -> {
            if (!submitted[0] && wasScanning) {
                startScanning();
            }
        });

        dialog.show();
        barcodeInput.requestFocus();
    }

    private BigDecimal parsePrice(String raw) {
        String value = raw.trim().replace(',', '.');
        if (value.isEmpty()) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return price;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatPrice(double price) {
        return BigDecimal.valueOf(price)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasCameraPermission()) {
            return;
        }
        if (cameraProvider == null) {
            startCamera();
        } else if (state == STATE_IDLE && !scanning) {
            startScanning();
        }
    }

    @Override
    protected void onPause() {
        pauseScanning();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
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
}
