package com.snaptools.snaptoolkit;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SnapAssistService extends AccessibilityService {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST_PATTERN = Pattern.compile("([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;

    private LinearLayout toolSheet;
    private View resultCard;
    private EdgeHighlightView edgeHighlightView;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        toast(Strings.get("assist_hint_swipe"));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Gesture-driven service; no per-event action needed.
    }

    @Override
    public void onInterrupt() {
        // No-op
    }

    @Override
    protected boolean onGesture(int gestureId) {
        if (isThreeFingerGesture(gestureId)) {
            activateAssist();
            return true;
        }
        return super.onGesture(gestureId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeViewSafely(toolSheet);
        removeViewSafely(resultCard);
        removeViewSafely(edgeHighlightView);
    }

    private boolean isThreeFingerGesture(int gestureId) {
        return gestureId == GESTURE_3_FINGER_SWIPE_UP
            || gestureId == GESTURE_3_FINGER_SWIPE_DOWN
            || gestureId == GESTURE_3_FINGER_SWIPE_LEFT
            || gestureId == GESTURE_3_FINGER_SWIPE_RIGHT
            || gestureId == GESTURE_3_FINGER_SINGLE_TAP
            || gestureId == GESTURE_3_FINGER_DOUBLE_TAP;
    }

    private void activateAssist() {
        String extracted = extractVisibleScreenText();
        if (extracted.isEmpty()) {
            toast(Strings.get("assist_ocr_empty"));
            return;
        }

        flashEdgeHighlight();
        showToolSheet(extracted);
        toast(Strings.get("assist_triggered"));
    }

    private void showToolSheet(String extractedText) {
        removeViewSafely(toolSheet);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), dp(14), dp(18), dp(18));
        container.setElevation(dp(12));

        GradientDrawable glass = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xAA1E2A3A, 0xAA0F1828}
        );
        glass.setCornerRadius(dp(26));
        glass.setStroke(dp(1), 0x663FA9FF);
        container.setBackground(glass);

        TextView title = new TextView(this);
        title.setText(Strings.get("assist_menu_title"));
        title.setTextColor(0xFFE9F4FF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setPadding(0, 0, 0, dp(10));
        container.addView(title);

        LinearLayout row1 = createActionRow();
        LinearLayout row2 = createActionRow();

        row1.addView(createLiquidButton(Strings.get("assist_translate"), v -> runTranslate(extractedText)));
        row1.addView(createLiquidButton(Strings.get("assist_count"), v -> showCount(extractedText)));
        row1.addView(createLiquidButton(Strings.get("assist_hash"), v -> showHash(extractedText)));

        row2.addView(createLiquidButton(Strings.get("assist_url"), v -> runShortUrl(extractedText)));
        row2.addView(createLiquidButton(Strings.get("assist_ip"), v -> runIpLookup(extractedText)));
        row2.addView(createLiquidButton(Strings.get("assist_qr"), v -> runQr(extractedText)));
        row2.addView(createLiquidButton(Strings.get("assist_screenshot"), v -> captureScreenshot()));

        container.addView(row1);
        container.addView(row2);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM;
        params.x = dp(14);
        params.y = dp(28);

        toolSheet = container;
        windowManager.addView(toolSheet, params);
        animateSheetIn(toolSheet);

        mainHandler.postDelayed(this::hideToolSheet, 9000);
    }

    private LinearLayout createActionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(4), 0, dp(4));
        return row;
    }

    private Button createLiquidButton(String text, View.OnClickListener click) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(0xFFE8F3FF);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

        GradientDrawable chip = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0x553DA6FF, 0x5532D3FF}
        );
        chip.setCornerRadius(dp(18));
        chip.setStroke(dp(1), 0x994EB6FF);
        button.setBackground(chip);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        button.setLayoutParams(lp);

        button.setOnClickListener(v -> {
            animateLiquidTap(v);
            click.onClick(v);
        });
        return button;
    }

    private void animateSheetIn(View v) {
        v.setAlpha(0f);
        v.setTranslationY(dp(36));
        v.setScaleX(0.96f);
        v.setScaleY(0.96f);
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(360)
            .setInterpolator(new OvershootInterpolator(1.15f))
            .start();
    }

    private void animateLiquidTap(View view) {
        view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).withEndAction(() ->
            view.animate().scaleX(1f).scaleY(1f).setDuration(180)
                .setInterpolator(new OvershootInterpolator(2f)).start()
        ).start();
    }

    private void hideToolSheet() {
        if (toolSheet == null) return;
        View target = toolSheet;
        target.animate().alpha(0f).translationY(dp(22)).setDuration(180).withEndAction(() -> {
            removeViewSafely(target);
            if (toolSheet == target) toolSheet = null;
        }).start();
    }

    private void flashEdgeHighlight() {
        removeViewSafely(edgeHighlightView);
        edgeHighlightView = new EdgeHighlightView(this);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        );
        p.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(edgeHighlightView, p);

        edgeHighlightView.startPulse(() -> {
            removeViewSafely(edgeHighlightView);
            edgeHighlightView = null;
        });
    }

    private String extractVisibleScreenText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";

        LinkedHashSet<String> lines = new LinkedHashSet<>();
        collectNodeText(root, lines);
        root.recycle();

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString().trim();
    }

    private void collectNodeText(AccessibilityNodeInfo node, LinkedHashSet<String> lines) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (!TextUtils.isEmpty(text)) lines.add(text.toString().trim());

        CharSequence desc = node.getContentDescription();
        if (!TextUtils.isEmpty(desc)) lines.add(desc.toString().trim());

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            collectNodeText(child, lines);
            if (child != null) child.recycle();
        }
    }

    private void runTranslate(String text) {
        hideToolSheet();
        showResultText(Strings.get("assist_translate"), Strings.get("translating"));
        final String targetLang = Locale.getDefault().getLanguage();

        new android.os.AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... voids) {
                try {
                    String enc = URLEncoder.encode(text, "UTF-8");
                    String api = "https://api.mymemory.translated.net/get?q=" + enc
                        + "&langpair=autodetect|" + targetLang;
                    HttpURLConnection c = (HttpURLConnection) new URL(api).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    if (c.getResponseCode() != 200) return null;

                    java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    c.disconnect();

                    String json = sb.toString();
                    int start = json.indexOf("\"translatedText\":\"");
                    if (start < 0) return null;
                    start += 18;
                    int end = json.indexOf("\"", start);
                    if (end < 0) return null;
                    return json.substring(start, end)
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
                } catch (Exception e) {
                    return null;
                }
            }

            protected void onPostExecute(String translated) {
                if (translated == null || translated.trim().isEmpty()) {
                    showResultText(Strings.get("assist_translate"), "❌ " + Strings.get("translate_error"));
                } else {
                    showResultText(Strings.get("assist_translate"), translated);
                }
            }
        }.execute();
    }

    private void showCount(String text) {
        hideToolSheet();
        int chars = text.length();
        int noSpaces = text.replaceAll("\\s", "").length();
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        int lines = text.split("\\n", -1).length;
        int sentences = countSentences(text);

        String message = "Words: " + words
            + "\nCharacters: " + chars
            + "\nNo spaces: " + noSpaces
            + "\nLines: " + lines
            + "\nSentences: " + sentences;
        showResultText(Strings.get("assist_count"), message);
    }

    private void showHash(String text) {
        hideToolSheet();
        try {
            byte[] bytes = text.getBytes("UTF-8");
            String md5 = hash(bytes, "MD5");
            String sha = hash(bytes, "SHA-256");
            showResultText(Strings.get("assist_hash"), "MD5:\n" + md5 + "\n\nSHA-256:\n" + sha);
        } catch (Exception e) {
            showResultText(Strings.get("assist_hash"), "❌ " + Strings.get("error"));
        }
    }

    private void runShortUrl(String text) {
        hideToolSheet();
        String url = findFirstUrl(text);
        if (url == null) {
            showResultText(Strings.get("assist_url"), "❌ " + Strings.get("no_valid_url"));
            return;
        }

        showResultText(Strings.get("assist_url"), Strings.get("shortening"));
        new android.os.AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... voids) {
                try {
                    String enc = URLEncoder.encode(url, "UTF-8");
                    HttpURLConnection c = (HttpURLConnection)
                        new URL("https://is.gd/create.php?format=simple&url=" + enc).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(c.getInputStream()));
                    String res = r.readLine();
                    r.close();
                    c.disconnect();
                    return (res != null && res.startsWith("http")) ? res : null;
                } catch (Exception e) {
                    return null;
                }
            }

            protected void onPostExecute(String shortUrl) {
                if (shortUrl == null) {
                    showResultText(Strings.get("assist_url"), "❌ " + Strings.get("shorten_error"));
                } else {
                    showResultText(Strings.get("assist_url"), shortUrl);
                }
            }
        }.execute();
    }

    private void runIpLookup(String text) {
        hideToolSheet();
        String host = findHost(text);
        if (host == null) {
            showResultText(Strings.get("assist_ip"), "❌ " + Strings.get("ip_error"));
            return;
        }

        showResultText(Strings.get("assist_ip"), Strings.get("looking_up") + " " + host);
        new android.os.AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... voids) {
                try {
                    String ip = InetAddress.getByName(host).getHostAddress();
                    HttpURLConnection c = (HttpURLConnection) new URL("https://ipapi.co/" + ip + "/json/").openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setRequestProperty("User-Agent", "SnapToolKit/1.0");
                    if (c.getResponseCode() != 200) return "Host: " + host + "\nIP: " + ip;

                    java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    c.disconnect();

                    String json = sb.toString();
                    return "Host: " + host
                        + "\nIP: " + ip
                        + "\nCountry: " + val(json, "country_name")
                        + "\nCity: " + val(json, "city")
                        + "\nRegion: " + val(json, "region")
                        + "\nISP: " + val(json, "org")
                        + "\nTimezone: " + val(json, "timezone");
                } catch (Exception e) {
                    return null;
                }
            }

            protected void onPostExecute(String info) {
                if (info == null) {
                    showResultText(Strings.get("assist_ip"), "❌ " + Strings.get("ip_error"));
                } else {
                    showResultText(Strings.get("assist_ip"), info);
                }
            }
        }.execute();
    }

    private void runQr(String text) {
        hideToolSheet();
        showResultText(Strings.get("assist_qr"), Strings.get("generating_qr"));

        new android.os.AsyncTask<Void, Void, Bitmap>() {
            protected Bitmap doInBackground(Void... voids) {
                try {
                    String enc = URLEncoder.encode(text, "UTF-8");
                    HttpURLConnection c = (HttpURLConnection) new URL(
                        "https://api.qrserver.com/v1/create-qr-code/?size=512x512&ecc=H&data=" + enc)
                        .openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    if (c.getResponseCode() != 200) return null;
                    Bitmap bmp = android.graphics.BitmapFactory.decodeStream(c.getInputStream());
                    c.disconnect();
                    return bmp;
                } catch (Exception e) {
                    return null;
                }
            }

            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap == null) {
                    showResultText(Strings.get("assist_qr"), "❌ " + Strings.get("qr_error"));
                } else {
                    showResultImage(Strings.get("assist_qr"), bitmap);
                }
            }
        }.execute();
    }

    private void captureScreenshot() {
        hideToolSheet();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            showResultText(Strings.get("assist_screenshot"), Strings.get("assist_screenshot_unsupported"));
            return;
        }

        showResultText(Strings.get("assist_screenshot"), Strings.get("assist_screenshot_taking"));
        Executor executor = command -> mainHandler.post(command);
        takeScreenshot(Display.DEFAULT_DISPLAY, executor, new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshot) {
                Bitmap bitmap = null;
                HardwareBuffer buffer = screenshot.getHardwareBuffer();
                try {
                    bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
                    if (bitmap == null) {
                        showResultText(Strings.get("assist_screenshot"), Strings.get("assist_screenshot_failed"));
                        return;
                    }
                    Uri uri = saveBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, false));
                    if (uri != null) {
                        showResultText(Strings.get("assist_screenshot"),
                            Strings.get("assist_screenshot_saved") + "\n" + uri);
                    } else {
                        showResultText(Strings.get("assist_screenshot"), Strings.get("assist_screenshot_failed"));
                    }
                } catch (Exception e) {
                    showResultText(Strings.get("assist_screenshot"), Strings.get("assist_screenshot_failed"));
                } finally {
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                    if (buffer != null) buffer.close();
                }
            }

            @Override
            public void onFailure(int errorCode) {
                showResultText(Strings.get("assist_screenshot"), Strings.get("assist_screenshot_failed"));
            }
        });
    }

    private void showResultText(String title, String body) {
        LinearLayout content = createResultShell(title);
        TextView message = new TextView(this);
        message.setText(body);
        message.setTextColor(0xFFE8F5FF);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        message.setLineSpacing(0f, 1.1f);
        content.addView(message);
        showResultCard(content);
    }

    private void showResultImage(String title, Bitmap bitmap) {
        LinearLayout content = createResultShell(title);
        ImageView image = new ImageView(this);
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(180), dp(180));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(6);
        image.setLayoutParams(lp);
        content.addView(image);
        showResultCard(content);
    }

    private LinearLayout createResultShell(String titleText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setElevation(dp(10));

        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xB2213147, 0xB1101723}
        );
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0x8847B6FF);
        card.setBackground(bg);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(0xFF9AD6FF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setPadding(0, 0, 0, dp(6));
        card.addView(title);

        return card;
    }

    private void showResultCard(View content) {
        removeViewSafely(resultCard);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = dp(70);

        resultCard = content;
        windowManager.addView(resultCard, p);

        resultCard.setAlpha(0f);
        resultCard.setTranslationY(-dp(18));
        resultCard.animate().alpha(1f).translationY(0f).setDuration(280)
            .setInterpolator(new OvershootInterpolator(1f)).start();

        mainHandler.postDelayed(() -> {
            if (resultCard != null) {
                View closing = resultCard;
                closing.animate().alpha(0f).translationY(-dp(12)).setDuration(220)
                    .withEndAction(() -> {
                        removeViewSafely(closing);
                        if (resultCard == closing) resultCard = null;
                    }).start();
            }
        }, 7000);
    }

    private Uri saveBitmap(Bitmap bitmap) {
        String fileName = "snap_" + System.currentTimeMillis() + ".png";
        OutputStream out = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/SnapToolKit");
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return null;
                out = resolver.openOutputStream(uri);
                if (out == null) return null;
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                return uri;
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "SnapToolKit");
                if (!dir.exists() && !dir.mkdirs()) return null;

                File file = new File(dir, fileName);
                out = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                return Uri.fromFile(file);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (out != null) {
                try { out.close(); } catch (Exception ignored) {}
            }
        }
    }

    private String findFirstUrl(String text) {
        Matcher m = URL_PATTERN.matcher(text);
        if (m.find()) return m.group();
        return null;
    }

    private String findHost(String text) {
        String url = findFirstUrl(text);
        if (url != null) {
            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (!TextUtils.isEmpty(host)) return host;
            } catch (Exception ignored) {}
        }

        Matcher m = HOST_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    private int countSentences(String text) {
        Matcher m = Pattern.compile("[.!?]+").matcher(text);
        int count = 0;
        while (m.find()) count++;
        if (count == 0 && !text.trim().isEmpty()) return 1;
        return count;
    }

    private String hash(byte[] data, String algorithm) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String val(String json, String key) {
        String search = "\"" + key + "\": \"";
        int s = json.indexOf(search);
        if (s < 0) return "—";
        s += search.length();
        int e = json.indexOf("\"", s);
        return (e > s) ? json.substring(s, e) : "—";
    }

    private void toast(String msg) {
        mainHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private void removeViewSafely(View view) {
        if (view == null || windowManager == null) return;
        try {
            windowManager.removeView(view);
        } catch (Exception ignored) {}
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics());
    }

    private static class EdgeHighlightView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float glowAlpha = 0f;

        EdgeHighlightView(android.content.Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(12f);
            paint.setColor(0xFF2D9CFF);
        }

        void startPulse(Runnable onEnd) {
            android.animation.ValueAnimator animator =
                android.animation.ValueAnimator.ofFloat(0f, 1f, 0.3f, 1f, 0f);
            animator.setDuration(880);
            animator.addUpdateListener(a -> {
                glowAlpha = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (onEnd != null) onEnd.run();
                }
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setAlpha((int) (255 * glowAlpha));
            float inset = 8f;
            canvas.drawRoundRect(
                inset,
                inset,
                getWidth() - inset,
                getHeight() - inset,
                30f,
                30f,
                paint
            );
        }
    }
}
