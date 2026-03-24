package com.snaptools.snaptoolkit;

public class SnapCaseActivity extends BaseActivity {

    @Override
    protected void onTextReceived() {
        String[] options = {
            Strings.get("uppercase"),
            Strings.get("lowercase"),
            Strings.get("titlecase"),
            Strings.get("sentencecase")
        };
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("🔠 SnapCase");
        b.setItems(options, (d, which) -> {
            String result;
            switch (which) {
                case 0:  result = selectedText.toUpperCase(java.util.Locale.getDefault()); break;
                case 1:  result = selectedText.toLowerCase(java.util.Locale.getDefault()); break;
                case 2:  result = toTitleCase(selectedText); break;
                default: result = toSentenceCase(selectedText); break;
            }
            returnText(result);
        });
        b.setOnCancelListener(d -> finish());
        b.show();
    }

    String toTitleCase(String s) {
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c)) { cap = true; sb.append(c); }
            else if (cap) { sb.append(Character.toUpperCase(c)); cap = false; }
            else sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    String toSentenceCase(String s) {
        if (s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (c == '.' || c == '!' || c == '?') { cap = true; sb.append(c); }
            else if (cap && !Character.isWhitespace(c)) { sb.append(Character.toUpperCase(c)); cap = false; }
            else sb.append(c);
        }
        return sb.toString();
    }
}
