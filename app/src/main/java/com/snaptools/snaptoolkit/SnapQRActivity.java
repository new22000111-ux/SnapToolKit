package com.snaptools.snaptoolkit;

public class SnapQRActivity extends BaseActivity {

    @Override
    protected void onTextReceived() {
        toast(Strings.get("generating_qr"));
        new android.os.AsyncTask<Void, Void, android.graphics.Bitmap>() {
            protected android.graphics.Bitmap doInBackground(Void... v) {
                try {
                    String enc = java.net.URLEncoder.encode(selectedText, "UTF-8");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL(
                            "https://api.qrserver.com/v1/create-qr-code/?size=512x512&ecc=H&data=" + enc)
                            .openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    if (c.getResponseCode() != 200) return null;
                    android.graphics.Bitmap bmp =
                        android.graphics.BitmapFactory.decodeStream(c.getInputStream());
                    c.disconnect();
                    return bmp;
                } catch (Exception e) { return null; }
            }
            protected void onPostExecute(android.graphics.Bitmap bmp) {
                if (bmp == null) {
                    toast("❌ " + Strings.get("qr_error")); finish(); return;
                }
                showQRDialog(bmp);
            }
        }.execute();
    }

    void showQRDialog(android.graphics.Bitmap bmp) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 16);
        layout.setGravity(android.view.Gravity.CENTER);

        android.widget.ImageView img = new android.widget.ImageView(this);
        img.setImageBitmap(bmp);
        int size = (int)(getResources().getDisplayMetrics().widthPixels * 0.75f);
        android.widget.LinearLayout.LayoutParams lp =
            new android.widget.LinearLayout.LayoutParams(size, size);
        lp.gravity = android.view.Gravity.CENTER;
        img.setLayoutParams(lp);
        img.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        layout.addView(img);

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("📷 SnapQR");
        b.setView(layout);
        b.setPositiveButton("💾 " + Strings.get("save"), (d, w) -> saveQR(bmp));
        b.setNegativeButton(Strings.get("close"), null);
        b.setOnDismissListener(d -> finish());
        b.show();
    }

    void saveQR(android.graphics.Bitmap bmp) {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            protected Boolean doInBackground(Void... v) {
                try {
                    String name = "SnapQR_" + System.currentTimeMillis() + ".png";
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        android.content.ContentValues cv = new android.content.ContentValues();
                        cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
                        cv.put(android.provider.MediaStore.Downloads.MIME_TYPE, "image/png");
                        android.net.Uri uri = getContentResolver().insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                        if (uri == null) return false;
                        java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os);
                        os.close();
                    } else {
                        java.io.File dir = android.os.Environment
                            .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                        dir.mkdirs();
                        java.io.FileOutputStream fos =
                            new java.io.FileOutputStream(new java.io.File(dir, name));
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();
                    }
                    return true;
                } catch (Exception e) { return false; }
            }
            protected void onPostExecute(Boolean ok) {
                toast(ok ? "✅ " + Strings.get("saved") : "❌ " + Strings.get("error"));
            }
        }.execute();
    }
}
