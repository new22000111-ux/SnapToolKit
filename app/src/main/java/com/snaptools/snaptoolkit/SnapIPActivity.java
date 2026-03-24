package com.snaptools.snaptoolkit;

public class SnapIPActivity extends BaseActivity {

    @Override
    protected void onTextReceived() {
        // Clean input: remove protocol and path
        String host = selectedText
            .replaceAll("https?://", "")
            .split("/")[0]
            .split("\\?")[0]
            .trim();

        if (host.isEmpty()) {
            toast("❌ " + Strings.get("ip_error")); finish(); return;
        }
        toast(Strings.get("looking_up") + " " + host);
        final String finalHost = host;

        new android.os.AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... v) {
                try {
                    // Resolve to IP
                    String ip = java.net.InetAddress.getByName(finalHost).getHostAddress();

                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL("https://ipapi.co/" + ip + "/json/").openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setRequestProperty("User-Agent", "SnapToolKit/1.0");

                    if (c.getResponseCode() != 200) {
                        // Fallback: basic info only
                        return "🌐 Host: " + finalHost + "\n📡 IP: " + ip;
                    }

                    java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close(); c.disconnect();

                    String json = sb.toString();
                    // Check for API error
                    if (json.contains("\"error\": true")) {
                        return "🌐 Host: " + finalHost + "\n📡 IP: " + ip;
                    }

                    return "🌐 Host:     " + finalHost           + "\n" +
                           "📡 IP:       " + ip                  + "\n" +
                           "🏳️ Country:  " + val(json,"country_name") + "\n" +
                           "🏙️ City:     " + val(json,"city")    + "\n" +
                           "🗺️ Region:   " + val(json,"region")  + "\n" +
                           "🏢 ISP:      " + val(json,"org")     + "\n" +
                           "🕐 Timezone: " + val(json,"timezone");

                } catch (java.net.UnknownHostException e) {
                    return "❌ Cannot resolve: " + finalHost;
                } catch (Exception e) {
                    return null;
                }
            }
            protected void onPostExecute(String info) {
                if (info != null) {
                    final String finalInfo = info;
                    showDialog("🌐 SnapIP", info,
                        "📋 " + Strings.get("copied"),
                        () -> copyToClipboard(finalInfo),
                        Strings.get("close"));
                } else {
                    toast("❌ " + Strings.get("ip_error")); finish();
                }
            }
        }.execute();
    }

    String val(String json, String key) {
        String search = "\"" + key + "\": \"";
        int s = json.indexOf(search);
        if (s < 0) return "—";
        s += search.length();
        int e = json.indexOf("\"", s);
        return (e > s) ? json.substring(s, e) : "—";
    }
}
