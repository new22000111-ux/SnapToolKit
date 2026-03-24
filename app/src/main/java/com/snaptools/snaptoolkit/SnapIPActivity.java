package com.snaptools.snaptoolkit;

public class SnapIPActivity extends BaseActivity {

    @Override
    protected void onTextReceived() {
        String host = extractHost(selectedText);

        if (host.isEmpty()) {
            toast("❌ " + Strings.get("ip_error")); finish(); return;
        }
        toast(Strings.get("looking_up") + " " + host);
        final String finalHost = host;

        new android.os.AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... v) {
                try {
                    String ip = resolveIp(finalHost);

                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL("https://ipwho.is/" + ip).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setRequestProperty("User-Agent", "SnapToolKit/1.0");

                    if (c.getResponseCode() != 200) {
                        c.disconnect();
                        return "🌐 Host: " + finalHost + "\n📡 IP: " + ip;
                    }

                    String json = readAll(c.getInputStream());
                    c.disconnect();
                    org.json.JSONObject root = new org.json.JSONObject(json);
                    if (!root.optBoolean("success", false)) {
                        return "🌐 Host: " + finalHost + "\n📡 IP: " + ip;
                    }

                    org.json.JSONObject conn = root.optJSONObject("connection");
                    org.json.JSONObject tz = root.optJSONObject("timezone");
                    String country = safe(root.optString("country", ""));
                    String city = safe(root.optString("city", ""));
                    String region = safe(root.optString("region", ""));
                    String isp = conn != null ? safe(conn.optString("isp", "")) : "—";
                    String timezone = tz != null ? safe(tz.optString("id", "")) : "—";

                    return "🌐 Host:     " + finalHost           + "\n" +
                           "📡 IP:       " + ip                  + "\n" +
                           "🏳️ Country:  " + country             + "\n" +
                           "🏙️ City:     " + city                + "\n" +
                           "🗺️ Region:   " + region              + "\n" +
                           "🏢 ISP:      " + isp                 + "\n" +
                           "🕐 Timezone: " + timezone;

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

    String extractHost(String input) {
        String raw = input.trim();
        if (raw.isEmpty()) return "";
        try {
            String normalized = raw.contains("://") ? raw : "http://" + raw;
            java.net.URI uri = new java.net.URI(normalized);
            String host = uri.getHost();
            if (host == null) return raw.split("/")[0].trim();
            return host.trim();
        } catch (Exception e) {
            return raw.split("/")[0].trim();
        }
    }

    String resolveIp(String hostOrIp) throws Exception {
        try {
            java.net.InetAddress literal = java.net.InetAddress.getByName(hostOrIp);
            if (literal.getHostAddress() != null && hostOrIp.equalsIgnoreCase(literal.getHostAddress())) {
                return literal.getHostAddress();
            }
        } catch (Exception ignored) {}

        java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(hostOrIp);
        for (java.net.InetAddress addr : addresses) {
            if (!addr.isLoopbackAddress()) return addr.getHostAddress();
        }
        if (addresses.length > 0) return addresses[0].getHostAddress();
        throw new java.net.UnknownHostException(hostOrIp);
    }

    String readAll(java.io.InputStream is) throws Exception {
        java.io.BufferedReader r = new java.io.BufferedReader(
            new java.io.InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    String safe(String s) {
        return s == null || s.trim().isEmpty() ? "—" : s.trim();
    }
}
