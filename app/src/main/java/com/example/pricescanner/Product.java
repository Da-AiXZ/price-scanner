package com.example.pricescanner;

public class Product {
    public final String barcode;
    public final String name;
    public final double price;

    public Product(String barcode, String name, double price) {
        this.barcode = barcode;
        this.name = name;
        this.price = price;
    }
}
