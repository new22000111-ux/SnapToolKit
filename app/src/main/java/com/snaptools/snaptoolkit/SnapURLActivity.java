package com.snaptools.snaptoolkit;

public class SnapURLActivity extends BaseActivity {

    @Override
    protected void onTextReceived() {
        if (!selectedText.startsWith("http://") && !selectedText.startsWith("https://")) {
            toast("❌ " + Strings.get("no_valid_url"));
            finish();
            return;
        }
        toast(Strings.get("shortening"));
        new android.os.AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... v) {
                try {
                    String enc = java.net.URLEncoder.encode(selectedText, "UTF-8");
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
}
