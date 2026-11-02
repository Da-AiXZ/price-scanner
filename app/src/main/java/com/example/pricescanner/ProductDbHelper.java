package com.example.pricescanner;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ProductDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "product_prices.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "products";

    public ProductDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "barcode TEXT PRIMARY KEY, "
                + "name TEXT NOT NULL, "
                + "price REAL NOT NULL, "
                + "updated_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No schema migrations yet.
    }

    public Product findProduct(String barcode) {
        try (Cursor cursor = getReadableDatabase().query(
                TABLE,
                new String[]{"barcode", "name", "price"},
                "barcode = ?",
                new String[]{barcode},
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return new Product(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getDouble(2));
            }
        }
        return null;
    }

    public void upsertProduct(Product product) {
        ContentValues values = new ContentValues();
        values.put("barcode", product.barcode);
        values.put("name", product.name);
        values.put("price", product.price);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(
                TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
