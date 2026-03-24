package com.snaptools.snaptoolkit;

public class SnapConvertActivity extends android.app.Activity {

    android.net.Uri inputUri;
    String mimeType;

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        inputUri = getIntent().getParcelableExtra(android.content.Intent.EXTRA_STREAM);
        mimeType = getIntent().getType();
        if (inputUri == null || mimeType == null) {
            toast(Strings.get("error")); finish(); return;
        }
        if (mimeType.startsWith("image/")) showImageDialog();
        else if (mimeType.startsWith("audio/")) showAudioDialog();
        else { toast(Strings.get("error")); finish(); }
    }

    void showImageDialog() {
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<android.graphics.Bitmap.CompressFormat> fmts = new java.util.ArrayList<>();
        java.util.List<String> exts  = new java.util.ArrayList<>();
        java.util.List<String> mimes = new java.util.ArrayList<>();

        if (!mimeType.contains("jpeg") && !mimeType.contains("jpg")) {
            labels.add("JPG"); fmts.add(android.graphics.Bitmap.CompressFormat.JPEG);
            exts.add(".jpg"); mimes.add("image/jpeg");
        }
        if (!mimeType.contains("png")) {
            labels.add("PNG"); fmts.add(android.graphics.Bitmap.CompressFormat.PNG);
            exts.add(".png"); mimes.add("image/png");
        }
        if (!mimeType.contains("webp")) {
            labels.add("WebP"); fmts.add(android.graphics.Bitmap.CompressFormat.WEBP);
            exts.add(".webp"); mimes.add("image/webp");
        }

        if (labels.isEmpty()) { toast(Strings.get("error")); finish(); return; }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("🔄 " + Strings.get("select_format"));
        builder.setItems(labels.toArray(new String[0]), (d, i) ->
            convertImage(fmts.get(i), exts.get(i), mimes.get(i)));
        builder.setOnCancelListener(d -> finish());
        builder.show();
    }

    void convertImage(android.graphics.Bitmap.CompressFormat fmt, String ext, String mime) {
        toast(Strings.get("converting"));
        new android.os.AsyncTask<Void, Void, Boolean>() {
            protected Boolean doInBackground(Void... v) {
                try {
                    java.io.InputStream is = getContentResolver().openInputStream(inputUri);
                    if (is == null) return false;
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
                    is.close();
                    if (bmp == null) return false;

                    int quality = fmt == android.graphics.Bitmap.CompressFormat.PNG ? 100 : 90;
                    String name = "SnapConvert_" + System.currentTimeMillis() + ext;

                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        android.content.ContentValues cv = new android.content.ContentValues();
                        cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
                        cv.put(android.provider.MediaStore.Downloads.MIME_TYPE, mime);
                        android.net.Uri uri = getContentResolver().insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                        if (uri == null) return false;
                        java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                        bmp.compress(fmt, quality, os);
                        os.close();
                    } else {
                        java.io.File dir = android.os.Environment
                            .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                        dir.mkdirs();
                        java.io.FileOutputStream fos =
                            new java.io.FileOutputStream(new java.io.File(dir, name));
                        bmp.compress(fmt, quality, fos);
                        fos.close();
                    }
                    bmp.recycle();
                    return true;
                } catch (Exception e) { return false; }
            }
            protected void onPostExecute(Boolean ok) {
                toast(ok ? "✅ " + Strings.get("convert_done")
                         : "❌ " + Strings.get("convert_error"));
                finish();
            }
        }.execute();
    }

    void showAudioDialog() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("🔄 " + Strings.get("select_format"));
        b.setItems(new String[]{"WAV"}, (d, w) -> convertToWav());
        b.setOnCancelListener(d -> finish());
        b.show();
    }

    void convertToWav() {
        toast(Strings.get("converting"));
        new android.os.AsyncTask<Void, Void, Boolean>() {
            protected Boolean doInBackground(Void... v) {
                android.media.MediaExtractor ex = new android.media.MediaExtractor();
                android.media.MediaCodec codec = null;
                java.io.RandomAccessFile raf = null;
                try {
                    ex.setDataSource(SnapConvertActivity.this, inputUri, null);
                    android.media.MediaFormat fmt = null;
                    for (int i = 0; i < ex.getTrackCount(); i++) {
                        android.media.MediaFormat f = ex.getTrackFormat(i);
                        String m = f.getString(android.media.MediaFormat.KEY_MIME);
                        if (m != null && m.startsWith("audio/")) {
                            fmt = f; ex.selectTrack(i); break;
                        }
                    }
                    if (fmt == null) return false;

                    int sampleRate = fmt.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE);
                    int channels   = fmt.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT);
                    String mime    = fmt.getString(android.media.MediaFormat.KEY_MIME);

                    codec = android.media.MediaCodec.createDecoderByType(mime);
                    codec.configure(fmt, null, null, 0);
                    codec.start();

                    String name = "SnapConvert_" + System.currentTimeMillis() + ".wav";
                    java.io.File tmp = new java.io.File(getCacheDir(), name);
                    raf = new java.io.RandomAccessFile(tmp, "rw");
                    writeWavHeader(raf, 0, sampleRate, channels);

                    android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
                    long totalBytes = 0;
                    boolean inputDone = false;

                    outer:
                    while (true) {
                        if (!inputDone) {
                            int idx = codec.dequeueInputBuffer(10000);
                            if (idx >= 0) {
                                java.nio.ByteBuffer buf = codec.getInputBuffer(idx);
                                buf.clear();
                                int sz = ex.readSampleData(buf, 0);
                                if (sz < 0) {
                                    codec.queueInputBuffer(idx, 0, 0, 0,
                                        android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inputDone = true;
                                } else {
                                    codec.queueInputBuffer(idx, 0, sz, ex.getSampleTime(), 0);
                                    ex.advance();
                                }
                            }
                        }
                        int outIdx = codec.dequeueOutputBuffer(info, 10000);
                        if (outIdx >= 0) {
                            java.nio.ByteBuffer outBuf = codec.getOutputBuffer(outIdx);
                            byte[] chunk = new byte[info.size];
                            outBuf.get(chunk);
                            raf.write(chunk);
                            totalBytes += chunk.length;
                            codec.releaseOutputBuffer(outIdx, false);
                            if ((info.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                                break;
                        }
                    }

                    // Fix WAV header sizes
                    raf.seek(4);  writeInt(raf, (int)(totalBytes + 36));
                    raf.seek(40); writeInt(raf, (int) totalBytes);
                    raf.close(); raf = null;

                    // Copy to Downloads
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        android.content.ContentValues cv = new android.content.ContentValues();
                        cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
                        cv.put(android.provider.MediaStore.Downloads.MIME_TYPE, "audio/wav");
                        android.net.Uri uri = getContentResolver().insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                        if (uri == null) return false;
                        java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                        java.io.FileInputStream fis = new java.io.FileInputStream(tmp);
                        byte[] buf = new byte[8192]; int n;
                        while ((n = fis.read(buf)) != -1) os.write(buf, 0, n);
                        fis.close(); os.close();
                        tmp.delete();
                    }
                    return true;
                } catch (Exception e) {
                    return false;
                } finally {
                    if (codec != null) try { codec.stop(); codec.release(); } catch (Exception ignored) {}
                    ex.release();
                    if (raf != null) try { raf.close(); } catch (Exception ignored) {}
                }
            }
            protected void onPostExecute(Boolean ok) {
                toast(ok ? "✅ " + Strings.get("convert_done")
                         : "❌ " + Strings.get("convert_error"));
                finish();
            }
        }.execute();
    }

    void writeWavHeader(java.io.RandomAccessFile f, int sz, int sr, int ch) throws Exception {
        int bps = 16, br = sr * ch * bps / 8, ba = ch * bps / 8;
        f.write("RIFF".getBytes()); writeInt(f, sz + 36);
        f.write("WAVE".getBytes()); f.write("fmt ".getBytes());
        writeInt(f, 16); writeShort(f, (short)1); writeShort(f, (short)ch);
        writeInt(f, sr); writeInt(f, br); writeShort(f, (short)ba);
        writeShort(f, (short)bps); f.write("data".getBytes()); writeInt(f, sz);
    }
    void writeInt(java.io.RandomAccessFile f, int v) throws Exception {
        f.write(v&0xFF); f.write((v>>8)&0xFF); f.write((v>>16)&0xFF); f.write((v>>24)&0xFF);
    }
    void writeShort(java.io.RandomAccessFile f, short v) throws Exception {
        f.write(v&0xFF); f.write((v>>8)&0xFF);
    }
    void toast(String msg) {
        runOnUiThread(() -> android.widget.Toast.makeText(this, msg,
            android.widget.Toast.LENGTH_LONG).show());
    }
}
