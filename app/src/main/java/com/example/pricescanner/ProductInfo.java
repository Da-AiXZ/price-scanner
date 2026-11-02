package com.example.pricescanner;

public class ProductInfo {
    public final String barcode;
    public final String name;
    public final String brand;
    public final String spec;
    public final String productType;
    public final String source;
    public final long fetchedAt;
    public final boolean found;
    public final boolean userEdited;

    public ProductInfo(
            String barcode,
            String name,
            String brand,
            String spec,
            String productType,
            String source,
            long fetchedAt,
            boolean found,
            boolean userEdited) {
        this.barcode = barcode;
        this.name = name;
        this.brand = brand;
        this.spec = spec;
        this.productType = productType;
        this.source = source;
        this.fetchedAt = fetchedAt;
        this.found = found;
        this.userEdited = userEdited;
    }

    public boolean hasUsableName() {
        return found && name != null && !name.trim().isEmpty();
    }
}
