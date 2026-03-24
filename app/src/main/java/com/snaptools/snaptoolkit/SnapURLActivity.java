package com.snaptools.snaptoolkit;

public class SnapURLActivity extends BaseActivity {

    @Override
    protected void onTextReceived() {
        String normalized = normalizeUrl(selectedText);
        if (normalized == null) {
            toast("❌ " + Strings.get("no_valid_url"));
            finish();
            return;
        }
        toast(Strings.get("shortening"));
        final String finalUrl = normalized;
        new android.os.AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... v) {
                try {
                    String enc = java.net.URLEncoder.encode(finalUrl, "UTF-8");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL("https://is.gd/create.php?format=simple&url=" + enc)
                            .openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(c.getInputStream()));
                    String res = r.readLine();
                    r.close(); c.disconnect();
                    // is.gd returns error message if URL is invalid
                    if (res != null && res.startsWith("http")) return res;
                    return null;
                } catch (Exception e) { return null; }
            }
            protected void onPostExecute(String s) {
                if (s != null) returnText(s);
                else { toast("❌ " + Strings.get("shorten_error")); finish(); }
            }
        }.execute();
    }

    String normalizeUrl(String input) {
        String raw = input.trim();
        if (raw.isEmpty()) return null;
        if (!raw.matches("(?i)^https?://.*$")) raw = "https://" + raw;
        try {
            java.net.URI uri = new java.net.URI(raw);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.trim().isEmpty()) return null;
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) return null;
            return uri.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
