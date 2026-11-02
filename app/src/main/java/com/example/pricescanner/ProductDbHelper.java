package com.example.pricescanner;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "product_prices.db";
    private static final int DB_VERSION = 3;

    private static final String TABLE_PRODUCTS = "products";
    private static final String TABLE_INFO = "product_info";
    private static final String TABLE_CART = "cart_items";
    private static final String TABLE_CHECKOUT = "checkout_history";

    public ProductDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createProductsTable(db, TABLE_PRODUCTS);
        createInfoTable(db);
        createCartTable(db);
        createCheckoutTable(db);
    }

    private void createProductsTable(SQLiteDatabase db, String table) {
        db.execSQL("CREATE TABLE " + table + " ("
                + "barcode TEXT PRIMARY KEY, "
                + "name TEXT NOT NULL, "
                + "brand TEXT NOT NULL DEFAULT '', "
                + "spec TEXT NOT NULL DEFAULT '', "
                + "price_cents INTEGER NOT NULL, "
                + "price_updated_at INTEGER NOT NULL, "
                + "name_updated_at INTEGER NOT NULL, "
                + "source TEXT NOT NULL DEFAULT 'manual', "
                + "category TEXT NOT NULL DEFAULT '')");
    }

    private void createInfoTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_INFO + " ("
                + "barcode TEXT PRIMARY KEY, "
                + "name TEXT NOT NULL DEFAULT '', "
                + "brand TEXT NOT NULL DEFAULT '', "
                + "spec TEXT NOT NULL DEFAULT '', "
                + "product_type TEXT NOT NULL DEFAULT '', "
                + "source TEXT NOT NULL DEFAULT 'openfoodfacts', "
                + "fetched_at INTEGER NOT NULL, "
                + "found INTEGER NOT NULL, "
                + "user_edited INTEGER NOT NULL DEFAULT 0)");
    }

    private void createCartTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_CART + " ("
                + "barcode TEXT PRIMARY KEY, "
                + "name_snapshot TEXT NOT NULL, "
                + "unit_price_cents INTEGER NOT NULL, "
                + "quantity INTEGER NOT NULL, "
                + "added_at INTEGER NOT NULL)");
    }

    private void createCheckoutTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CHECKOUT + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "total_cents INTEGER NOT NULL, "
                + "item_count INTEGER NOT NULL, "
                + "checked_out_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRODUCTS + "_new");
            createProductsTable(db, TABLE_PRODUCTS + "_new");
            db.execSQL("INSERT INTO " + TABLE_PRODUCTS + "_new "
                    + "(barcode, name, brand, spec, price_cents, price_updated_at, "
                    + "name_updated_at, source) "
                    + "SELECT barcode, name, '', '', "
                    + "CAST(ROUND(price * 100) AS INTEGER), updated_at, updated_at, 'manual' "
                    + "FROM " + TABLE_PRODUCTS);
            db.execSQL("DROP TABLE " + TABLE_PRODUCTS);
            db.execSQL("ALTER TABLE " + TABLE_PRODUCTS + "_new RENAME TO " + TABLE_PRODUCTS);
            createInfoTable(db);
            createCartTable(db);
        }
        if (oldVersion < 3) {
            if (oldVersion >= 2) {
                db.execSQL("ALTER TABLE " + TABLE_PRODUCTS
                        + " ADD COLUMN category TEXT NOT NULL DEFAULT ''");
            }
            createCheckoutTable(db);
        }
    }

    public Product findProduct(String barcode) {
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_PRODUCTS,
                productColumns(),
                "barcode = ?",
                new String[]{barcode},
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return readProduct(cursor);
            }
        }
        return null;
    }

    public List<Product> getAllProducts() {
        return readAllProducts(getReadableDatabase());
    }

    public boolean deleteProduct(String barcode) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int removed = db.delete(
                    TABLE_PRODUCTS, "barcode = ?", new String[]{barcode});
            db.delete(TABLE_INFO, "barcode = ?", new String[]{barcode});
            db.setTransactionSuccessful();
            return removed > 0;
        } finally {
            db.endTransaction();
        }
    }

    private List<Product> readAllProducts(SQLiteDatabase db) {
        List<Product> products = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_PRODUCTS,
                productColumns(),
                null, null, null, null,
                "name COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                products.add(readProduct(cursor));
            }
        }
        return products;
    }

    public Product saveProduct(
            String barcode, String name, String brand, String spec,
            long priceCents, String source) {
        return saveProduct(barcode, name, brand, spec, priceCents, source, "");
    }

    public Product saveProduct(
            String barcode, String name, String brand, String spec,
            long priceCents, String source, String category) {
        long now = System.currentTimeMillis();
        Product product = new Product(
                barcode, name, brand == null ? "" : brand, spec == null ? "" : spec,
                priceCents, now, now, source == null ? "manual" : source,
                category == null ? "" : category);
        upsertProduct(product);
        return product;
    }

    public void upsertProduct(Product product) {
        getWritableDatabase().insertWithOnConflict(
                TABLE_PRODUCTS, null, productValues(product), SQLiteDatabase.CONFLICT_REPLACE);
    }

    private String[] productColumns() {
        return new String[]{
                "barcode", "name", "brand", "spec",
                "price_cents", "price_updated_at", "name_updated_at", "source",
                "category"};
    }

    private Product readProduct(Cursor cursor) {
        return new Product(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getLong(4),
                cursor.getLong(5),
                cursor.getLong(6),
                cursor.getString(7),
                cursor.getString(8));
    }

    private ContentValues productValues(Product product) {
        ContentValues values = new ContentValues();
        values.put("barcode", product.barcode);
        values.put("name", product.name);
        values.put("brand", product.brand == null ? "" : product.brand);
        values.put("spec", product.spec == null ? "" : product.spec);
        values.put("price_cents", product.priceCents);
        values.put("price_updated_at", product.priceUpdatedAt);
        values.put("name_updated_at", product.nameUpdatedAt);
        values.put("source", product.source == null ? "manual" : product.source);
        values.put("category", product.category == null ? "" : product.category);
        return values;
    }

    /**
     * 按条码合并导入：价格取更新时间较新的；名称优先保留本地人工修正。
     */
    public void mergeImportProducts(List<Product> incoming) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<String, Product> localMap = new HashMap<>();
            for (Product local : readAllProducts(db)) {
                localMap.put(local.barcode, local);
            }
            for (Product product : incoming) {
                Product local = localMap.get(product.barcode);
                if (local == null) {
                    db.insertWithOnConflict(
                            TABLE_PRODUCTS, null, productValues(product),
                            SQLiteDatabase.CONFLICT_REPLACE);
                    continue;
                }

                long priceCents = local.priceCents;
                long priceUpdatedAt = local.priceUpdatedAt;
                if (product.priceUpdatedAt >= local.priceUpdatedAt) {
                    priceCents = product.priceCents;
                    priceUpdatedAt = product.priceUpdatedAt;
                }

                boolean localManual = "manual".equals(local.source);
                boolean incomingManual = "manual".equals(product.source);
                boolean useIncomingName = product.nameUpdatedAt >= local.nameUpdatedAt
                        && (incomingManual || !localManual);

                String name = useIncomingName ? product.name : local.name;
                String brand = useIncomingName ? product.brand : local.brand;
                String spec = useIncomingName ? product.spec : local.spec;
                String source = useIncomingName ? product.source : local.source;
                long nameUpdatedAt = useIncomingName
                        ? product.nameUpdatedAt : local.nameUpdatedAt;
                String category = local.category;
                if (useIncomingName
                        && product.category != null
                        && !product.category.isEmpty()) {
                    category = product.category;
                }

                ContentValues values = new ContentValues();
                values.put("barcode", local.barcode);
                values.put("name", name);
                values.put("brand", brand == null ? "" : brand);
                values.put("spec", spec == null ? "" : spec);
                values.put("price_cents", priceCents);
                values.put("price_updated_at", priceUpdatedAt);
                values.put("name_updated_at", nameUpdatedAt);
                values.put("source", source == null ? "manual" : source);
                values.put("category", category == null ? "" : category);
                db.update(TABLE_PRODUCTS, values,
                        "barcode = ?", new String[]{local.barcode});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void replaceImportProducts(List<Product> incoming) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_PRODUCTS, null, null);
            for (Product product : incoming) {
                db.insertWithOnConflict(
                        TABLE_PRODUCTS, null, productValues(product),
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public ProductInfo findProductInfo(String barcode) {
        return findProductInfo(getReadableDatabase(), barcode);
    }

    private ProductInfo findProductInfo(SQLiteDatabase db, String barcode) {
        try (Cursor cursor = db.query(
                TABLE_INFO,
                infoColumns(),
                "barcode = ?",
                new String[]{barcode},
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return readInfo(cursor);
            }
        }
        return null;
    }

    public List<ProductInfo> getAllProductInfo() {
        List<ProductInfo> infos = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_INFO,
                infoColumns(),
                null, null, null, null,
                "fetched_at ASC")) {
            while (cursor.moveToNext()) {
                infos.add(readInfo(cursor));
            }
        }
        return infos;
    }

    private String[] infoColumns() {
        return new String[]{
                "barcode", "name", "brand", "spec", "product_type",
                "source", "fetched_at", "found", "user_edited"};
    }

    private ProductInfo readInfo(Cursor cursor) {
        return new ProductInfo(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getString(4),
                cursor.getString(5),
                cursor.getLong(6),
                cursor.getInt(7) == 1,
                cursor.getInt(8) == 1);
    }

    public void upsertProductInfo(ProductInfo info) {
        getWritableDatabase().insertWithOnConflict(
                TABLE_INFO, null, infoValues(info), SQLiteDatabase.CONFLICT_REPLACE);
    }

    private ContentValues infoValues(ProductInfo info) {
        ContentValues values = new ContentValues();
        values.put("barcode", info.barcode);
        values.put("name", info.name == null ? "" : info.name);
        values.put("brand", info.brand == null ? "" : info.brand);
        values.put("spec", info.spec == null ? "" : info.spec);
        values.put("product_type", info.productType == null ? "" : info.productType);
        values.put("source", info.source == null ? "openfoodfacts" : info.source);
        values.put("fetched_at", info.fetchedAt);
        values.put("found", info.found ? 1 : 0);
        values.put("user_edited", info.userEdited ? 1 : 0);
        return values;
    }

    public void mergeImportProductInfo(List<ProductInfo> incoming) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (ProductInfo info : incoming) {
                ProductInfo local = findProductInfo(db, info.barcode);
                if (local == null) {
                    db.insertWithOnConflict(
                            TABLE_INFO, null, infoValues(info),
                            SQLiteDatabase.CONFLICT_REPLACE);
                    continue;
                }
                if (local.userEdited && !info.userEdited) {
                    continue;
                }
                if (info.fetchedAt >= local.fetchedAt) {
                    db.insertWithOnConflict(
                            TABLE_INFO, null, infoValues(info),
                            SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void clearProductInfo() {
        getWritableDatabase().delete(TABLE_INFO, null, null);
    }

    public void addToCart(Product product) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int quantity = 0;
            try (Cursor cursor = db.query(
                    TABLE_CART,
                    new String[]{"quantity"},
                    "barcode = ?",
                    new String[]{product.barcode},
                    null, null, null)) {
                if (cursor.moveToFirst()) {
                    quantity = cursor.getInt(0);
                }
            }
            if (quantity > 0) {
                ContentValues increment = new ContentValues();
                increment.put("quantity", quantity + 1);
                db.update(TABLE_CART, increment,
                        "barcode = ?", new String[]{product.barcode});
            } else {
                ContentValues values = new ContentValues();
                values.put("barcode", product.barcode);
                values.put("name_snapshot", product.name);
                values.put("unit_price_cents", product.priceCents);
                values.put("quantity", 1);
                values.put("added_at", System.currentTimeMillis());
                db.insertWithOnConflict(
                        TABLE_CART, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<CartItem> getCartItems() {
        List<CartItem> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_CART,
                new String[]{"barcode", "name_snapshot", "unit_price_cents", "quantity"},
                null, null, null, null,
                "added_at ASC")) {
            while (cursor.moveToNext()) {
                items.add(new CartItem(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getLong(2),
                        cursor.getInt(3)));
            }
        }
        return items;
    }

    public void updateCartQuantity(String barcode, int quantity) {
        if (quantity <= 0) {
            removeCartItem(barcode);
            return;
        }
        ContentValues values = new ContentValues();
        values.put("quantity", quantity);
        getWritableDatabase().update(
                TABLE_CART, values, "barcode = ?", new String[]{barcode});
    }

    public void removeCartItem(String barcode) {
        getWritableDatabase().delete(
                TABLE_CART, "barcode = ?", new String[]{barcode});
    }

    public void clearCart() {
        getWritableDatabase().delete(TABLE_CART, null, null);
    }

    public void recordCheckout(long totalCents, int itemCount) {
        ContentValues values = new ContentValues();
        values.put("total_cents", totalCents);
        values.put("item_count", itemCount);
        values.put("checked_out_at", System.currentTimeMillis());
        getWritableDatabase().insert(TABLE_CHECKOUT, null, values);
    }

    public CheckoutStats getCheckoutStats() {
        long totalCents = 0;
        int count = 0;
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_CHECKOUT,
                new String[]{"total_cents"},
                null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                totalCents += cursor.getLong(0);
                count++;
            }
        }
        return new CheckoutStats(count, totalCents);
    }

    public CartSummary getCartSummary() {
        long totalCents = 0;
        int totalQuantity = 0;
        int distinctCount = 0;
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_CART,
                new String[]{"unit_price_cents", "quantity"},
                null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                long price = cursor.getLong(0);
                int quantity = cursor.getInt(1);
                totalCents += price * quantity;
                totalQuantity += quantity;
                distinctCount++;
            }
        }
        return new CartSummary(totalQuantity, totalCents, distinctCount);
    }

    public static class CheckoutStats {
        public final int count;
        public final long totalCents;

        public CheckoutStats(int count, long totalCents) {
            this.count = count;
            this.totalCents = totalCents;
        }
    }

    public static class CartSummary {
        public final int totalQuantity;
        public final long totalCents;
        public final int distinctCount;

        public CartSummary(int totalQuantity, long totalCents, int distinctCount) {
            this.totalQuantity = totalQuantity;
            this.totalCents = totalCents;
            this.distinctCount = distinctCount;
        }
    }
}
