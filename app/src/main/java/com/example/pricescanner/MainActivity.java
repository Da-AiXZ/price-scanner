package com.example.pricescanner;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CaptureRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

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
import androidx.core.view.WindowInsetsControllerCompat;

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
import java.nio.ByteBuffer;
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
    private static final String PREF_CAMERA_REQUESTED = "camera_requested";
    private static final String PREF_TUTORIAL_SEEN = "tutorial_seen";

    private static final int TAB_SCAN = 0;
    private static final int TAB_CART = 1;
    private static final int TAB_LIB = 2;
    private static final int TAB_DATA = 3;

    private static final int REQUIRED_STABLE_FRAMES = 1;

    private static final Interpolator EASE =
            new PathInterpolator(0.22f, 1f, 0.36f, 1f);

    private static final int[] CAT_DRINK = {0xFFE0EFFF, 0xFF1D64D8};
    private static final int[] CAT_DAIRY = {0xFFE2F4FF, 0xFF0B6FA4};
    private static final int[] CAT_SNACK = {0xFFFFE8E6, 0xFFC0392B};
    private static final int[] CAT_INSTANT = {0xFFFFF2D9, 0xFFA96A0A};
    private static final int[] CAT_DAILY = {0xFFECE7FF, 0xFF5B3EC8};
    private static final int[] CAT_DEFAULT = {0xFFF1F1F4, 0xFF4B4B55};

    private FrameLayout root;
    private TextView tbTitle;
    private ImageButton tbAction;
    private FrameLayout tabbar;
    private View tabIndicator;
    private LinearLayout tabScan;
    private LinearLayout tabCart;
    private LinearLayout tabLib;
    private LinearLayout tabData;
    private ImageView tabIconScan;
    private ImageView tabIconCart;
    private ImageView tabIconLib;
    private ImageView tabIconData;
    private TextView tabLabelScan;
    private TextView tabLabelCart;
    private TextView tabLabelLib;
    private TextView tabLabelData;
    private TextView tabBadgeCart;

    private ScrollView viewScan;
    private View viewCart;
    private View viewLib;
    private ScrollView viewData;

    private PreviewView previewView;
    private ScannerOverlayView scannerOverlay;
    private TextView vfLabel;
    private LinearLayout scanButton;
    private ImageView scanButtonIcon;
    private ProgressBar scanButtonSpinner;
    private TextView scanButtonLabel;

    private LinearLayout resultCard;
    private TextView resultThumb;
    private TextView resultName;
    private TextView resultCategory;
    private FrameLayout resultCheckWrap;
    private TextView resultCurrency;
    private TextView resultPrice;
    private TextView resultBarcode;
    private TextView resultPrimary;
    private TextView resultSecondary;

    private ListView cartList;
    private View cartEmpty;
    private TextView cartCountText;
    private TextView cartTotalValue;
    private TextView checkoutBtn;
    private TextView cartClear;

    private EditText libSearch;
    private ListView libList;
    private View libEmpty;
    private TextView libEmptyTitle;
    private TextView libEmptySub;

    private TextView statLib;
    private TextView statCart;
    private TextView statCheckouts;
    private TextView statRevenue;

    private View sheetMask;
    private FrameLayout sheetContainer;
    private LinearLayout sheetPanel;
    private ScrollView sheetScroll;
    private LinearLayout sheetBody;
    private TextView appToast;

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
    private boolean sheetOpen;
    private boolean destroyed;
    private boolean cameraPermissionRequested;
    private int currentTab = -1;

    private String pendingBarcode;
    private int pendingFrames;
    private String currentBarcode;
    private Product resultProduct;

    private float focusDownX;
    private float focusDownY;
    private int darkFrames;
    private long lastLowLightHintAt;

    private long lastCartQuantity;
    private long lastCartTotalCents;
    private int cartDisplayedCents;

    private final List<Product> allProducts = new ArrayList<>();
    private String libraryQuery = "";
    private CartAdapter cartAdapter;
    private ProductAdapter productAdapter;

    private ValueAnimator priceAnimator;
    private ValueAnimator resultHeightAnimator;
    private ValueAnimator cartTotalAnimator;

    private Runnable toastHideRunnable;

    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<String> createDocumentLauncher;
    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private File pendingExportFile;
    private String lastHandledImportUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        }
        setContentView(R.layout.activity_main);

        bindViews();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setupWindowInsets();
        }

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        cameraPermissionRequested = preferences.getBoolean(
                PREF_CAMERA_REQUESTED, false);
        cameraExecutor = Executors.newSingleThreadExecutor();
        ioExecutor = Executors.newSingleThreadExecutor();
        dbHelper = new ProductDbHelper(this);
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
                        startScanSession();
                    } else {
                        showAppToast(getString(R.string.toast_permission_denied));
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

        setupTabs();
        setupScanPage();
        setupCartPage();
        setupLibraryPage();
        setupDataPage();
        setupBackNavigation();

        applyTab(TAB_SCAN, false);
        refreshCartBadge();
        handleIncomingIntent(getIntent());
        maybeShowTutorial(savedInstanceState);
    }

    private void maybeShowTutorial(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            return;
        }
        Intent intent = getIntent();
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action)) {
            return;
        }
        if (preferences.getBoolean(PREF_TUTORIAL_SEEN, false)) {
            return;
        }
        startActivity(new Intent(this, TutorialActivity.class));
    }

    private void setupWindowInsets() {
        View rootView = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(0, bars.top, 0, Math.max(bars.bottom, ime.bottom));
            view.post(() -> {
                if (sheetOpen) {
                    fitSheetHeight();
                    if (ime.bottom > 0) {
                        view.postDelayed(this::ensureFocusedFieldVisible, 80);
                    }
                }
            });
            return insets;
        });

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        ViewCompat.requestApplyInsets(rootView);
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        tbTitle = findViewById(R.id.tb_title);
        tbAction = findViewById(R.id.tb_action);
        tabbar = findViewById(R.id.tabbar);
        tabIndicator = findViewById(R.id.tab_indicator);
        tabScan = findViewById(R.id.tab_scan);
        tabCart = findViewById(R.id.tab_cart);
        tabLib = findViewById(R.id.tab_lib);
        tabData = findViewById(R.id.tab_data);
        tabIconScan = findViewById(R.id.tab_icon_scan);
        tabIconCart = findViewById(R.id.tab_icon_cart);
        tabIconLib = findViewById(R.id.tab_icon_lib);
        tabIconData = findViewById(R.id.tab_icon_data);
        tabLabelScan = findViewById(R.id.tab_label_scan);
        tabLabelCart = findViewById(R.id.tab_label_cart);
        tabLabelLib = findViewById(R.id.tab_label_lib);
        tabLabelData = findViewById(R.id.tab_label_data);
        tabBadgeCart = findViewById(R.id.tab_badge_cart);

        viewScan = findViewById(R.id.view_scan);
        viewCart = findViewById(R.id.view_cart);
        viewLib = findViewById(R.id.view_lib);
        viewData = findViewById(R.id.view_data);

        previewView = findViewById(R.id.preview_view);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        scannerOverlay = findViewById(R.id.scanner_overlay);
        vfLabel = findViewById(R.id.vf_label);
        scanButton = findViewById(R.id.scan_button);
        scanButtonIcon = findViewById(R.id.scan_button_icon);
        scanButtonSpinner = findViewById(R.id.scan_button_spinner);
        scanButtonLabel = findViewById(R.id.scan_button_label);

        resultCard = findViewById(R.id.result_card);
        resultThumb = findViewById(R.id.result_thumb);
        resultName = findViewById(R.id.result_name);
        resultCategory = findViewById(R.id.result_category);
        resultCheckWrap = findViewById(R.id.result_check_wrap);
        resultCurrency = findViewById(R.id.result_currency);
        resultPrice = findViewById(R.id.result_price);
        resultBarcode = findViewById(R.id.result_barcode);
        resultPrimary = findViewById(R.id.result_primary);
        resultSecondary = findViewById(R.id.result_secondary);

        cartList = findViewById(R.id.cart_list);
        cartEmpty = findViewById(R.id.cart_empty);
        cartCountText = findViewById(R.id.cart_count_text);
        cartTotalValue = findViewById(R.id.cart_total_value);
        checkoutBtn = findViewById(R.id.checkout_btn);
        cartClear = findViewById(R.id.cart_clear);

        libSearch = findViewById(R.id.lib_search);
        libList = findViewById(R.id.lib_list);
        libEmpty = findViewById(R.id.lib_empty);
        libEmptyTitle = findViewById(R.id.lib_empty_title);
        libEmptySub = findViewById(R.id.lib_empty_sub);

        statLib = findViewById(R.id.stat_lib);
        statCart = findViewById(R.id.stat_cart);
        statCheckouts = findViewById(R.id.stat_checkouts);
        statRevenue = findViewById(R.id.stat_revenue);

        sheetMask = findViewById(R.id.sheet_mask);
        sheetContainer = findViewById(R.id.sheet_container);
        sheetPanel = findViewById(R.id.sheet_panel);
        sheetScroll = findViewById(R.id.sheet_scroll);
        sheetBody = findViewById(R.id.sheet_body);
        appToast = findViewById(R.id.app_toast);
    }

    private void setupTabs() {
        tabScan.setOnClickListener(v -> setTab(TAB_SCAN));
        tabCart.setOnClickListener(v -> setTab(TAB_CART));
        tabLib.setOnClickListener(v -> setTab(TAB_LIB));
        tabData.setOnClickListener(v -> setTab(TAB_DATA));
        applyPressScale(tabScan, 0.92f);
        applyPressScale(tabCart, 0.92f);
        applyPressScale(tabLib, 0.92f);
        applyPressScale(tabData, 0.92f);
        tabbar.post(() -> updateTabIndicator(false));
    }

    private void applyPressScale(View view, float pressedScale) {
        view.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(pressedScale).scaleY(pressedScale)
                        .setDuration(120).setInterpolator(EASE).start();
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(180)
                        .setInterpolator(new OvershootInterpolator(1.3f))
                        .start();
            }
            return false;
        });
    }

    private void setupScanPage() {
        scanButton.setOnClickListener(v -> startScanSession());
        resultSecondary.setOnClickListener(v -> startScanSession());
        applyPressScale(scanButton, 0.97f);
        applyPressScale(resultPrimary, 0.96f);
        applyPressScale(resultSecondary, 0.96f);
        previewView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                focusDownX = event.getX();
                focusDownY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - focusDownX;
                float dy = event.getY() - focusDownY;
                float slop = dp(24);
                if (event.getPointerCount() == 1 && dx * dx + dy * dy < slop * slop) {
                    focusAt(event.getX(), event.getY(), true);
                    scannerOverlay.showFocusRing(event.getX(), event.getY());
                    v.performClick();
                }
            }
            return true;
        });
        resultName.setOnClickListener(v -> {
            if (resultProduct != null) {
                showProductSheet(resultProduct.barcode, resultProduct, false);
            }
        });
        resultThumb.setOnClickListener(v -> {
            if (resultProduct != null) {
                showProductSheet(resultProduct.barcode, resultProduct, false);
            }
        });
    }

    private void setupCartPage() {
        cartAdapter = new CartAdapter();
        cartList.setAdapter(cartAdapter);
        cartClear.setOnClickListener(v -> clearCartWithAnimation());
        checkoutBtn.setOnClickListener(v -> checkout());
        checkoutBtn.setClickable(true);
        checkoutBtn.setAlpha(0.4f);
        applyPressScale(checkoutBtn, 0.96f);

        View.OnClickListener goScan = v -> setTab(TAB_SCAN);
        View emptyScan = findViewById(R.id.cart_empty_scan);
        emptyScan.setOnClickListener(goScan);
    }

    private void setupLibraryPage() {
        productAdapter = new ProductAdapter();
        libList.setAdapter(productAdapter);
        libList.setOnItemClickListener((parent, view, position, id) ->
                showProductSheet(
                        productAdapter.getItem(position).barcode,
                        productAdapter.getItem(position),
                        false));
        libSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                libraryQuery = s.toString().trim();
                applyLibraryFilter();
            }
        });
    }

    private void setupDataPage() {
        findViewById(R.id.action_tutorial).setOnClickListener(v ->
                startActivity(new Intent(this, TutorialActivity.class)));
        findViewById(R.id.action_export).setOnClickListener(v -> showExportSheet());
        findViewById(R.id.action_import).setOnClickListener(v -> openImportPicker());
        findViewById(R.id.action_restore).setOnClickListener(v -> showBackupsSheet());
        findViewById(R.id.action_about).setOnClickListener(v -> showAboutSheet());
        sheetMask.setOnClickListener(v -> closeSheet());
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (sheetOpen) {
                    closeSheet();
                } else if (currentTab != TAB_SCAN) {
                    setTab(TAB_SCAN);
                } else {
                    finish();
                }
            }
        });
    }

    private void setTab(int tab) {
        if (tab == currentTab) {
            refreshTab(tab);
            return;
        }
        applyTab(tab, true);
    }

    private void applyTab(int tab, boolean animate) {
        int oldTab = currentTab;
        if (tab != TAB_SCAN) {
            if (scanning) {
                stopScanSession();
            } else {
                stopCamera();
            }
        }
        View newView = tabView(tab);
        for (int i = 0; i < 4; i++) {
            tabView(i).setVisibility(i == tab ? View.VISIBLE : View.GONE);
        }

        if (animate && oldTab >= 0 && oldTab != tab) {
            newView.setAlpha(0f);
            newView.setTranslationX((tab > oldTab ? 1 : -1) * dp(30));
            newView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(320)
                    .setInterpolator(EASE)
                    .start();
        } else {
            newView.setAlpha(1f);
            newView.setTranslationX(0f);
        }

        currentTab = tab;
        CharSequence title = getString(titleForTab(tab));
        if (!tbTitle.getText().toString().contentEquals(title)) {
            tbTitle.setText(title);
            animateTitle();
        }
        updateTopAction();
        updateTabColors();
        updateTabIndicator(animate);
        refreshTab(tab);
    }

    private View tabView(int tab) {
        switch (tab) {
            case TAB_CART:
                return viewCart;
            case TAB_LIB:
                return viewLib;
            case TAB_DATA:
                return viewData;
            default:
                return viewScan;
        }
    }

    private int titleForTab(int tab) {
        switch (tab) {
            case TAB_CART:
                return R.string.title_cart_tab;
            case TAB_LIB:
                return R.string.title_products;
            case TAB_DATA:
                return R.string.title_data;
            default:
                return R.string.title_scan;
        }
    }

    private void animateTitle() {
        tbTitle.animate().cancel();
        tbTitle.setAlpha(0.3f);
        tbTitle.setTranslationY(dp(5));
        tbTitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260)
                .setInterpolator(EASE)
                .start();
    }

    private void refreshTab(int tab) {
        switch (tab) {
            case TAB_CART:
                loadCart();
                break;
            case TAB_LIB:
                loadLibrary();
                break;
            case TAB_DATA:
                loadStats();
                break;
            default:
                break;
        }
    }

    private void updateTopAction() {
        switch (currentTab) {
            case TAB_SCAN:
                tbAction.setVisibility(View.VISIBLE);
                tbAction.setImageResource(R.drawable.ic_bolt);
                tbAction.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                        this, torchOn ? R.color.blue : R.color.ink2)));
                tbAction.setContentDescription(getString(R.string.hint_torch));
                tbAction.setOnClickListener(v -> toggleTorch());
                break;
            case TAB_CART:
                tbAction.setVisibility(View.VISIBLE);
                tbAction.setImageResource(R.drawable.ic_trash);
                tbAction.setImageTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.ink2)));
                tbAction.setContentDescription(getString(R.string.cart_clear));
                tbAction.setOnClickListener(v -> clearCartWithAnimation());
                break;
            case TAB_LIB:
                tbAction.setVisibility(View.VISIBLE);
                tbAction.setImageResource(R.drawable.ic_plus);
                tbAction.setImageTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.ink2)));
                tbAction.setContentDescription(getString(R.string.sheet_new_product));
                tbAction.setOnClickListener(v -> showPopupMenu());
                break;
            default:
                tbAction.setVisibility(View.GONE);
                tbAction.setOnClickListener(null);
                break;
        }
    }

    private void updateTabColors() {
        int active = ContextCompat.getColor(this, R.color.blue);
        int inactive = ContextCompat.getColor(this, R.color.tab_inactive);
        ImageView[] icons = {tabIconScan, tabIconCart, tabIconLib, tabIconData};
        TextView[] labels = {tabLabelScan, tabLabelCart, tabLabelLib, tabLabelData};
        for (int i = 0; i < 4; i++) {
            int color = i == currentTab ? active : inactive;
            icons[i].setImageTintList(ColorStateList.valueOf(color));
            labels[i].setTextColor(color);
        }
    }

    private void updateTabIndicator(boolean animate) {
        int width = tabbar.getWidth();
        if (width <= 0) {
            return;
        }
        int itemWidth = (width - dpInt(16)) / 4;
        ViewGroup.LayoutParams params = tabIndicator.getLayoutParams();
        if (params.width != itemWidth) {
            params.width = itemWidth;
            tabIndicator.setLayoutParams(params);
        }
        float target = Math.max(0, currentTab) * itemWidth;
        if (animate) {
            tabIndicator.animate()
                    .translationX(target)
                    .setDuration(460)
                    .setInterpolator(new OvershootInterpolator(0.9f))
                    .start();
        } else {
            tabIndicator.setTranslationX(target);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        if (hasCameraPermission()) {
            startScanSession();
            return;
        }
        if (!cameraPermissionRequested
                || shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            cameraPermissionRequested = true;
            preferences.edit()
                    .putBoolean(PREF_CAMERA_REQUESTED, true)
                    .apply();
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
            showAppToast(getString(R.string.toast_permission_denied));
        }
    }

    private void startCamera() {
        if (!hasCameraPermission() || cameraStarting) {
            return;
        }
        if (camera != null && imageAnalysis != null) {
            if (scanning) {
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
            }
            return;
        }
        if (cameraProvider != null) {
            bindCamera();
            return;
        }
        cameraStarting = true;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            cameraStarting = false;
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (Exception e) {
                handleCameraError(e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (cameraProvider == null) {
            return;
        }
        try {
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
            previewView.setVisibility(View.VISIBLE);

            if (torchOn && camera.getCameraInfo().hasFlashUnit()) {
                camera.getCameraControl().enableTorch(true);
            }
            previewView.postDelayed(this::focusCenter, 600);
            if (scanning) {
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
            }
        } catch (Exception e) {
            handleCameraError(e);
        }
    }

    private void handleCameraError(Exception e) {
        Log.e(TAG, "Unable to start camera", e);
        scanning = false;
        stopCamera();
        resetScanUi();
        vfLabel.setText(R.string.status_no_camera);
        showAppToast(getString(R.string.toast_camera_error));
    }

    private void stopCamera() {
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        camera = null;
        imageAnalysis = null;
        if (previewView != null) {
            previewView.setVisibility(View.GONE);
        }
        if (torchOn) {
            torchOn = false;
            if (currentTab == TAB_SCAN) {
                updateTopAction();
            }
        }
    }

    private void startScanSession() {
        if (!hasCameraPermission()) {
            requestCameraPermission();
            return;
        }
        hideResultCard();
        scanning = true;
        pendingBarcode = null;
        pendingFrames = 0;
        previewView.setVisibility(View.VISIBLE);
        scannerOverlay.setMode(ScannerOverlayView.MODE_SCANNING);
        vfLabel.setText(R.string.scanning_label);
        vfLabel.setTextColor(Color.parseColor("#6EE7B7"));
        scanButton.setClickable(false);
        scanButtonIcon.setVisibility(View.GONE);
        scanButtonSpinner.setVisibility(View.VISIBLE);
        scanButtonLabel.setText(R.string.action_scanning);
        startCamera();
        previewView.postDelayed(this::focusCenter, 200);
    }

    private void stopScanSession() {
        scanning = false;
        pendingBarcode = null;
        pendingFrames = 0;
        stopCamera();
        resetScanUi();
    }

    private void resetScanUi() {
        scannerOverlay.setMode(ScannerOverlayView.MODE_IDLE);
        vfLabel.setText(R.string.scan_hint_aim);
        vfLabel.setTextColor(Color.parseColor("#88FFFFFF"));
        scanButton.setClickable(true);
        scanButtonIcon.setVisibility(View.VISIBLE);
        scanButtonSpinner.setVisibility(View.GONE);
        scanButtonLabel.setText(R.string.action_start_scan);
        hideResultCard();
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
                    String value = BarcodePicker.pickInFrame(
                            barcodes,
                            imageProxy,
                            previewView.getWidth(),
                            previewView.getHeight(),
                            scannerOverlay.getFrameRect());
                    if (value == null) {
                        pendingBarcode = null;
                        pendingFrames = 0;
                        checkLowLight(imageProxy);
                        return;
                    }
                    darkFrames = 0;
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

    private void onBarcodeDetected(String barcode) {
        if (!scanning) {
            return;
        }
        scanning = false;
        pendingBarcode = null;
        pendingFrames = 0;

        playSuccessTone();
        vibrate();
        scannerOverlay.playSuccess();
        vfLabel.setText(R.string.scanning_label);
        stopCamera();

        currentBarcode = barcode;
        ioExecutor.execute(() -> {
            Product product = dbHelper.findProduct(barcode);
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                showResult(barcode, product);
            });
        });
    }

    private void showResult(String barcode, Product product) {
        resultBarcode.setText(barcode);
        resultProduct = product;

        if (product != null) {
            resultName.setText(product.name);
            resultCategory.setText(categoryLabel(product.category));
            resultCurrency.setVisibility(View.VISIBLE);
            resultPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
            resultPrice.setTextColor(ContextCompat.getColor(this, R.color.ink));
            animatePrice(product.priceCents);
            resultCheckWrap.setVisibility(View.VISIBLE);
            resultCheckWrap.setAlpha(0f);
            resultCheckWrap.setScaleX(0.4f);
            resultCheckWrap.setScaleY(0.4f);
            resultCheckWrap.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(120)
                    .setDuration(380)
                    .setInterpolator(new OvershootInterpolator(1.5f))
                    .start();
            applyThumb(resultThumb, product.name, product.category, 14);
            resultPrimary.setText(R.string.action_add_to_cart);
            resultPrimary.setBackgroundResource(R.drawable.bg_btn_primary);
            resultPrimary.setTextColor(ContextCompat.getColor(this, R.color.white));
            resultPrimary.setOnClickListener(v -> addToCart(product, resultPrimary));
            vfLabel.setText(getString(R.string.scan_ok, product.name));
            vfLabel.setTextColor(Color.parseColor("#6EE7B7"));
        } else {
            resultName.setText(R.string.result_not_found);
            resultCategory.setText(R.string.result_not_found_category);
            resultCurrency.setVisibility(View.GONE);
            resultPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            resultPrice.setText("—");
            resultPrice.setTextColor(ContextCompat.getColor(this, R.color.ink3));
            if (priceAnimator != null) {
                priceAnimator.cancel();
            }
            resultCheckWrap.setVisibility(View.GONE);
            applyThumb(resultThumb, "?", "", 14);
            resultPrimary.setText(R.string.action_enter_price);
            resultPrimary.setBackgroundResource(R.drawable.bg_btn_primary);
            resultPrimary.setTextColor(ContextCompat.getColor(this, R.color.white));
            resultPrimary.setOnClickListener(v ->
                    showProductSheet(barcode, null, true));
            vfLabel.setText(getString(R.string.scan_ok, barcode));
            vfLabel.setTextColor(Color.parseColor("#6EE7B7"));
        }
        scanButton.setClickable(true);
        scanButtonIcon.setVisibility(View.VISIBLE);
        scanButtonSpinner.setVisibility(View.GONE);
        scanButtonLabel.setText(R.string.action_start_scan);
        expandResultCard();
    }

    private void animatePrice(long cents) {
        if (priceAnimator != null) {
            priceAnimator.cancel();
        }
        int target = (int) Math.min(cents, 99999999L);
        priceAnimator = ValueAnimator.ofInt(0, target);
        priceAnimator.setDuration(620);
        priceAnimator.setInterpolator(EASE);
        priceAnimator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            resultPrice.setText(MoneyUtils.formatCents(value));
        });
        priceAnimator.start();
    }

    private void expandResultCard() {
        if (resultCard.getVisibility() == View.VISIBLE) {
            ViewGroup.LayoutParams params = resultCard.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            resultCard.setLayoutParams(params);
            return;
        }
        if (resultHeightAnimator != null) {
            resultHeightAnimator.cancel();
        }
        int width = resultCard.getWidth();
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels - dpInt(32);
        }
        ViewGroup.LayoutParams params = resultCard.getLayoutParams();
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        resultCard.setVisibility(View.VISIBLE);
        resultCard.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int target = resultCard.getMeasuredHeight();
        params.height = 0;
        resultCard.setLayoutParams(params);
        resultCard.setAlpha(0f);
        resultCard.setTranslationY(dp(10));
        resultCard.animate().alpha(1f).translationY(0f)
                .setDuration(420).setInterpolator(EASE).start();

        resultHeightAnimator = ValueAnimator.ofInt(0, target);
        resultHeightAnimator.setDuration(440);
        resultHeightAnimator.setInterpolator(EASE);
        resultHeightAnimator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            ViewGroup.LayoutParams lp = resultCard.getLayoutParams();
            lp.height = value;
            resultCard.setLayoutParams(lp);
        });
        resultHeightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                ViewGroup.LayoutParams lp = resultCard.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                resultCard.setLayoutParams(lp);
            }
        });
        resultHeightAnimator.start();

        viewScan.postDelayed(() -> {
            if (resultCard.getVisibility() != View.VISIBLE || scanning) {
                return;
            }
            int bottom = resultCard.getBottom() + dpInt(16);
            int targetScroll = Math.max(0, bottom - viewScan.getHeight());
            viewScan.smoothScrollTo(0, targetScroll);
        }, 520);
    }

    private void hideResultCard() {
        if (priceAnimator != null) {
            priceAnimator.cancel();
            priceAnimator = null;
        }
        if (resultCard.getVisibility() != View.VISIBLE || resultCard.getHeight() <= 0) {
            resultCard.setVisibility(View.GONE);
            return;
        }
        if (resultHeightAnimator != null) {
            resultHeightAnimator.cancel();
        }
        int start = resultCard.getHeight();
        resultCard.animate().alpha(0f).translationY(dp(8))
                .setDuration(220).start();
        resultHeightAnimator = ValueAnimator.ofInt(start, 0);
        resultHeightAnimator.setDuration(260);
        resultHeightAnimator.setInterpolator(EASE);
        resultHeightAnimator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            ViewGroup.LayoutParams lp = resultCard.getLayoutParams();
            lp.height = value;
            resultCard.setLayoutParams(lp);
        });
        resultHeightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                ViewGroup.LayoutParams lp = resultCard.getLayoutParams();
                lp.height = 0;
                resultCard.setLayoutParams(lp);
                resultCard.setVisibility(View.GONE);
            }
        });
        resultHeightAnimator.start();
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

    private void focusCenter() {
        if (previewView == null || previewView.getWidth() <= 0) {
            return;
        }
        focusAt(previewView.getWidth() / 2f, previewView.getHeight() / 2f, false);
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

    private void toggleTorch() {
        if (camera == null) {
            showAppToast(getString(R.string.toast_start_scan_first));
            return;
        }
        if (!camera.getCameraInfo().hasFlashUnit()) {
            showAppToast(getString(R.string.toast_no_flash));
            return;
        }
        torchOn = !torchOn;
        camera.getCameraControl().enableTorch(torchOn);
        updateTopAction();
        showAppToast(getString(
                torchOn ? R.string.toast_torch_on : R.string.toast_torch_off));
    }

    private void checkLowLight(ImageProxy imageProxy) {
        if (torchOn || camera == null) {
            darkFrames = 0;
            return;
        }
        if (averageLuma(imageProxy) >= 45) {
            darkFrames = 0;
            return;
        }
        darkFrames++;
        long now = System.currentTimeMillis();
        if (darkFrames >= 30 && now - lastLowLightHintAt >= 60000) {
            darkFrames = 0;
            lastLowLightHintAt = now;
            runOnUiThread(() -> {
                if (!isFinishing() && !destroyed && scanning) {
                    showAppToast(getString(R.string.toast_low_light));
                }
            });
        }
    }

    private int averageLuma(ImageProxy imageProxy) {
        if (imageProxy.getPlanes().length == 0) {
            return 128;
        }
        ImageProxy.PlaneProxy plane = imageProxy.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();
        long sum = 0;
        int count = 0;
        for (int y = 0; y < height; y += 16) {
            int rowStart = y * rowStride;
            for (int x = 0; x < width; x += 16) {
                int index = rowStart + x * pixelStride;
                if (index >= buffer.limit()) {
                    break;
                }
                sum += buffer.get(index) & 0xFF;
                count++;
            }
        }
        return count == 0 ? 128 : (int) (sum / count);
    }

    private String categoryLabel(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "其他";
        }
        return category.trim();
    }

    private int[] categoryColors(String category) {
        switch (categoryLabel(category)) {
            case "饮料":
                return CAT_DRINK;
            case "乳品":
                return CAT_DAIRY;
            case "零食":
                return CAT_SNACK;
            case "方便食品":
                return CAT_INSTANT;
            case "日化":
                return CAT_DAILY;
            default:
                return CAT_DEFAULT;
        }
    }

    private void applyThumb(TextView view, String name, String category, float radiusDp) {
        int[] colors = categoryColors(category);
        GradientDrawable background = new GradientDrawable();
        background.setColor(colors[0]);
        background.setCornerRadius(dp(radiusDp));
        view.setBackground(background);
        view.setTextColor(colors[1]);
        view.setText(name == null || name.isEmpty() ? "?" : name.substring(0, 1));
    }

    private void addToCart(Product product, TextView feedbackButton) {
        ioExecutor.execute(() -> {
            dbHelper.addToCart(product);
            ProductDbHelper.CartSummary summary = dbHelper.getCartSummary();
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                updateCartBadge(summary.totalQuantity, true);
                showAppToast(getString(R.string.toast_added_cart_short, product.name));
                if (feedbackButton != null) {
                    feedbackButton.setText(R.string.toast_added);
                    feedbackButton.setBackgroundResource(R.drawable.bg_btn_done);
                    feedbackButton.postDelayed(() -> {
                        if (feedbackButton == resultPrimary && resultProduct != null) {
                            feedbackButton.setText(R.string.action_add_to_cart);
                            feedbackButton.setBackgroundResource(R.drawable.bg_btn_primary);
                        }
                    }, 1000);
                }
            });
        });
    }

    private void updateCartBadge(int totalQuantity, boolean pop) {
        if (tabBadgeCart == null) {
            return;
        }
        if (totalQuantity <= 0) {
            tabBadgeCart.setVisibility(View.GONE);
            return;
        }
        tabBadgeCart.setText(totalQuantity > 99 ? "99+" : String.valueOf(totalQuantity));
        tabBadgeCart.setVisibility(View.VISIBLE);
        if (pop) {
            tabBadgeCart.setScaleX(0.5f);
            tabBadgeCart.setScaleY(0.5f);
            tabBadgeCart.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(360)
                    .setInterpolator(new OvershootInterpolator(1.6f))
                    .start();
        }
    }

    private void refreshCartBadge() {
        if (dbHelper == null) {
            return;
        }
        ioExecutor.execute(() -> {
            ProductDbHelper.CartSummary summary = dbHelper.getCartSummary();
            runOnUiThread(() -> {
                if (!destroyed && !isFinishing()) {
                    updateCartBadge(summary.totalQuantity, false);
                }
            });
        });
    }

    private void loadCart() {
        ioExecutor.execute(() -> {
            List<CartItem> items = dbHelper.getCartItems();
            ProductDbHelper.CartSummary summary = dbHelper.getCartSummary();
            List<Product> products = dbHelper.getAllProducts();
            Map<String, Product> productMap = new HashMap<>();
            for (Product product : products) {
                productMap.put(product.barcode, product);
            }
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                cartAdapter.setItems(items, productMap);
                lastCartQuantity = summary.totalQuantity;
                lastCartTotalCents = summary.totalCents;
                cartCountText.setText(getString(
                        R.string.cart_count, summary.totalQuantity));
                animateCartTotal(summary.totalCents);
                cartList.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                cartEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                checkoutBtn.setAlpha(summary.totalQuantity == 0 ? 0.4f : 1f);
                checkoutBtn.setClickable(summary.totalQuantity > 0);
                updateCartBadge(summary.totalQuantity, false);
            });
        });
    }

    private void animateCartTotal(long cents) {
        if (cartTotalAnimator != null) {
            cartTotalAnimator.cancel();
        }
        int target = (int) Math.min(cents, 99999999L);
        cartTotalAnimator = ValueAnimator.ofInt(cartDisplayedCents, target);
        cartTotalAnimator.setDuration(420);
        cartTotalAnimator.setInterpolator(EASE);
        cartTotalAnimator.addUpdateListener(animation ->
                cartTotalValue.setText(MoneyUtils.formatCents(
                        (int) animation.getAnimatedValue())));
        cartTotalAnimator.start();
        cartDisplayedCents = target;
    }

    private void changeCartQuantity(String barcode, int quantity, View qtyView) {
        if (qtyView != null) {
            qtyView.setScaleX(1.32f);
            qtyView.setScaleY(1.32f);
            qtyView.animate().scaleX(1f).scaleY(1f)
                    .setDuration(360)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
        }
        ioExecutor.execute(() -> {
            dbHelper.updateCartQuantity(barcode, quantity);
            runOnUiThread(() -> {
                if (!destroyed && !isFinishing()) {
                    loadCart();
                }
            });
        });
    }

    private void clearCartWithAnimation() {
        if (lastCartQuantity == 0) {
            showAppToast(getString(R.string.toast_checkout_empty));
            return;
        }
        cartList.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            ioExecutor.execute(() -> {
                dbHelper.clearCart();
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) {
                        return;
                    }
                    loadCart();
                    cartList.setAlpha(1f);
                    showAppToast(getString(R.string.toast_cart_cleared));
                });
            });
        }).start();
    }

    private void checkout() {
        if (lastCartQuantity == 0) {
            showAppToast(getString(R.string.toast_checkout_empty));
            return;
        }
        final long total = lastCartTotalCents;
        final int quantity = (int) lastCartQuantity;
        checkoutBtn.setClickable(false);
        checkoutBtn.setText(R.string.action_checkout_busy);
        checkoutBtn.animate().scaleX(0.97f).scaleY(0.97f).setDuration(120).start();
        checkoutBtn.postDelayed(() -> ioExecutor.execute(() -> {
            dbHelper.recordCheckout(total, quantity);
            dbHelper.clearCart();
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                loadCart();
                loadStats();
                checkoutBtn.setBackgroundResource(R.drawable.bg_btn_done);
                checkoutBtn.setText(R.string.action_checkout_done);
                checkoutBtn.animate().scaleX(1f).scaleY(1f)
                        .setDuration(180).start();
                showAppToast(getString(
                        R.string.toast_checkout_done,
                        MoneyUtils.formatCents(total)));
                checkoutBtn.postDelayed(() -> {
                    checkoutBtn.setBackgroundResource(R.drawable.bg_btn_dark);
                    checkoutBtn.setText(R.string.cart_checkout);
                }, 1400);
            });
        }), 350);
    }

    private void loadLibrary() {
        ioExecutor.execute(() -> {
            List<Product> products = dbHelper.getAllProducts();
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                allProducts.clear();
                allProducts.addAll(products);
                applyLibraryFilter();
            });
        });
    }

    private void applyLibraryFilter() {
        List<Product> filtered = new ArrayList<>();
        for (Product product : allProducts) {
            if (product.matches(libraryQuery)) {
                filtered.add(product);
            }
        }
        productAdapter.setItems(filtered);
        if (allProducts.isEmpty()) {
            libEmptyTitle.setText(R.string.products_empty);
            libEmptySub.setText(R.string.products_empty_sub);
        } else if (filtered.isEmpty()) {
            libEmptyTitle.setText(R.string.products_no_match);
            libEmptySub.setText(R.string.products_no_match_sub);
        }
        libEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        libList.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showPopupMenu() {
        View content = LayoutInflater.from(this)
                .inflate(R.layout.menu_popup, null, false);
        PopupWindow popup = new PopupWindow(
                content, dpInt(190), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(12));
        content.findViewById(R.id.pop_scan).setOnClickListener(v -> {
            popup.dismiss();
            startActivity(new Intent(this, ScanAddActivity.class));
        });
        content.findViewById(R.id.pop_manual).setOnClickListener(v -> {
            popup.dismiss();
            showProductSheet("", null, false);
        });
        popup.showAsDropDown(tbAction, -dpInt(150), dpInt(6));
    }

    private void showProductSheet(String barcode, Product existing, boolean fromScan) {
        View content = showSheet(R.layout.sheet_product);
        TextView title = content.findViewById(R.id.sheet_title);
        TextView sub = content.findViewById(R.id.sheet_sub);
        EditText barcodeInput = content.findViewById(R.id.edit_barcode);
        EditText nameInput = content.findViewById(R.id.edit_name);
        EditText priceInput = content.findViewById(R.id.edit_price);
        EditText categoryInput = content.findViewById(R.id.edit_category);
        TextView deleteButton = content.findViewById(R.id.btn_delete);

        final boolean isNew = existing == null;
        final boolean canEditBarcode = isNew && !fromScan;

        title.setText(isNew ? R.string.sheet_new_product : R.string.sheet_edit_product);
        if (fromScan) {
            sub.setVisibility(View.VISIBLE);
            sub.setText(R.string.sheet_scan_product_sub);
        } else if (existing != null) {
            sub.setVisibility(View.VISIBLE);
            sub.setText(R.string.sheet_product_sub);
        } else {
            sub.setVisibility(View.GONE);
        }

        String initialBarcode = existing != null ? existing.barcode : barcode;
        barcodeInput.setText(initialBarcode);
        barcodeInput.setEnabled(canEditBarcode);
        if (!canEditBarcode) {
            barcodeInput.setTextColor(ContextCompat.getColor(this, R.color.ink3));
        }
        if (existing != null) {
            nameInput.setText(existing.name);
            nameInput.setSelection(nameInput.getText().length());
            priceInput.setText(MoneyUtils.formatCents(existing.priceCents));
            categoryInput.setText(existing.category);
        }
        deleteButton.setVisibility(existing == null ? View.GONE : View.VISIBLE);

        content.findViewById(R.id.btn_cancel).setOnClickListener(v -> closeSheet());
        content.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String code = barcodeInput.getText().toString().trim();
            String name = nameInput.getText().toString().trim();
            String category = categoryInput.getText().toString().trim();
            if (!code.matches("\\d{6,14}")) {
                showAppToast(getString(R.string.error_barcode_digits));
                return;
            }
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

            final String finalName = name;
            final String finalCategory = category;
            final long finalPrice = priceCents;
            final String finalCode = code;

            ioExecutor.execute(() -> {
                if (isNew && dbHelper.findProduct(finalCode) != null) {
                    runOnUiThread(() -> showAppToast(
                            getString(R.string.error_barcode_exists)));
                    return;
                }
                Product saved = dbHelper.saveProduct(
                        finalCode,
                        finalName,
                        existing != null ? existing.brand : "",
                        existing != null ? existing.spec : "",
                        finalPrice,
                        "manual",
                        finalCategory);
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) {
                        return;
                    }
                    closeSheet();
                    loadLibrary();
                    loadStats();
                    if (resultProduct != null
                            && resultProduct.barcode.equals(saved.barcode)) {
                        showResult(saved.barcode, saved);
                    } else if (fromScan && currentTab == TAB_SCAN) {
                        showResult(saved.barcode, saved);
                    }
                    showAppToast(getString(R.string.toast_saved));
                });
            });
        });

        deleteButton.setOnClickListener(v -> {
            if (existing != null) {
                confirmDeleteProduct(existing);
            }
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
                                allProducts.removeIf(item ->
                                        item.barcode.equals(product.barcode));
                                applyLibraryFilter();
                                loadStats();
                                if (resultProduct != null
                                        && resultProduct.barcode.equals(product.barcode)) {
                                    resultProduct = null;
                                    hideResultCard();
                                    scannerOverlay.setMode(ScannerOverlayView.MODE_IDLE);
                                    vfLabel.setText(R.string.scan_hint_aim);
                                }
                                showAppToast(getString(
                                        R.string.toast_product_deleted,
                                        product.name));
                            });
                        }))
                .show();
    }

    private void loadStats() {
        ioExecutor.execute(() -> {
            int productCount = dbHelper.getAllProducts().size();
            ProductDbHelper.CartSummary cart = dbHelper.getCartSummary();
            ProductDbHelper.CheckoutStats checkout = dbHelper.getCheckoutStats();
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                statLib.setText(String.valueOf(productCount));
                statCart.setText(String.valueOf(cart.totalQuantity));
                statCheckouts.setText(String.valueOf(checkout.count));
                statRevenue.setText("¥" + MoneyUtils.formatCents(checkout.totalCents));
            });
        });
    }

    private void showExportSheet() {
        ioExecutor.execute(() -> {
            int count = dbHelper.getAllProducts().size();
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                View content = showSheet(R.layout.sheet_export);
                TextView sub = content.findViewById(R.id.export_sub);
                sub.setText(getString(R.string.sheet_export_sub, count));
                content.findViewById(R.id.export_share).setOnClickListener(v -> {
                    closeSheet();
                    exportData(false);
                });
                content.findViewById(R.id.export_save).setOnClickListener(v -> {
                    closeSheet();
                    exportData(true);
                });
                content.findViewById(R.id.export_cancel).setOnClickListener(
                        v -> closeSheet());
            });
        });
    }

    private void exportData(boolean saveToFile) {
        showAppToast(getString(R.string.toast_exporting));
        ioExecutor.execute(() -> {
            try {
                List<Product> products = dbHelper.getAllProducts();
                List<ProductInfo> info = dbHelper.getAllProductInfo();
                if (products.isEmpty() && info.isEmpty()) {
                    runOnUiThread(() -> showAppToast(
                            getString(R.string.error_export_no_data)));
                    return;
                }
                String json = DataTransfer.buildJson(
                        products, info, System.currentTimeMillis());
                File file = DataTransfer.writeExportFile(this, json);
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) {
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
                runOnUiThread(() -> showAppToast(
                        getString(R.string.toast_export_failed)));
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
            startActivity(Intent.createChooser(
                    send, getString(R.string.action_share_family)));
        } catch (Exception e) {
            Log.w(TAG, "Share failed", e);
            showAppToast(getString(R.string.toast_export_failed));
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
                runOnUiThread(() -> showAppToast(
                        getString(R.string.toast_export_ready)));
            } catch (Exception e) {
                Log.w(TAG, "Save export failed", e);
                runOnUiThread(() -> showAppToast(
                        getString(R.string.toast_export_failed)));
            }
        });
    }

    private void openImportPicker() {
        openDocumentLauncher.launch(new String[]{
                "application/json", "application/octet-stream", "text/plain"});
    }

    private void startImport(Uri uri) {
        showAppToast(getString(R.string.toast_importing));
        ioExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IllegalStateException("无法读取文件");
                }
                DataTransfer.ImportData data = DataTransfer.parse(
                        DataTransfer.readText(input));
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) {
                        return;
                    }
                    showImportPreview(data);
                });
            } catch (Exception e) {
                Log.w(TAG, "Import read failed", e);
                runOnUiThread(() -> showAppToast(getString(
                        R.string.toast_import_failed, messageOf(e))));
            }
        });
    }

    private void showImportPreview(DataTransfer.ImportData data) {
        ioExecutor.execute(() -> {
            ImportPreview preview = buildImportPreview(data);
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
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
        showAppToast(getString(R.string.toast_importing));
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
                    if (destroyed || isFinishing()) {
                        return;
                    }
                    if (restore) {
                        showAppToast(getString(R.string.backup_done));
                    } else if (replace) {
                        showAppToast(getString(
                                R.string.toast_import_done_replace,
                                data.products.size()));
                    } else {
                        showAppToast(getString(
                                R.string.toast_import_done,
                                preview.newCount,
                                preview.updateCount,
                                preview.localOnly));
                    }
                    loadLibrary();
                    loadStats();
                    refreshCartBadge();
                    if (currentTab == TAB_SCAN) {
                        hideResultCard();
                        scannerOverlay.setMode(ScannerOverlayView.MODE_IDLE);
                        vfLabel.setText(R.string.scan_hint_aim);
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Import failed", e);
                runOnUiThread(() -> showAppToast(getString(
                        R.string.toast_import_failed, messageOf(e))));
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

    private void showBackupsSheet() {
        ioExecutor.execute(() -> {
            List<File> backups = DataTransfer.listBackups(this);
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                if (backups.isEmpty()) {
                    showAppToast(getString(R.string.backup_empty));
                    return;
                }
                View content = showSheet(R.layout.sheet_backups);
                LinearLayout rows = content.findViewById(R.id.backup_rows);
                SimpleDateFormat format =
                        new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                for (File file : backups) {
                    TextView row = new TextView(this);
                    row.setText(format.format(new Date(file.lastModified())));
                    row.setTextColor(ContextCompat.getColor(this, R.color.ink));
                    row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dpInt(16), 0, dpInt(16), 0);
                    row.setBackgroundResource(R.drawable.bg_btn_ghost);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dpInt(48));
                    params.bottomMargin = dpInt(8);
                    row.setLayoutParams(params);
                    row.setOnClickListener(v -> {
                        closeSheet();
                        confirmRestore(file);
                    });
                    rows.addView(row);
                }
                content.findViewById(R.id.backup_cancel).setOnClickListener(
                        v -> closeSheet());
            });
        });
    }

    private void confirmRestore(File file) {
        ioExecutor.execute(() -> {
            try {
                DataTransfer.ImportData data = DataTransfer.parse(
                        DataTransfer.readFile(file));
                ImportPreview preview = buildImportPreview(data);
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) {
                        return;
                    }
                    new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                            .setTitle(R.string.backup_title)
                            .setMessage(getString(
                                    R.string.backup_confirm, file.getName()))
                            .setNegativeButton(R.string.action_cancel, null)
                            .setPositiveButton(
                                    R.string.backup_restore_action,
                                    (dialog, which) ->
                                            applyImport(data, true, preview, true))
                            .show();
                });
            } catch (Exception e) {
                Log.w(TAG, "Restore backup failed", e);
                runOnUiThread(() -> showAppToast(getString(
                        R.string.toast_import_failed, messageOf(e))));
            }
        });
    }

    private void showAboutSheet() {
        View content = showSheet(R.layout.sheet_about);
        TextView version = content.findViewById(R.id.about_version);
        version.setText(getString(R.string.about_version, "1.9"));
        content.findViewById(R.id.about_close).setOnClickListener(v -> closeSheet());
    }

    private View showSheet(int layoutRes) {
        sheetPanel.animate().cancel();
        sheetMask.animate().cancel();
        sheetBody.removeAllViews();
        ViewGroup.LayoutParams panelParams = sheetPanel.getLayoutParams();
        panelParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        sheetPanel.setLayoutParams(panelParams);
        View content = LayoutInflater.from(this)
                .inflate(layoutRes, sheetBody, false);
        sheetBody.addView(content);
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
        return content;
    }

    private void fitSheetHeight() {
        if (!sheetOpen || sheetBody.getChildCount() == 0) {
            return;
        }
        View content = sheetBody.getChildAt(0);
        int width = sheetBody.getWidth();
        if (width <= 0) {
            width = root.getWidth() - dpInt(32);
        }
        if (width <= 0) {
            return;
        }
        content.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int natural = content.getMeasuredHeight() + dpInt(49);
        int available = root.getHeight()
                - root.getPaddingTop()
                - root.getPaddingBottom()
                - dpInt(8);
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
                })
                .start();
        sheetMask.animate().alpha(0f).setDuration(220)
                .withEndAction(() -> sheetMask.setVisibility(View.GONE))
                .start();
    }

    private void showAppToast(String message) {
        if (appToast == null || destroyed || isFinishing()) {
            return;
        }
        if (toastHideRunnable != null) {
            appToast.removeCallbacks(toastHideRunnable);
        }
        appToast.animate().cancel();
        appToast.setText(message);
        appToast.setVisibility(View.VISIBLE);
        appToast.setAlpha(0f);
        appToast.setTranslationY(dp(12));
        appToast.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(EASE)
                .start();
        toastHideRunnable = () -> appToast.animate()
                .alpha(0f)
                .translationY(dp(12))
                .setDuration(250)
                .withEndAction(() -> appToast.setVisibility(View.GONE))
                .start();
        appToast.postDelayed(toastHideRunnable, 1600);
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

    @Override
    protected void onResume() {
        super.onResume();
        refreshTab(currentTab);
        refreshCartBadge();
    }

    @Override
    protected void onPause() {
        if (scanning) {
            stopScanSession();
        } else {
            stopCamera();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        scanning = false;
        stopCamera();
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
        return (int) dp(value);
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty()
                ? e.getClass().getSimpleName() : message;
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

    private class CartAdapter extends BaseAdapter {

        private final List<CartItem> items = new ArrayList<>();
        private final Map<String, Product> products = new HashMap<>();

        void setItems(List<CartItem> newItems, Map<String, Product> productMap) {
            items.clear();
            items.addAll(newItems);
            products.clear();
            products.putAll(productMap);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public CartItem getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            boolean isNew = convertView == null;
            if (isNew) {
                convertView = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_cart, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            CartItem item = getItem(position);
            Product product = products.get(item.barcode);
            String category = product == null ? "" : product.category;
            applyThumb(holder.thumb, item.name, category, 13);
            holder.name.setText(item.name);
            holder.meta.setText(getString(
                    R.string.cart_item_meta,
                    MoneyUtils.formatCents(item.unitPriceCents),
                    item.barcode));
            holder.quantity.setText(String.valueOf(item.quantity));

            holder.minus.setOnClickListener(v ->
                    changeCartQuantity(item.barcode, item.quantity - 1, holder.quantity));
            holder.plus.setOnClickListener(v ->
                    changeCartQuantity(item.barcode, item.quantity + 1, holder.quantity));

            if (isNew) {
                convertView.setAlpha(0f);
                convertView.setTranslationY(dp(12));
                convertView.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(380)
                        .setStartDelay(Math.min(position, 8) * 45L)
                        .setInterpolator(EASE)
                        .start();
            }
            return convertView;
        }
    }

    private static class ViewHolder {
        final TextView thumb;
        final TextView name;
        final TextView meta;
        final TextView quantity;
        final ImageView minus;
        final ImageView plus;

        ViewHolder(View view) {
            thumb = view.findViewById(R.id.item_thumb);
            name = view.findViewById(R.id.item_name);
            meta = view.findViewById(R.id.item_meta);
            quantity = view.findViewById(R.id.item_quantity);
            minus = view.findViewById(R.id.btn_minus);
            plus = view.findViewById(R.id.btn_plus);
        }
    }

    private class ProductAdapter extends BaseAdapter {

        private final List<Product> items = new ArrayList<>();

        void setItems(List<Product> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Product getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ProductViewHolder holder;
            boolean isNew = convertView == null;
            if (isNew) {
                convertView = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_product, parent, false);
                holder = new ProductViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ProductViewHolder) convertView.getTag();
            }

            Product product = getItem(position);
            applyThumb(holder.thumb, product.name, product.category, 12);
            holder.name.setText(product.name);
            String meta = product.barcode;
            if (!product.category.isEmpty()) {
                meta = meta + " · " + product.category;
            }
            holder.meta.setText(meta);
            holder.price.setText(getString(
                    R.string.product_price_format,
                    MoneyUtils.formatCents(product.priceCents)));
            holder.add.setOnClickListener(v -> addToCart(product, null));

            if (isNew) {
                convertView.setAlpha(0f);
                convertView.setTranslationY(dp(12));
                convertView.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(380)
                        .setStartDelay(Math.min(position, 9) * 38L)
                        .setInterpolator(EASE)
                        .start();
            }
            return convertView;
        }
    }

    private static class ProductViewHolder {
        final TextView thumb;
        final TextView name;
        final TextView meta;
        final TextView price;
        final ImageView add;

        ProductViewHolder(View view) {
            thumb = view.findViewById(R.id.item_thumb);
            name = view.findViewById(R.id.item_name);
            meta = view.findViewById(R.id.item_meta);
            price = view.findViewById(R.id.item_price);
            add = view.findViewById(R.id.btn_item_add);
        }
    }
}
