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
                    String primary = translateWithMyMemory(enc, targetLang);
                    if (primary != null) return primary;
                    return translateWithGoogle(enc, targetLang);

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

    private String translateWithMyMemory(String encoded, String targetLang) {
        java.net.HttpURLConnection c = null;
        try {
            String apiUrl = "https://api.mymemory.translated.net/get?q=" + encoded
                + "&langpair=autodetect|" + targetLang;
            c = (java.net.HttpURLConnection) new java.net.URL(apiUrl).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "SnapToolKit/1.0");
            if (c.getResponseCode() != 200) return null;

            String json = readAll(c.getInputStream());
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONObject data = root.optJSONObject("responseData");
            if (data == null) return null;

            String translated = data.optString("translatedText", "").trim();
            if (translated.isEmpty()) return null;
            translated = decodeEntities(translated);
            if (translated.contains("MYMEMORY WARNING")) return null;
            return translated.isEmpty() ? null : translated;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String translateWithGoogle(String encoded, String targetLang) {
        java.net.HttpURLConnection c = null;
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx"
                + "&sl=auto&tl=" + targetLang + "&dt=t&q=" + encoded;
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "SnapToolKit/1.0");
            if (c.getResponseCode() != 200) return null;

            org.json.JSONArray root = new org.json.JSONArray(readAll(c.getInputStream()));
            org.json.JSONArray sentences = root.optJSONArray(0);
            if (sentences == null) return null;
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < sentences.length(); i++) {
                org.json.JSONArray part = sentences.optJSONArray(i);
                if (part == null) continue;
                String token = part.optString(0, "");
                if (!token.isEmpty()) out.append(token);
            }
            String translated = out.toString().trim();
            return translated.isEmpty() ? null : translated;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String decodeEntities(String text) {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            return android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        }
        return android.text.Html.fromHtml(text).toString();
    }
}
