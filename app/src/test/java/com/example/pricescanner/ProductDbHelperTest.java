package com.example.pricescanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ProductDbHelperTest {

    private Context context() {
        return RuntimeEnvironment.getApplication();
    }

    private void deleteDatabase() {
        File file = context().getDatabasePath("product_prices.db");
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        //noinspection ResultOfMethodCallIgnored
        new File(file.getPath() + "-journal").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(file.getPath() + "-wal").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(file.getPath() + "-shm").delete();
    }

    @Test
    public void migratesVersionOneDatabase() {
        deleteDatabase();
        File file = context().getDatabasePath("product_prices.db");
        File parent = file.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.execSQL("CREATE TABLE products ("
                + "barcode TEXT PRIMARY KEY, name TEXT NOT NULL, "
                + "price REAL NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("INSERT INTO products VALUES ('6901234567892','可乐',12.90,1700000000000)");
        db.setVersion(1);
        db.close();

        try (ProductDbHelper helper = new ProductDbHelper(context())) {
            Product product = helper.findProduct("6901234567892");
            assertNotNull(product);
            assertEquals("可乐", product.name);
            assertEquals(1290, product.priceCents);
            assertEquals(1700000000000L, product.priceUpdatedAt);
            assertEquals("manual", product.source);
            assertEquals("", product.category);
        }
    }

    @Test
    public void mergesNewerPricesAndKeepsManualNames() {
        deleteDatabase();
        try (ProductDbHelper helper = new ProductDbHelper(context())) {
            Product local = new Product(
                    "6901234567892", "可乐", "", "", 350,
                    1000L, 1000L, "manual");
            helper.upsertProduct(local);

            Product newer = new Product(
                    "6901234567892", "可口可乐", "", "", 400,
                    2000L, 2000L, "manual");
            helper.mergeImportProducts(Collections.singletonList(newer));
            Product merged = helper.findProduct("6901234567892");
            assertNotNull(merged);
            assertEquals(400, merged.priceCents);
            assertEquals("可口可乐", merged.name);

            Product older = new Product(
                    "6901234567892", "旧名字", "", "", 300,
                    500L, 500L, "manual");
            helper.mergeImportProducts(Collections.singletonList(older));
            merged = helper.findProduct("6901234567892");
            assertNotNull(merged);
            assertEquals(400, merged.priceCents);
            assertEquals("可口可乐", merged.name);

            Product api = new Product(
                    "6901234567892", "API 名称", "", "", 500,
                    3000L, 3000L, "openfoodfacts");
            helper.mergeImportProducts(Collections.singletonList(api));
            merged = helper.findProduct("6901234567892");
            assertNotNull(merged);
            assertEquals(500, merged.priceCents);
            assertEquals("可口可乐", merged.name);
        }
    }

    @Test
    public void cartAddsAndSums() {
        deleteDatabase();
        try (ProductDbHelper helper = new ProductDbHelper(context())) {
            Product product = helper.saveProduct(
                    "6901234567892", "可乐", "", "", 350, "manual");
            helper.addToCart(product);
            helper.addToCart(product);
            ProductDbHelper.CartSummary summary = helper.getCartSummary();
            assertEquals(2, summary.totalQuantity);
            assertEquals(700, summary.totalCents);
            assertEquals(1, summary.distinctCount);

            helper.updateCartQuantity(product.barcode, 3);
            summary = helper.getCartSummary();
            assertEquals(3, summary.totalQuantity);
            assertEquals(1050, summary.totalCents);
        }
    }

    @Test
    public void jsonRoundTripKeepsPricesAndInfo() throws Exception {
        Product product = new Product(
                "6901234567892", "六神花露水（195ml）", "六神", "195ml",
                1290, 1700000000000L, 1700000000000L, "manual", "日化");
        ProductInfo info = new ProductInfo(
                "6901234567892", "六神花露水（195ml）", "六神", "195ml",
                "beauty", "openfoodfacts", 1700000000000L, true, false);

        String json = DataTransfer.buildJson(
                Collections.singletonList(product),
                Collections.singletonList(info),
                1700000000000L);
        DataTransfer.ImportData data = DataTransfer.parse(json);
        assertEquals(1, data.products.size());
        assertEquals(1, data.productInfo.size());
        assertEquals(1290, data.products.get(0).priceCents);
        assertEquals("日化", data.products.get(0).category);
        assertTrue(data.productInfo.get(0).hasUsableName());
    }

    @Test
    public void cacheMergeKeepsUserEdits() {
        deleteDatabase();
        try (ProductDbHelper helper = new ProductDbHelper(context())) {
            helper.upsertProductInfo(new ProductInfo(
                    "6901234567892", "六神花露水（195ml）", "六神", "195ml",
                    "beauty", "openfoodfacts", 1000L, true, true));

            helper.mergeImportProductInfo(Collections.singletonList(
                    new ProductInfo(
                            "6901234567892", "错误名称", "六神", "195ml",
                            "beauty", "openfoodfacts", 2000L, true, false)));
            ProductInfo cached = helper.findProductInfo("6901234567892");
            assertNotNull(cached);
            assertEquals("六神花露水（195ml）", cached.name);

            helper.mergeImportProductInfo(Collections.singletonList(
                    new ProductInfo(
                            "6901234567892", "人工确认的新名称", "六神", "195ml",
                            "beauty", "openfoodfacts", 3000L, true, true)));
            cached = helper.findProductInfo("6901234567892");
            assertNotNull(cached);
            assertEquals("人工确认的新名称", cached.name);
        }
    }

    @Test
    public void deletesProductAndCachedInfo() {
        deleteDatabase();
        try (ProductDbHelper helper = new ProductDbHelper(context())) {
            Product product = helper.saveProduct(
                    "6901234567892", "可乐", "可口可乐", "500ml", 350, "manual");
            helper.upsertProductInfo(new ProductInfo(
                    product.barcode, "可乐", "可口可乐", "500ml",
                    "drink", "openfoodfacts", 1000L, true, false));

            assertTrue(helper.deleteProduct(product.barcode));
            assertNull(helper.findProduct(product.barcode));
            assertNull(helper.findProductInfo(product.barcode));
            assertFalse(helper.deleteProduct("0000000000000"));
        }
    }

    @Test
    public void productSearchMatchesNameBarcodeBrandAndSpec() {
        Product product = new Product(
                "6901234567892", "六神花露水", "六神", "195ml",
                1290, 1000L, 1000L, "manual");
        assertTrue(product.matches(""));
        assertTrue(product.matches("花露水"));
        assertTrue(product.matches("690123"));
        assertTrue(product.matches("六神"));
        assertTrue(product.matches("195"));
        assertFalse(product.matches("可乐"));
    }

    @Test
    public void savesCategoryAndCheckoutStats() {
        deleteDatabase();
        try (ProductDbHelper helper = new ProductDbHelper(context())) {
            Product product = helper.saveProduct(
                    "6901234567892", "可乐", "", "", 350, "manual", "饮料");
            assertEquals("饮料", product.category);
            Product saved = helper.findProduct("6901234567892");
            assertNotNull(saved);
            assertEquals("饮料", saved.category);

            assertEquals(0, helper.getCheckoutStats().count);
            helper.recordCheckout(700, 2);
            helper.recordCheckout(350, 1);
            assertEquals(2, helper.getCheckoutStats().count);
            assertEquals(1050, helper.getCheckoutStats().totalCents);
        }
    }

    @Test
    public void inflatesMainAndSheetLayouts() {
        Context themed = new ContextThemeWrapper(context(), R.style.Theme_PriceScanner);
        LayoutInflater inflater = LayoutInflater.from(themed);
        assertNotNull(inflater.inflate(R.layout.activity_main, null));
        assertNotNull(inflater.inflate(R.layout.activity_scan_add, null));
        assertNotNull(inflater.inflate(R.layout.item_cart, null));
        assertNotNull(inflater.inflate(R.layout.item_product, null));
        assertNotNull(inflater.inflate(R.layout.sheet_product, null));
        assertNotNull(inflater.inflate(R.layout.sheet_export, null));
        assertNotNull(inflater.inflate(R.layout.sheet_backups, null));
        assertNotNull(inflater.inflate(R.layout.sheet_about, null));
        assertNotNull(inflater.inflate(R.layout.menu_popup, null));
    }
}
