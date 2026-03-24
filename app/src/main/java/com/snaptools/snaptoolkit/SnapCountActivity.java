package com.snaptools.snaptoolkit;

public class SnapCountActivity extends BaseActivity {

    @Override
    protected void onTextReceived() {
        int chars     = selectedText.length();
        int noSpaces  = selectedText.replaceAll("\\s", "").length();
        int words     = selectedText.trim().isEmpty() ? 0
                        : selectedText.trim().split("\\s+").length;
        int lines     = selectedText.split("\n", -1).length;
        int sentences = countSentences(selectedText);

        String msg =
            "📝  Words:      " + words      + "\n" +
            "🔤  Characters: " + chars      + "\n" +
            "🔡  No spaces:  " + noSpaces   + "\n" +
            "📄  Lines:      " + lines      + "\n" +
            "💬  Sentences:  " + sentences;

        showDialog("🔢 SnapCount", msg,
            Strings.get("close"), null, null);
    }

    int countSentences(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("[.!?]+")
            .matcher(text);
        int count = 0;
        while (m.find()) count++;
        if (count == 0 && !text.trim().isEmpty()) return 1;
        return count;
    }
}
