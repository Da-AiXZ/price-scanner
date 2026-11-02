package com.example.pricescanner;

import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

public final class OpenFoodFactsClient {

    private static final String TAG = "OpenFoodFacts";
    private static final String USER_AGENT =
            "PriceScanner/1.3 (https://github.com/Da-AiXZ/price-scanner)";
    private static final String FIELDS =
            "code,product_name,product_name_zh,generic_name,generic_name_zh,"
                    + "abbreviated_product_name,brands,quantity,product_quantity,"
                    + "product_quantity_unit,product_type";
    private static final long MIN_INTERVAL_MS = 10000L;
    private static final Object RATE_LOCK = new Object();
    private static long lastRequestAt = 0L;

    private OpenFoodFactsClient() {
    }

    public enum Status {
        FOUND,
        NOT_FOUND,
        BUSY,
        ERROR
    }

    public static class Result {
        public final Status status;
        public final ProductInfo info;
        public final String message;

        private Result(Status status, ProductInfo info, String message) {
            this.status = status;
            this.info = info;
            this.message = message;
        }

        public static Result found(ProductInfo info) {
            return new Result(Status.FOUND, info, null);
        }

        public static Result notFound(String barcode) {
            long now = System.currentTimeMillis();
            return new Result(Status.NOT_FOUND,
                    new ProductInfo(barcode, "", "", "", "", "openfoodfacts",
                            now, false, false),
                    null);
        }

        public static Result busy() {
            return new Result(Status.BUSY, null, "免费接口繁忙，请稍后或手动填写");
        }

        public static Result error(String message) {
            return new Result(Status.ERROR, null, message);
        }
    }

    public static Result lookup(String barcode) {
        try {
            waitForRateLimit();
            String encoded = URLEncoder.encode(barcode, "UTF-8");
            String url = "https://world.openfoodfacts.org/api/v3/product/" + encoded
                    + ".json?product_type=all&lc=zh&fields=" + FIELDS;
            HttpResult response = get(url, 0);
            if (response.code == 404) {
                return Result.notFound(barcode);
            }
            if (response.code == 429 || response.code == 503) {
                return Result.busy();
            }
            if (response.code != 200 || response.body == null) {
                return Result.error("联网查询失败，请手动填写");
            }

            JSONObject root = new JSONObject(response.body);
            JSONObject product = root.optJSONObject("product");
            if (product == null) {
                return Result.notFound(barcode);
            }

            String name = firstNonEmpty(
                    product.optString("product_name_zh"),
                    product.optString("product_name"),
                    product.optString("generic_name_zh"),
                    product.optString("generic_name"),
                    product.optString("abbreviated_product_name"));
            if (name.isEmpty()) {
                return Result.notFound(barcode);
            }

            String brand = selectBrand(product.optString("brands"), name);
            String spec = buildSpec(product);
            String displayName = buildDisplayName(name, brand, spec);
            String productType = product.optString("product_type");

            ProductInfo info = new ProductInfo(
                    barcode,
                    displayName,
                    brand,
                    spec,
                    productType,
                    "openfoodfacts",
                    System.currentTimeMillis(),
                    true,
                    false);
            return Result.found(info);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.error("查询已取消");
        } catch (Exception e) {
            Log.w(TAG, "Product lookup failed", e);
            return Result.error("联网查询失败，请手动填写");
        }
    }

    private static void waitForRateLimit() throws InterruptedException {
        synchronized (RATE_LOCK) {
            long now = System.currentTimeMillis();
            long wait = lastRequestAt + MIN_INTERVAL_MS - now;
            if (wait > 0) {
                Thread.sleep(wait);
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }

    private static HttpResult get(String url, int redirectCount) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");

            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                if (location == null || redirectCount >= 2 || !isAllowedUrl(location)) {
                    return new HttpResult(code, null);
                }
                return get(location, redirectCount + 1);
            }
            if (code == 200) {
                try (InputStream input = connection.getInputStream()) {
                    return new HttpResult(code, readLimited(input));
                }
            }
            return new HttpResult(code, null);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isAllowedUrl(String url) {
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            return host.endsWith(".openfoodfacts.org")
                    || host.endsWith(".openbeautyfacts.org")
                    || host.endsWith(".openpetfoodfacts.org")
                    || host.endsWith(".openproductsfacts.org");
        } catch (Exception e) {
            return false;
        }
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > 1024 * 1024) {
                throw new IllegalStateException("Response too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toString("UTF-8");
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String selectBrand(String brands, String name) {
        if (brands == null || brands.trim().isEmpty()) {
            return "";
        }
        String[] parts = brands.split(",");
        String first = parts[0].trim();
        String lowerName = name.toLowerCase(Locale.ROOT);
        for (String part : parts) {
            String candidate = part.trim();
            if (!candidate.isEmpty()
                    && lowerName.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return first;
    }

    private static String buildSpec(JSONObject product) {
        String quantity = product.optString("quantity").trim();
        if (!quantity.isEmpty()) {
            return cleanSpec(quantity);
        }
        String amount = product.optString("product_quantity").trim();
        String unit = product.optString("product_quantity_unit").trim();
        if (!amount.isEmpty() && !unit.isEmpty()) {
            return cleanSpec(amount + unit);
        }
        return "";
    }

    private static String cleanSpec(String spec) {
        String value = spec.trim();
        value = value.replaceAll("\\s*[eE℮]$", "");
        return value.replaceAll("\\s+", "");
    }

    private static String buildDisplayName(String name, String brand, String spec) {
        String display = name.trim();
        if (!brand.isEmpty()
                && !display.toLowerCase(Locale.ROOT)
                .contains(brand.toLowerCase(Locale.ROOT))) {
            display = brand + display;
        }
        if (!spec.isEmpty()) {
            String normalizedName = normalizeForCompare(display);
            String normalizedSpec = normalizeForCompare(spec);
            if (!normalizedName.contains(normalizedSpec)) {
                display = display + "（" + spec + "）";
            }
        }
        return display;
    }

    private static String normalizeForCompare(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace('（', '(')
                .replace('）', ')');
    }

    private static class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }
}
