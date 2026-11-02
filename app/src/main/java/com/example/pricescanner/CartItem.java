package com.example.pricescanner;

public class CartItem {
    public final String barcode;
    public final String name;
    public final long unitPriceCents;
    public final int quantity;

    public CartItem(String barcode, String name, long unitPriceCents, int quantity) {
        this.barcode = barcode;
        this.name = name;
        this.unitPriceCents = unitPriceCents;
        this.quantity = quantity;
    }

    public long lineTotalCents() {
        return unitPriceCents * quantity;
    }
}
