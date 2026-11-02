package com.example.pricescanner;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.RectF;
import android.hardware.camera2.CaptureRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
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
import androidx.core.content.FileProvider;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PriceScanner";
    private static final String PREFS_NAME = "settings";
    private static final String PREF_CHECKOUT = "checkout_mode";

    private static final int MENU_EXPORT_SHARE = 1;
    private static final int MENU_EXPORT_SAVE = 2;
    private static final int MENU_IMPORT = 3;
    private static final int MENU_RESTORE = 4;
    private static final int MENU_ABOUT = 5;

    private static final int STATE_IDLE = 0;
    private static final int STATE_LOOKUP = 1;
    private static final int STATE_PRODUCT = 2;
    private static final int STATE_EMPTY = 3;
    private static final int STATE_PERMISSION = 4;

    private static final int REQUIRED_STABLE_FRAMES = 1;
    private static final int REQUIRED_EMPTY_FRAMES = 3;

    private PreviewView previewView;
    private ScannerOverlayView scannerOverlay;
    private ImageButton btnTorch;
    private ImageButton btnManual;
    private ImageButton btnMore;
    private ImageButton btnCart;
    private TextView cartBadge;
    private TextView idleTitle;
    private TextView idleSubtitle;
    private TextView productName;
    private TextView productBarcode;
    private TextView productPrice;
    private TextView emptyBarcode;
    private TextView btnCheckoutMode;
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
    private SharedPreferences preferences;

    private volatile boolean scanning;
    private boolean cameraStarting;
    private boolean torchOn;
    private boolean checkoutMode;
    private int state = STATE_IDLE;
    private String currentBarcode;
    private Product currentProduct;
    private String pendingBarcode;
    private int pendingFrames;
    private String checkoutWaitBarcode;
    private int checkoutWaitEmptyFrames;
    private long checkoutWaitStartedAt;

    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<String> createDocumentLauncher;
    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private File pendingExportFile;
    private String lastHandledImportUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        bindViews();
        applyWindowInsets();

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        checkoutMode = preferences.getBoolean(PREF_CHECKOUT, false);

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

        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                this::onExportDocumentSelected);

        openDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        startImport(uri);
                    }
                });

        setupClickListeners();
        setupBackNavigation();
        scannerOverlay.setScanning(false);
        updateCheckoutModeUi();
        refreshCartBadge();

        if (hasCameraPermission()) {
            showIdlePanel();
            startCamera();
        } else {
            state = STATE_PERMISSION;
            showPermissionPanel();
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }

        handleIncomingIntent(getIntent());
    }

    private void bindViews() {
        previewView = findViewById(R.id.preview_view);
        scannerOverlay = findViewById(R.id.scanner_overlay);
        btnTorch = findViewById(R.id.btn_torch);
        btnManual = findViewById(R.id.btn_manual);
        btnMore = findViewById(R.id.btn_more);
        btnCart = findViewById(R.id.btn_cart);
        cartBadge = findViewById(R.id.cart_badge);
        idleTitle = findViewById(R.id.idle_title);
        idleSubtitle = findViewById(R.id.idle_subtitle);
        productName = findViewById(R.id.product_name);
        productBarcode = findViewById(R.id.product_barcode);
        productPrice = findViewById(R.id.product_price);
        emptyBarcode = findViewById(R.id.empty_barcode);
        btnCheckoutMode = findViewById(R.id.btn_checkout_mode);
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
        btnMore.setOnClickListener(this::showOverflowMenu);
        btnCart.setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));
        btnCheckoutMode.setOnClickListener(v -> toggleCheckoutMode());
        previewView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                focusAt(event.getX(), event.getY(), true);
                v.performClick();
            }
            return true;
        });

        findViewById(R.id.btn_rescan).setOnClickListener(v -> startScanning());
        findViewById(R.id.btn_rescan_empty).setOnClickListener(v -> startScanning());
        findViewById(R.id.btn_add_cart).setOnClickListener(v -> addCurrentProductToCart());
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

                if (torchOn && camera.getCameraInfo().hasFlashUnit()) {
                    camera.getCameraControl().enableTorch(true);
                }

                previewView.postDelayed(this::focusCenter, 600);

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
        checkoutWaitBarcode = null;
        checkoutWaitEmptyFrames = 0;
        checkoutWaitStartedAt = 0;
        state = STATE_IDLE;
        showIdlePanel();
        scannerOverlay.setScanning(true);
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
        previewView.postDelayed(this::focusCenter, 300);
    }

    private void pauseScanning() {
        scanning = false;
        pendingBarcode = null;
        pendingFrames = 0;
        checkoutWaitBarcode = null;
        checkoutWaitEmptyFrames = 0;
        checkoutWaitStartedAt = 0;
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
                    if (checkoutWaitBarcode != null) {
                        handleCheckoutWait(barcodes, imageProxy);
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

    private void handleCheckoutWait(
            java.util.List<Barcode> barcodes, ImageProxy imageProxy) {
        boolean targetSeen = false;
        for (Barcode barcode : barcodes) {
            String raw = barcode.getRawValue();
            if (raw != null && raw.trim().equals(checkoutWaitBarcode)) {
                targetSeen = true;
                break;
            }
        }
        String inFrame = pickBarcodeInFrame(barcodes, imageProxy);
        if (targetSeen || inFrame != null) {
            checkoutWaitEmptyFrames = 0;
            return;
        }

        checkoutWaitEmptyFrames++;
        boolean enoughEmptyFrames = checkoutWaitEmptyFrames >= REQUIRED_EMPTY_FRAMES;
        boolean enoughTime = System.currentTimeMillis() - checkoutWaitStartedAt >= 2000;
        if (enoughEmptyFrames && enoughTime) {
            checkoutWaitBarcode = null;
            checkoutWaitEmptyFrames = 0;
            checkoutWaitStartedAt = 0;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    showIdlePanel();
                }
            });
        }
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
                    if (checkoutMode) {
                        addProductToCart(product);
                    } else {
                        showProduct(product);
                    }
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
        productPrice.setText(MoneyUtils.formatCents(product.priceCents));
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
        if (checkoutMode) {
            idleTitle.setText(R.string.status_waiting_checkout);
            idleSubtitle.setText(R.string.status_waiting_checkout_sub);
        } else {
            idleTitle.setText(R.string.status_waiting);
            idleSubtitle.setText(R.string.status_waiting_sub);
        }
        updateCheckoutModeUi();
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

    private void focusCenter() {
        if (previewView == null || previewView.getWidth() <= 0) {
            return;
        }
        focusAt(previewView.getWidth() / 2f, previewView.getHeight() * 0.44f, false);
    }

    private void focusAt(float x, float y, boolean tap) {
        if (camera == null) {
            return;
        }
        try {
            MeteringPointFactory factory = previewView.getMeteringPointFactory();
            MeteringPoint point = factory.createPoint(x, y);
            int flags = FocusMeteringAction.FLAG_AF;
            if (tap) {
                flags |= FocusMeteringAction.FLAG_AE;
            }
            FocusMeteringAction action = new FocusMeteringAction.Builder(point, flags)
                    .setAutoCancelDuration(5, TimeUnit.SECONDS)
                    .build();
            camera.getCameraControl().startFocusAndMetering(action);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to focus camera", e);
        }
    }

    private void toggleCheckoutMode() {
        checkoutMode = !checkoutMode;
        preferences.edit().putBoolean(PREF_CHECKOUT, checkoutMode).apply();
        updateCheckoutModeUi();
        if (state == STATE_IDLE) {
            showIdlePanel();
        }
        Toast.makeText(
                this,
                checkoutMode ? R.string.checkout_mode_enabled : R.string.checkout_mode_disabled,
                Toast.LENGTH_SHORT).show();
    }

    private void updateCheckoutModeUi() {
        if (btnCheckoutMode == null) {
            return;
        }
        btnCheckoutMode.setText(checkoutMode
                ? R.string.checkout_mode_on
                : R.string.checkout_mode_off);
        btnCheckoutMode.setContentDescription(getString(R.string.checkout_mode_hint));
    }

    private void addCurrentProductToCart() {
        if (currentProduct == null) {
            return;
        }
        addProductToCart(currentProduct);
    }

    private void addProductToCart(Product product) {
        ioExecutor.execute(() -> {
            dbHelper.addToCart(product);
            ProductDbHelper.CartSummary summary = dbHelper.getCartSummary();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                updateCartBadge(summary.totalQuantity);
                Toast.makeText(
                        MainActivity.this,
                        getString(
                                R.string.toast_added_cart,
                                product.name,
                                MoneyUtils.formatCents(summary.totalCents)),
                        Toast.LENGTH_SHORT).show();
            });
        });
        startBarcodeRemovalWait(product);
    }

    private void startBarcodeRemovalWait(Product product) {
        if (imageAnalysis == null) {
            startScanning();
            return;
        }
        checkoutWaitBarcode = product.barcode;
        checkoutWaitEmptyFrames = 0;
        checkoutWaitStartedAt = System.currentTimeMillis();
        pendingBarcode = null;
        pendingFrames = 0;
        scanning = true;
        state = STATE_IDLE;
        idleTitle.setText(R.string.status_checkout_added);
        idleSubtitle.setText(getString(R.string.status_checkout_locked, product.name));
        updateCheckoutModeUi();
        showOnly(panelIdle);
        scannerOverlay.setScanning(true);
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
        previewView.postDelayed(this::focusCenter, 300);
    }

    private void refreshCartBadge() {
        if (dbHelper == null) {
            return;
        }
        ioExecutor.execute(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            ProductDbHelper.CartSummary summary = dbHelper.getCartSummary();
            runOnUiThread(() -> updateCartBadge(summary.totalQuantity));
        });
    }

    private void updateCartBadge(int totalQuantity) {
        if (cartBadge == null) {
            return;
        }
        if (totalQuantity <= 0) {
            cartBadge.setVisibility(View.GONE);
            return;
        }
        cartBadge.setText(totalQuantity > 99 ? "99+" : String.valueOf(totalQuantity));
        cartBadge.setVisibility(View.VISIBLE);
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

        final String[] brand = {existing != null ? existing.brand : ""};
        final String[] spec = {existing != null ? existing.spec : ""};

        if (existing != null) {
            nameInput.setText(existing.name);
            nameInput.setSelection(nameInput.getText().length());
            priceInput.setText(MoneyUtils.formatCents(existing.priceCents));
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

                Long priceCents = MoneyUtils.parseYuanToCents(priceInput.getText().toString());
                if (priceCents == null) {
                    priceInput.setError(getString(R.string.error_price));
                    return;
                }

                final String finalName = name;
                final long finalPriceCents = priceCents;
                ioExecutor.execute(() -> {
                    Product product = dbHelper.saveProduct(
                            barcode, finalName, brand[0], spec[0],
                            finalPriceCents, "manual");
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        dialog.dismiss();
                        Toast.makeText(
                                MainActivity.this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                        if (checkoutMode) {
                            addProductToCart(product);
                        } else {
                            showProduct(product);
                        }
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

    private void showOverflowMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, MENU_EXPORT_SHARE, 1, R.string.menu_export_share);
        popup.getMenu().add(0, MENU_EXPORT_SAVE, 2, R.string.menu_export_save);
        popup.getMenu().add(0, MENU_IMPORT, 3, R.string.menu_import);
        popup.getMenu().add(0, MENU_RESTORE, 4, R.string.menu_restore);
        popup.getMenu().add(0, MENU_ABOUT, 5, R.string.menu_about);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_EXPORT_SHARE:
                    exportData(false);
                    return true;
                case MENU_EXPORT_SAVE:
                    exportData(true);
                    return true;
                case MENU_IMPORT:
                    openImportPicker();
                    return true;
                case MENU_RESTORE:
                    showBackups();
                    return true;
                case MENU_ABOUT:
                    showAboutDialog();
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void exportData(boolean saveToFile) {
        Toast.makeText(this, R.string.toast_exporting, Toast.LENGTH_SHORT).show();
        ioExecutor.execute(() -> {
            try {
                List<Product> products = dbHelper.getAllProducts();
                List<ProductInfo> info = dbHelper.getAllProductInfo();
                if (products.isEmpty() && info.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(
                            MainActivity.this,
                            R.string.error_export_no_data,
                            Toast.LENGTH_SHORT).show());
                    return;
                }
                String json = DataTransfer.buildJson(
                        products, info, System.currentTimeMillis());
                File file = DataTransfer.writeExportFile(this, json);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (saveToFile) {
                        pendingExportFile = file;
                        createDocumentLauncher.launch(file.getName());
                    } else {
                        shareExportFile(file);
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Export failed", e);
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        R.string.toast_export_failed,
                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void shareExportFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newUri(getContentResolver(), file.getName(), uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.menu_export_share)));
        } catch (Exception e) {
            Log.w(TAG, "Share failed", e);
            Toast.makeText(this, R.string.toast_export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void onExportDocumentSelected(Uri uri) {
        if (uri == null || pendingExportFile == null) {
            return;
        }
        final File file = pendingExportFile;
        pendingExportFile = null;
        ioExecutor.execute(() -> {
            try (InputStream input = new FileInputStream(file);
                 OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) {
                    throw new IllegalStateException("无法写入目标文件");
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        R.string.toast_export_ready,
                        Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.w(TAG, "Save export failed", e);
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        R.string.toast_export_failed,
                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void openImportPicker() {
        openDocumentLauncher.launch(new String[]{
                "application/json", "application/octet-stream", "text/plain"});
    }

    private void startImport(Uri uri) {
        Toast.makeText(this, R.string.toast_importing, Toast.LENGTH_SHORT).show();
        ioExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IllegalStateException("无法读取文件");
                }
                DataTransfer.ImportData data = DataTransfer.parse(
                        DataTransfer.readText(input));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    showImportPreview(data);
                });
            } catch (Exception e) {
                Log.w(TAG, "Import read failed", e);
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        getString(R.string.toast_import_failed, messageOf(e)),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showImportPreview(DataTransfer.ImportData data) {
        ioExecutor.execute(() -> {
            ImportPreview preview = buildImportPreview(data);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                String date = data.exportedAt <= 0
                        ? "未知"
                        : new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(new Date(data.exportedAt));
                String message = getString(
                        R.string.import_message,
                        date,
                        data.products.size(),
                        preview.newCount,
                        preview.updateCount,
                        preview.localOnly,
                        preview.localCount);
                new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                        .setTitle(R.string.import_title)
                        .setMessage(message)
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.import_merge, (dialog, which) ->
                                applyImport(data, false, preview, false))
                        .setNeutralButton(R.string.import_replace, (dialog, which) ->
                                confirmReplace(data, preview))
                        .show();
            });
        });
    }

    private void confirmReplace(DataTransfer.ImportData data, ImportPreview preview) {
        new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                .setTitle(R.string.import_replace)
                .setMessage(getString(
                        R.string.import_replace_confirm, preview.localCount))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.import_replace, (dialog, which) ->
                        applyImport(data, true, preview, false))
                .show();
    }

    private void applyImport(
            DataTransfer.ImportData data,
            boolean replace,
            ImportPreview preview,
            boolean restore) {
        Toast.makeText(this, R.string.toast_importing, Toast.LENGTH_SHORT).show();
        ioExecutor.execute(() -> {
            try {
                List<Product> currentProducts = dbHelper.getAllProducts();
                List<ProductInfo> currentInfo = dbHelper.getAllProductInfo();
                String backup = DataTransfer.buildJson(
                        currentProducts, currentInfo, System.currentTimeMillis());
                DataTransfer.createBackup(this, backup);

                if (replace) {
                    dbHelper.replaceImportProducts(data.products);
                } else {
                    dbHelper.mergeImportProducts(data.products);
                }
                dbHelper.mergeImportProductInfo(data.productInfo);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (restore) {
                        Toast.makeText(
                                MainActivity.this,
                                R.string.backup_done,
                                Toast.LENGTH_SHORT).show();
                    } else if (replace) {
                        Toast.makeText(
                                MainActivity.this,
                                getString(
                                        R.string.toast_import_done_replace,
                                        data.products.size()),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(
                                MainActivity.this,
                                getString(
                                        R.string.toast_import_done,
                                        preview.newCount,
                                        preview.updateCount,
                                        preview.localOnly),
                                Toast.LENGTH_SHORT).show();
                    }
                    startScanning();
                });
            } catch (Exception e) {
                Log.w(TAG, "Import failed", e);
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        getString(R.string.toast_import_failed, messageOf(e)),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private ImportPreview buildImportPreview(DataTransfer.ImportData data) {
        List<Product> localProducts = dbHelper.getAllProducts();
        Map<String, Product> localMap = new HashMap<>();
        for (Product product : localProducts) {
            localMap.put(product.barcode, product);
        }
        int newCount = 0;
        int updateCount = 0;
        for (Product incoming : data.products) {
            Product local = localMap.get(incoming.barcode);
            if (local == null) {
                newCount++;
                continue;
            }
            boolean priceNewer = incoming.priceUpdatedAt >= local.priceUpdatedAt
                    && incoming.priceCents != local.priceCents;
            boolean nameNewer = incoming.nameUpdatedAt >= local.nameUpdatedAt
                    && "manual".equals(incoming.source);
            if (priceNewer || nameNewer) {
                updateCount++;
            }
        }
        int localOnly = 0;
        Set<String> incomingBarcodes = new HashSet<>();
        for (Product incoming : data.products) {
            incomingBarcodes.add(incoming.barcode);
        }
        for (Product local : localProducts) {
            if (!incomingBarcodes.contains(local.barcode)) {
                localOnly++;
            }
        }
        return new ImportPreview(newCount, updateCount, localOnly, localProducts.size());
    }

    private void showBackups() {
        ioExecutor.execute(() -> {
            List<File> backups = DataTransfer.listBackups(this);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (backups.isEmpty()) {
                    Toast.makeText(
                            MainActivity.this,
                            R.string.backup_empty,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] labels = new String[backups.size()];
                SimpleDateFormat format =
                        new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                for (int i = 0; i < backups.size(); i++) {
                    labels[i] = format.format(new Date(backups.get(i).lastModified()));
                }
                new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                        .setTitle(R.string.backup_title)
                        .setMessage(R.string.backup_select)
                        .setItems(labels, (dialog, which) -> restoreBackup(backups.get(which)))
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            });
        });
    }

    private void restoreBackup(File file) {
        ioExecutor.execute(() -> {
            try {
                DataTransfer.ImportData data = DataTransfer.parse(DataTransfer.readFile(file));
                ImportPreview preview = buildImportPreview(data);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    String name = file.getName();
                    new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                            .setTitle(R.string.backup_title)
                            .setMessage(getString(R.string.backup_confirm, name))
                            .setNegativeButton(R.string.action_cancel, null)
                            .setPositiveButton(R.string.backup_restore_action, (dialog, which) ->
                                    applyImport(data, true, preview, true))
                            .show();
                });
            } catch (Exception e) {
                Log.w(TAG, "Restore backup failed", e);
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        getString(R.string.toast_import_failed, messageOf(e)),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showAboutDialog() {
        String message = getString(R.string.about_version, "1.4")
                + "\n" + getString(R.string.about_github)
                + "\n\n" + getString(R.string.settings_about);
        new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                .setTitle(R.string.menu_about)
                .setMessage(message)
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Uri uri = null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            Object extra = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (extra instanceof Uri) {
                uri = (Uri) extra;
            } else if (intent.getClipData() != null
                    && intent.getClipData().getItemCount() > 0) {
                uri = intent.getClipData().getItemAt(0).getUri();
            }
        }
        if (uri != null && !uri.toString().equals(lastHandledImportUri)) {
            lastHandledImportUri = uri.toString();
            startImport(uri);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private static class ImportPreview {
        final int newCount;
        final int updateCount;
        final int localOnly;
        final int localCount;

        ImportPreview(int newCount, int updateCount, int localOnly, int localCount) {
            this.newCount = newCount;
            this.updateCount = updateCount;
            this.localOnly = localOnly;
            this.localCount = localCount;
        }
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCartBadge();
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
