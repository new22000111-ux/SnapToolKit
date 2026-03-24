package com.snaptools.snaptoolkit;

public class SnapTranslateActivity extends BaseActivity {

    @Override
    protected void onTextReceived() {
        String targetLang = java.util.Locale.getDefault().getLanguage();
        toast(Strings.get("translating"));

        new android.os.AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... v) {
                try {
                    String enc = java.net.URLEncoder.encode(selectedText, "UTF-8");
                    String apiUrl = "https://api.mymemory.translated.net/get?q=" + enc
                        + "&langpair=autodetect|" + targetLang;
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL(apiUrl).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setRequestProperty("User-Agent", "SnapToolKit/1.0");

                    int code = c.getResponseCode();
                    if (code != 200) {
                        c.disconnect();
                        return null;
                    }

                    String json = readAll(c.getInputStream());
                    c.disconnect();

                    org.json.JSONObject root = new org.json.JSONObject(json);
                    org.json.JSONObject data = root.optJSONObject("responseData");
                    if (data == null) return null;

                    String translated = data.optString("translatedText", "").trim();
                    if (translated.isEmpty()) return null;
                    translated = decodeEntities(translated);

                    // MyMemory returns "MYMEMORY WARNING" on quota exceed
                    if (translated.contains("MYMEMORY WARNING")) return null;
                    return translated.isEmpty() ? null : translated;

                } catch (Exception e) { return null; }
            }
            protected void onPostExecute(String result) {
                if (result != null) returnText(result);
                else { toast("❌ " + Strings.get("translate_error")); finish(); }
            }
        }.execute();
    }

    private String readAll(java.io.InputStream is) throws Exception {
        java.io.BufferedReader r = new java.io.BufferedReader(
            new java.io.InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    private String decodeEntities(String text) {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            return android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        }
        return android.text.Html.fromHtml(text).toString();
    }
}
