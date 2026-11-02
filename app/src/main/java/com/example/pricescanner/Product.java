package com.example.pricescanner;

import java.util.Locale;

public class Product {
    public final String barcode;
    public final String name;
    public final String brand;
    public final String spec;
    public final long priceCents;
    public final long priceUpdatedAt;
    public final long nameUpdatedAt;
    public final String source;
    public final String category;

    public Product(
            String barcode,
            String name,
            String brand,
            String spec,
            long priceCents,
            long priceUpdatedAt,
            long nameUpdatedAt,
            String source) {
        this(barcode, name, brand, spec, priceCents,
                priceUpdatedAt, nameUpdatedAt, source, "");
    }

    public Product(
            String barcode,
            String name,
            String brand,
            String spec,
            long priceCents,
            long priceUpdatedAt,
            long nameUpdatedAt,
            String source,
            String category) {
        this.barcode = barcode;
        this.name = name;
        this.brand = brand;
        this.spec = spec;
        this.priceCents = priceCents;
        this.priceUpdatedAt = priceUpdatedAt;
        this.nameUpdatedAt = nameUpdatedAt;
        this.source = source;
        this.category = category == null ? "" : category;
    }

    public boolean matches(String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String keyword = query.toLowerCase(Locale.ROOT);
        return contains(name, keyword)
                || contains(barcode, keyword)
                || contains(brand, keyword)
                || contains(spec, keyword)
                || contains(category, keyword);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }
}
