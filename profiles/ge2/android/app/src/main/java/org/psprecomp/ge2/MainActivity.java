package org.psprecomp.ge2;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_ISO = 1;
    private static final int PICK_EBOOT = 2;

    private Uri isoUri;
    private Uri ebootUri;
    private TextView status;
    private Button importButton;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        showStatus("Native profile: " + NativeBridge.getStatus()
                + "\n\nPilih ISO game dan EBOOT.BIN yang sudah didekripsi. "
                + "File game akan disimpan di penyimpanan privat aplikasi. "
                + "Runtime game belum tersedia pada tahap scaffold ini.");
    }

    private void buildUi() {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad, pad, pad);
        column.setGravity(Gravity.TOP);

        TextView title = new TextView(this);
        title.setText("God Eater 2 — Native ARM64");
        title.setTextSize(23);
        column.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView description = new TextView(this);
        description.setText("Impor ISO dan executable terdekripsi milik Anda. Ini menyiapkan file game untuk runtime AOT; belum menjalankan game.");
        description.setTextSize(15);
        description.setPadding(0, pad / 2, 0, pad);
        column.addView(description);

        Button pickIso = new Button(this);
        pickIso.setText("Pilih ISO / CSO (ISO 9660)");
        pickIso.setOnClickListener(v -> pickFile(PICK_ISO, "application/octet-stream"));
        column.addView(pickIso, matchWrap());

        Button pickEboot = new Button(this);
        pickEboot.setText("Pilih EBOOT.BIN terdekripsi");
        pickEboot.setOnClickListener(v -> pickFile(PICK_EBOOT, "application/octet-stream"));
        column.addView(pickEboot, matchWrap());

        importButton = new Button(this);
        importButton.setText("Impor game");
        importButton.setEnabled(false);
        importButton.setOnClickListener(v -> importGame());
        column.addView(importButton, matchWrap());

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextIsSelectable(true);
        status.setPadding(0, pad, 0, 0);
        column.addView(status, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void pickFile(int request, String mime) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        startActivityForResult(intent, request);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == PICK_ISO) isoUri = data.getData();
        if (request == PICK_EBOOT) ebootUri = data.getData();
        importButton.setEnabled(isoUri != null && ebootUri != null);
        showStatus("ISO: " + (isoUri == null ? "belum dipilih" : isoUri)
                + "\nEBOOT terdekripsi: " + (ebootUri == null ? "belum dipilih" : ebootUri)
                + "\n\nPilih keduanya lalu tekan Impor game.");
    }

    private void importGame() {
        importButton.setEnabled(false);
        final Uri selectedIso = isoUri;
        final Uri selectedEboot = ebootUri;
        showStatus("Mengimpor ISO… jangan tutup aplikasi.");
        worker.execute(() -> {
            try {
                File root = new File(getFilesDir(), "game");
                File staging = new File(getFilesDir(), "game-importing");
                deleteTree(staging);
                if (!staging.mkdirs() && !staging.isDirectory())
                    throw new IllegalStateException("Tidak dapat membuat direktori impor.");
                File isoCopy = new File(getCacheDir(), "selected-game.iso");
                copyUri(selectedIso, isoCopy);
                Iso9660Importer.extract(isoCopy, staging);
                File sysdir = new File(staging, "PSP_GAME/SYSDIR");
                if (!sysdir.isDirectory())
                    throw new IllegalArgumentException("ISO tidak memiliki PSP_GAME/SYSDIR.");
                File decrypted = new File(sysdir, "EBOOT.BIN");
                copyUri(selectedEboot, decrypted);
                if (decrypted.length() < 1024)
                    throw new IllegalArgumentException("EBOOT terdekripsi terlalu kecil.");
                String hash = sha256(decrypted);
                File hashFile = new File(staging, "EBOOT.sha256");
                try (FileOutputStream out = new FileOutputStream(hashFile)) {
                    out.write((hash + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                }
                deleteTree(root);
                if (!staging.renameTo(root))
                    throw new IllegalStateException("Tidak dapat mengaktifkan direktori game hasil impor.");
                isoCopy.delete();
                runOnUiThread(() -> {
                    showStatus("Impor selesai.\nLokasi privat: " + root.getAbsolutePath()
                            + "\nEBOOT SHA-256: " + hash
                            + "\n\nCatatan: file game sudah disiapkan, tetapi runtime AOT/HLE/GE2 belum diimplementasikan sehingga game belum dapat dijalankan.");
                    importButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showStatus("Impor gagal: " + e.getMessage());
                    importButton.setEnabled(true);
                });
            }
        });
    }

    private void copyUri(Uri uri, File dest) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IllegalArgumentException("Tidak dapat membuka file: " + uri);
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    private static void deleteTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        if (file.exists()) file.delete();
    }

    private void showStatus(String text) { status.setText(text); }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
