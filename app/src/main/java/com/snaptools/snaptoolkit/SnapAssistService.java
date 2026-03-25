package com.snaptools.snaptoolkit;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.concurrent.Executor;

public class SnapAssistService extends AccessibilityService {

    private WindowManager windowManager;
    private View floatingTrigger;
    private LinearLayout floatingMenu;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            createFloatingViews();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No-op: actions are user-triggered from the floating menu.
    }

    @Override
    public void onInterrupt() {
        // No-op
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null) {
            if (floatingTrigger != null) {
                windowManager.removeView(floatingTrigger);
            }
            if (floatingMenu != null) {
                windowManager.removeView(floatingMenu);
            }
        }
    }

    private void createFloatingViews() {
        WindowManager.LayoutParams triggerParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        triggerParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        triggerParams.x = 24;

        Button trigger = new Button(this);
        trigger.setText("⚡");
        trigger.setOnClickListener(v -> toggleMenu());
        floatingTrigger = trigger;
        windowManager.addView(floatingTrigger, triggerParams);

        floatingMenu = new LinearLayout(this);
        floatingMenu.setOrientation(LinearLayout.VERTICAL);
        floatingMenu.setBackgroundColor(0xDD111111);
        floatingMenu.setPadding(16, 16, 16, 16);
        floatingMenu.setVisibility(View.GONE);

        addMenuButton(Strings.get("assist_translate"), v -> runToolAction(SnapTranslateActivity.class));
        addMenuButton(Strings.get("assist_copy"), v -> copyExtractedText());
        addMenuButton(Strings.get("assist_count"), v -> runToolAction(SnapCountActivity.class));
        addMenuButton(Strings.get("assist_hash"), v -> runToolAction(SnapHashActivity.class));
        addMenuButton(Strings.get("assist_url"), v -> runToolAction(SnapURLActivity.class));
        addMenuButton(Strings.get("assist_ip"), v -> runToolAction(SnapIPActivity.class));
        addMenuButton(Strings.get("assist_qr"), v -> runToolAction(SnapQRActivity.class));
        addMenuButton(Strings.get("assist_screenshot"), v -> captureScreenshot());

        WindowManager.LayoutParams menuParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        menuParams.x = 24;
        menuParams.y = 120;

        windowManager.addView(floatingMenu, menuParams);
    }

    private void addMenuButton(String label, View.OnClickListener onClick) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> {
            onClick.onClick(v);
            hideMenu();
        });
        floatingMenu.addView(btn);
    }

    private void toggleMenu() {
        if (floatingMenu == null) return;
        floatingMenu.setVisibility(floatingMenu.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void hideMenu() {
        if (floatingMenu != null) floatingMenu.setVisibility(View.GONE);
    }

    private void runToolAction(Class<?> activityClass) {
        String extracted = extractVisibleScreenText();
        if (extracted.isEmpty()) {
            toast(Strings.get("assist_ocr_empty"));
            return;
        }

        Intent intent = new Intent(this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, extracted);
        intent.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        startActivity(intent);
    }

    private void copyExtractedText() {
        String extracted = extractVisibleScreenText();
        if (extracted.isEmpty()) {
            toast(Strings.get("assist_ocr_empty"));
            return;
        }

        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("snap_assist", extracted));
            toast(Strings.get("copied"));
        } else {
            toast("❌ " + Strings.get("error"));
        }
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
        if (text != null && text.toString().trim().length() > 0) {
            lines.add(text.toString().trim());
        }

        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && contentDesc.toString().trim().length() > 0) {
            lines.add(contentDesc.toString().trim());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            collectNodeText(child, lines);
            if (child != null) {
                child.recycle();
            }
        }
    }

    private void captureScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            toast(Strings.get("assist_screenshot_unsupported"));
            return;
        }

        toast(Strings.get("assist_screenshot_taking"));
        Executor executor = command -> mainHandler.post(command);

        takeScreenshot(Display.DEFAULT_DISPLAY, executor, new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshot) {
                Bitmap bitmap = null;
                HardwareBuffer buffer = screenshot.getHardwareBuffer();
                try {
                    bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
                    if (bitmap == null) {
                        toast(Strings.get("assist_screenshot_failed"));
                        return;
                    }
                    Uri uri = saveBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, false));
                    if (uri != null) {
                        toast(Strings.get("assist_screenshot_saved") + "\n" + uri.toString());
                    } else {
                        toast(Strings.get("assist_screenshot_failed"));
                    }
                } catch (Exception e) {
                    toast(Strings.get("assist_screenshot_failed"));
                } finally {
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    if (buffer != null) {
                        buffer.close();
                    }
                }
            }

            @Override
            public void onFailure(int errorCode) {
                toast(Strings.get("assist_screenshot_failed"));
            }
        });
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
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private void toast(String msg) {
        mainHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }
}
