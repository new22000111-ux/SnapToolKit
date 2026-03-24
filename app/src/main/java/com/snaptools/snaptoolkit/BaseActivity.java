package com.snaptools.snaptoolkit;

public abstract class BaseActivity extends android.app.Activity {

    protected String selectedText;
    protected boolean isReadOnly;

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        CharSequence sel = getIntent().getCharSequenceExtra(
            android.content.Intent.EXTRA_PROCESS_TEXT);
        isReadOnly = getIntent().getBooleanExtra(
            android.content.Intent.EXTRA_PROCESS_TEXT_READONLY, false);

        if (sel == null || sel.toString().trim().isEmpty()) {
            toast(Strings.get("no_text"));
            finish();
            return;
        }
        selectedText = sel.toString().trim();
        onTextReceived();
    }

    protected abstract void onTextReceived();

    protected void returnText(String result) {
        if (!isReadOnly) {
            android.content.Intent i = new android.content.Intent();
            i.putExtra(android.content.Intent.EXTRA_PROCESS_TEXT, result);
            setResult(RESULT_OK, i);
        } else {
            copyToClipboard(result);
            toast(Strings.get("copied") + ": " + result);
        }
        finish();
    }

    protected void copyToClipboard(String text) {
        android.content.ClipboardManager cm =
            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("snap", text));
    }

    protected void toast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
    }

    protected void showDialog(String title, String message,
                               String posBtn, Runnable onPos,
                               String negBtn) {
        runOnUiThread(() -> {
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
            b.setTitle(title);
            b.setMessage(message);
            if (posBtn != null)
                b.setPositiveButton(posBtn, (d, w) -> { if (onPos != null) onPos.run(); d.dismiss(); });
            if (negBtn != null)
                b.setNegativeButton(negBtn, null);
            b.setOnDismissListener(d -> finish());
            b.show();
        });
    }
}
