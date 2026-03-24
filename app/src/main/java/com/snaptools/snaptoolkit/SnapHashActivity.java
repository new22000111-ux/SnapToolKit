package com.snaptools.snaptoolkit;

public class SnapHashActivity extends BaseActivity {

    @Override
    protected void onTextReceived() {
        new android.os.AsyncTask<Void, Void, String[]>() {
            protected String[] doInBackground(Void... v) {
                try {
                    byte[] bytes = selectedText.getBytes("UTF-8");
                    return new String[]{ hash(bytes,"MD5"), hash(bytes,"SHA-256") };
                } catch (Exception e) { return null; }
            }
            protected void onPostExecute(String[] h) {
                if (h == null) { toast("❌ " + Strings.get("error")); finish(); return; }
                String msg = "MD5:\n" + h[0] + "\n\nSHA-256:\n" + h[1];
                copyToClipboard(msg);
                showDialog("🔐 SnapHash", msg,
                    "✅ " + Strings.get("copied"), null,
                    Strings.get("close"));
            }
        }.execute();
    }

    String hash(byte[] data, String algo) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance(algo);
        byte[] d = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
