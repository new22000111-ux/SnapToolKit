package com.snaptools.snaptoolkit;

public class SnapCountActivity extends BaseActivity {

    @Override
    protected void onTextReceived() {
        int chars     = selectedText.length();
        int noSpaces  = selectedText.replaceAll("\\s", "").length();
        int words     = selectedText.trim().isEmpty() ? 0
                        : selectedText.trim().split("\\s+").length;
        int lines     = selectedText.split("\n", -1).length;
        int sentences = selectedText.split("[.!?]+", -1).length - 1;

        String msg =
            "📝  Words:      " + words      + "\n" +
            "🔤  Characters: " + chars      + "\n" +
            "🔡  No spaces:  " + noSpaces   + "\n" +
            "📄  Lines:      " + lines      + "\n" +
            "💬  Sentences:  " + sentences;

        showDialog("🔢 SnapCount", msg,
            Strings.get("close"), null, null);
    }
}
