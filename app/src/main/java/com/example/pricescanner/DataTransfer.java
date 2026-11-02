package com.example.pricescanner;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DataTransfer {

    private static final int SCHEMA_VERSION = 2;
    private static final long MAX_IMPORT_BYTES = 20L * 1024 * 1024;
    private static final String LICENSE_NOTICE =
            "部分商品资料来自 Open Food Facts（ODbL 1.0），仅供参考。";

    private DataTransfer() {
    }

    public static class ImportData {
        public final List<Product> products;
        public final List<ProductInfo> productInfo;
        public final long exportedAt;

        ImportData(List<Product> products, List<ProductInfo> productInfo, long exportedAt) {
            this.products = products;
            this.productInfo = productInfo;
            this.exportedAt = exportedAt;
        }
    }

    public static String buildJson(
            List<Product> products, List<ProductInfo> productInfo, long exportedAt) {
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("app", "PriceScanner");
            root.put("appVersion", "1.9");
            root.put("exportedAt", exportedAt);
            root.put("licenseNotice", LICENSE_NOTICE);

            JSONArray productArray = new JSONArray();
            for (Product product : products) {
                JSONObject item = new JSONObject();
                item.put("barcode", product.barcode);
                item.put("name", product.name);
                item.put("brand", product.brand);
                item.put("spec", product.spec);
                item.put("priceCents", product.priceCents);
                item.put("priceUpdatedAt", product.priceUpdatedAt);
                item.put("nameUpdatedAt", product.nameUpdatedAt);
                item.put("source", product.source);
                item.put("category", product.category);
                productArray.put(item);
            }
            root.put("products", productArray);

            JSONArray infoArray = new JSONArray();
            for (ProductInfo info : productInfo) {
                if (!info.hasUsableName()) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("barcode", info.barcode);
                item.put("name", info.name);
                item.put("brand", info.brand);
                item.put("spec", info.spec);
                item.put("productType", info.productType);
                item.put("source", info.source);
                item.put("fetchedAt", info.fetchedAt);
                item.put("userEdited", info.userEdited);
                infoArray.put(item);
            }
            root.put("productInfo", infoArray);

            return root.toString(2);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build export JSON", e);
        }
    }

    public static ImportData parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        int schemaVersion = root.optInt("schemaVersion", 0);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "不支持的文件版本：" + schemaVersion + "，请升级 App");
        }

        long exportedAt = root.optLong("exportedAt", System.currentTimeMillis());
        List<Product> products = new ArrayList<>();
        JSONArray productArray = root.optJSONArray("products");
        if (productArray != null) {
            for (int i = 0; i < productArray.length(); i++) {
                JSONObject item = productArray.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String barcode = item.optString("barcode").trim();
                String name = item.optString("name").trim();
                if (barcode.isEmpty() || name.isEmpty()) {
                    continue;
                }
                long priceCents;
                if (item.has("priceCents")) {
                    priceCents = item.optLong("priceCents", -1);
                } else {
                    double yuan = item.optDouble("price", -1);
                    priceCents = yuan < 0 ? -1 : Math.round(yuan * 100);
                }
                if (priceCents < 0) {
                    continue;
                }
                long priceUpdatedAt = item.optLong("priceUpdatedAt", exportedAt);
                long nameUpdatedAt = item.optLong("nameUpdatedAt", priceUpdatedAt);
                String source = item.optString("source", "manual");
                if (source.isEmpty()) {
                    source = "manual";
                }
                products.add(new Product(
                        barcode,
                        name,
                        item.optString("brand", ""),
                        item.optString("spec", ""),
                        priceCents,
                        priceUpdatedAt,
                        nameUpdatedAt,
                        source,
                        item.optString("category", "")));
            }
        }

        List<ProductInfo> infos = new ArrayList<>();
        JSONArray infoArray = root.optJSONArray("productInfo");
        if (infoArray != null) {
            for (int i = 0; i < infoArray.length(); i++) {
                JSONObject item = infoArray.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String barcode = item.optString("barcode").trim();
                String name = item.optString("name").trim();
                if (barcode.isEmpty() || name.isEmpty()) {
                    continue;
                }
                infos.add(new ProductInfo(
                        barcode,
                        name,
                        item.optString("brand", ""),
                        item.optString("spec", ""),
                        item.optString("productType", ""),
                        item.optString("source", "openfoodfacts"),
                        item.optLong("fetchedAt", exportedAt),
                        true,
                        item.optBoolean("userEdited", false)));
            }
        }

        return new ImportData(products, infos, exportedAt);
    }

    public static String readText(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_IMPORT_BYTES) {
                throw new IllegalArgumentException("文件过大，最大支持 20MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toString("UTF-8");
    }

    public static String readFile(File file) throws Exception {
        try (InputStream input = new FileInputStream(file)) {
            return readText(input);
        }
    }

    public static File writeExportFile(Context context, String json) throws Exception {
        File dir = new File(context.getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建导出目录");
        }
        String name = "price-scanner-" + timestamp() + ".json";
        File file = new File(dir, name);
        writeAtomically(file, json);
        return file;
    }

    public static File createBackup(Context context, String json) throws Exception {
        File dir = new File(context.getFilesDir(), "backups");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建备份目录");
        }
        File file = new File(dir, "backup-" + timestamp() + ".json");
        writeAtomically(file, json);

        File[] files = dir.listFiles();
        if (files != null && files.length > 3) {
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File left, File right) {
                    return Long.compare(right.lastModified(), left.lastModified());
                }
            });
            int kept = 0;
            for (File candidate : files) {
                if (!candidate.getName().endsWith(".json")) {
                    continue;
                }
                kept++;
                if (kept > 3) {
                    //noinspection ResultOfMethodCallIgnored
                    candidate.delete();
                }
            }
        }
        return file;
    }

    public static List<File> listBackups(Context context) {
        File dir = new File(context.getFilesDir(), "backups");
        File[] files = dir.listFiles();
        List<File> backups = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".json")) {
                    backups.add(file);
                }
            }
            backups.sort((left, right) ->
                    Long.compare(right.lastModified(), left.lastModified()));
        }
        return backups;
    }

    private static void writeAtomically(File file, String json) throws Exception {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
        if (!temp.renameTo(file)) {
            throw new IllegalStateException("写入文件失败");
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
    }
}
