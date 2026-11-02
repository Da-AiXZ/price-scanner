package com.example.pricescanner;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductsActivity extends AppCompatActivity {

    private ProductDbHelper dbHelper;
    private ExecutorService executor;
    private ProductAdapter adapter;
    private final List<Product> allProducts = new ArrayList<>();
    private String query = "";
    private View emptyView;
    private TextView emptyTitle;
    private TextView emptySubtitle;
    private TextView countView;
    private TextView cartBadge;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_products);

        dbHelper = new ProductDbHelper(this);
        executor = Executors.newSingleThreadExecutor();

        ImageButton back = findViewById(R.id.btn_back);
        back.setOnClickListener(v -> finish());

        emptyView = findViewById(R.id.products_empty);
        emptyTitle = findViewById(R.id.products_empty_title);
        emptySubtitle = findViewById(R.id.products_empty_sub);
        countView = findViewById(R.id.text_product_count);
        cartBadge = findViewById(R.id.products_cart_badge);

        adapter = new ProductAdapter();
        ListView listView = findViewById(R.id.product_list);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(
                (parent, view, position, id) -> showEditDialog(adapter.getItem(position)));

        EditText search = findViewById(R.id.edit_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                applyFilter();
            }
        });

        findViewById(R.id.btn_products_cart).setOnClickListener(
                v -> startActivity(new Intent(this, CartActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProducts();
        refreshCartBadge();
    }

    private void loadProducts() {
        executor.execute(() -> {
            List<Product> products = dbHelper.getAllProducts();
            runOnUiThread(() -> {
                if (destroyed) {
                    return;
                }
                allProducts.clear();
                allProducts.addAll(products);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        List<Product> filtered = new ArrayList<>();
        for (Product product : allProducts) {
            if (matches(product, query)) {
                filtered.add(product);
            }
        }
        adapter.setItems(filtered);

        if (allProducts.isEmpty()) {
            emptyTitle.setText(R.string.products_empty);
            emptySubtitle.setText(R.string.products_empty_sub);
        } else if (filtered.isEmpty()) {
            emptyTitle.setText(R.string.products_no_match);
            emptySubtitle.setText(R.string.products_no_match_sub);
        }
        emptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);

        countView.setText(query.isEmpty()
                ? getString(R.string.products_count, allProducts.size())
                : getString(R.string.products_match, filtered.size(), allProducts.size()));
    }

    static boolean matches(Product product, String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String keyword = query.toLowerCase(Locale.ROOT);
        return contains(product.name, keyword)
                || contains(product.barcode, keyword)
                || contains(product.brand, keyword)
                || contains(product.spec, keyword);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private void showEditDialog(Product product) {
        View content = LayoutInflater.from(this)
                .inflate(R.layout.dialog_product, null, false);
        EditText nameInput = content.findViewById(R.id.edit_name);
        EditText priceInput = content.findViewById(R.id.edit_price);
        TextView barcodeView = content.findViewById(R.id.dialog_barcode);
        barcodeView.setText(getString(R.string.barcode_prefix, product.barcode));

        nameInput.setText(product.name);
        nameInput.setSelection(nameInput.getText().length());
        priceInput.setText(MoneyUtils.formatCents(product.priceCents));

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                .setTitle(R.string.dialog_edit_title)
                .setView(content)
                .setNeutralButton(R.string.action_delete, null)
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
                Long priceCents = MoneyUtils.parseYuanToCents(
                        priceInput.getText().toString());
                if (priceCents == null) {
                    priceInput.setError(getString(R.string.error_price));
                    return;
                }

                executor.execute(() -> {
                    dbHelper.saveProduct(
                            product.barcode, name, product.brand, product.spec,
                            priceCents, "manual");
                    runOnUiThread(() -> {
                        if (destroyed) {
                            return;
                        }
                        dialog.dismiss();
                        Toast.makeText(
                                ProductsActivity.this,
                                R.string.toast_saved,
                                Toast.LENGTH_SHORT).show();
                        loadProducts();
                    });
                });
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(
                    v -> {
                        dialog.dismiss();
                        confirmDelete(product);
                    });
        });

        dialog.show();
    }

    private void confirmDelete(Product product) {
        new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                .setTitle(R.string.delete_product_title)
                .setMessage(getString(R.string.delete_product_message, product.name))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        executor.execute(() -> {
                            dbHelper.deleteProduct(product.barcode);
                            runOnUiThread(() -> {
                                if (destroyed) {
                                    return;
                                }
                                Toast.makeText(
                                        ProductsActivity.this,
                                        getString(
                                                R.string.toast_product_deleted,
                                                product.name),
                                        Toast.LENGTH_SHORT).show();
                                loadProducts();
                            });
                        }))
                .show();
    }

    private void addToCart(Product product) {
        executor.execute(() -> {
            dbHelper.addToCart(product);
            ProductDbHelper.CartSummary summary = dbHelper.getCartSummary();
            runOnUiThread(() -> {
                if (destroyed) {
                    return;
                }
                updateCartBadge(summary.totalQuantity);
                Toast.makeText(
                        ProductsActivity.this,
                        getString(
                                R.string.toast_added_cart,
                                product.name,
                                MoneyUtils.formatCents(summary.totalCents)),
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void refreshCartBadge() {
        executor.execute(() -> {
            ProductDbHelper.CartSummary summary = dbHelper.getCartSummary();
            runOnUiThread(() -> {
                if (!destroyed) {
                    updateCartBadge(summary.totalQuantity);
                }
            });
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

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (executor != null) {
            executor.shutdown();
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
        super.onDestroy();
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
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(ProductsActivity.this)
                        .inflate(R.layout.item_product, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            Product product = getItem(position);
            holder.name.setText(product.name);
            holder.price.setText(getString(
                    R.string.product_price_format,
                    MoneyUtils.formatCents(product.priceCents)));
            holder.barcode.setText(getString(
                    R.string.barcode_prefix, product.barcode));

            String meta = "";
            if (product.brand != null && !product.brand.isEmpty()) {
                meta = product.brand;
            }
            if (product.spec != null && !product.spec.isEmpty()) {
                meta = meta.isEmpty() ? product.spec : meta + " · " + product.spec;
            }
            holder.meta.setText(meta);
            holder.meta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);

            holder.add.setOnClickListener(v -> addToCart(product));
            return convertView;
        }
    }

    private static class ViewHolder {
        final TextView name;
        final TextView meta;
        final TextView price;
        final TextView barcode;
        final TextView add;

        ViewHolder(View view) {
            name = view.findViewById(R.id.item_product_name);
            meta = view.findViewById(R.id.item_product_meta);
            price = view.findViewById(R.id.item_product_price);
            barcode = view.findViewById(R.id.item_product_barcode);
            add = view.findViewById(R.id.btn_item_add_cart);
        }
    }
}
