package com.example.pricescanner;

public class Product {
    public final String barcode;
    public final String name;
    public final String brand;
    public final String spec;
    public final long priceCents;
    public final long priceUpdatedAt;
    public final long nameUpdatedAt;
    public final String source;

    public Product(
            String barcode,
            String name,
            String brand,
            String spec,
            long priceCents,
            long priceUpdatedAt,
            long nameUpdatedAt,
            String source) {
        this.barcode = barcode;
        this.name = name;
        this.brand = brand;
        this.spec = spec;
        this.priceCents = priceCents;
        this.priceUpdatedAt = priceUpdatedAt;
        this.nameUpdatedAt = nameUpdatedAt;
        this.source = source;
    }
}
