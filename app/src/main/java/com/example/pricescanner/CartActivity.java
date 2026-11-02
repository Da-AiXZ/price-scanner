package com.example.pricescanner;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CartActivity extends AppCompatActivity {

    private ProductDbHelper dbHelper;
    private ExecutorService executor;
    private CartAdapter adapter;
    private View emptyView;
    private TextView countView;
    private TextView totalView;
    private ListView listView;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        dbHelper = new ProductDbHelper(this);
        executor = Executors.newSingleThreadExecutor();

        ImageButton back = findViewById(R.id.btn_back);
        back.setOnClickListener(v -> finish());

        listView = findViewById(R.id.cart_list);
        emptyView = findViewById(R.id.cart_empty);
        countView = findViewById(R.id.cart_item_count);
        totalView = findViewById(R.id.cart_total_value);
        listView.setEmptyView(emptyView);

        adapter = new CartAdapter();
        listView.setAdapter(adapter);

        findViewById(R.id.btn_clear_cart).setOnClickListener(v -> confirmClear());
        findViewById(R.id.btn_checkout).setOnClickListener(v -> confirmCheckout());

        loadCart();
    }

    private void loadCart() {
        executor.execute(() -> {
            List<CartItem> items = dbHelper.getCartItems();
            ProductDbHelper.CartSummary summary = dbHelper.getCartSummary();
            runOnUiThread(() -> {
                if (destroyed) {
                    return;
                }
                adapter.setItems(items);
                countView.setText(getString(
                        R.string.cart_item_count,
                        summary.totalQuantity,
                        summary.distinctCount));
                totalView.setText(MoneyUtils.formatCents(summary.totalCents));
                findViewById(R.id.btn_checkout).setAlpha(
                        summary.totalQuantity == 0 ? 0.4f : 1f);
            });
        });
    }

    private void changeQuantity(String barcode, int quantity) {
        executor.execute(() -> {
            dbHelper.updateCartQuantity(barcode, quantity);
            loadCart();
        });
    }

    private void confirmClear() {
        ProductDbHelper.CartSummary summary = dbHelper.getCartSummary();
        if (summary.totalQuantity == 0) {
            Toast.makeText(this, R.string.toast_cart_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                .setTitle(R.string.cart_clear)
                .setMessage(R.string.cart_clear_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.cart_clear, (dialog, which) -> {
                    executor.execute(() -> {
                        dbHelper.clearCart();
                        runOnUiThread(() -> {
                            loadCart();
                            Toast.makeText(
                                    CartActivity.this,
                                    R.string.cart_cleared,
                                    Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .show();
    }

    private void confirmCheckout() {
        ProductDbHelper.CartSummary summary = dbHelper.getCartSummary();
        if (summary.totalQuantity == 0) {
            Toast.makeText(this, R.string.toast_cart_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        String message = getString(
                R.string.cart_checkout_message,
                summary.totalQuantity,
                MoneyUtils.formatCents(summary.totalCents));
        new AlertDialog.Builder(this, R.style.Theme_PriceScanner_Dialog)
                .setTitle(R.string.cart_checkout_title)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.cart_checkout, (dialog, which) ->
                        executor.execute(() -> {
                            dbHelper.clearCart();
                            runOnUiThread(() -> {
                                Toast.makeText(
                                        CartActivity.this,
                                        R.string.cart_cleared,
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }))
                .show();
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

    private class CartAdapter extends BaseAdapter {

        private final List<CartItem> items = new ArrayList<>();

        void setItems(List<CartItem> newItems) {
            items.clear();
            items.addAll(newItems);
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
            if (convertView == null) {
                convertView = LayoutInflater.from(CartActivity.this)
                        .inflate(R.layout.item_cart, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            CartItem item = getItem(position);
            holder.name.setText(item.name);
            holder.unitPrice.setText(getString(
                    R.string.cart_unit_price,
                    MoneyUtils.formatCents(item.unitPriceCents)));
            holder.quantity.setText(String.valueOf(item.quantity));
            holder.lineTotal.setText(getString(
                    R.string.cart_line_total,
                    MoneyUtils.formatCents(item.lineTotalCents())));

            holder.minus.setOnClickListener(v ->
                    changeQuantity(item.barcode, item.quantity - 1));
            holder.plus.setOnClickListener(v ->
                    changeQuantity(item.barcode, item.quantity + 1));
            return convertView;
        }
    }

    private static class ViewHolder {
        final TextView name;
        final TextView unitPrice;
        final TextView quantity;
        final TextView lineTotal;
        final TextView minus;
        final TextView plus;

        ViewHolder(View view) {
            name = view.findViewById(R.id.item_name);
            unitPrice = view.findViewById(R.id.item_unit_price);
            quantity = view.findViewById(R.id.item_quantity);
            lineTotal = view.findViewById(R.id.item_line_total);
            minus = view.findViewById(R.id.btn_minus);
            plus = view.findViewById(R.id.btn_plus);
        }
    }
}
