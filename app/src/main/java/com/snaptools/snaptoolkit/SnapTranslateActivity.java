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

                    int code = c.getResponseCode();
                    if (code != 200) return null;

                    java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close(); c.disconnect();

                    String json = sb.toString();
                    // Parse translatedText from JSON
                    int start = json.indexOf("\"translatedText\":\"");
                    if (start < 0) return null;
                    start += 18;
                    int end = json.indexOf("\"", start);
                    if (end < 0) return null;

                    String translated = json.substring(start, end);
                    // Unescape common sequences
                    translated = translated
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .replace("\\\"", "\"")
                        .replace("\\'", "'")
                        .replace("\\\\", "\\");

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
}
