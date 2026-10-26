package com.laczek.whatsapptoolkit;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 41;
    private static final int REQ_AUDIO = 42;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private File latestRecording;
    private Uri importedAudio;
    private TextView status;
    private TextView timer;
    private long recordingStarted;
    private android.os.Handler handler = new android.os.Handler();
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (recorder != null) {
                long seconds = (System.currentTimeMillis() - recordingStarted) / 1000;
                timer.setText(String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60));
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF08090B);
        getWindow().setNavigationBarColor(0xFF08090B);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF08090B);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(28));
        scroll.addView(root);

        TextView brand = new TextView(this);
        brand.setText("LaCzek\nAUDIO LAB");
        brand.setTextColor(0xFFFF8A3D);
        brand.setTextSize(29);
        brand.setTypeface(null, 1);
        root.addView(brand);

        TextView intro = new TextView(this);
        intro.setText("Private audio tools. Recorded locally on your device.");
        intro.setTextColor(0xFFB7BBC4);
        intro.setTextSize(14);
        intro.setPadding(0, dp(8), 0, dp(22));
        root.addView(intro);

        cardTitle(root, "01  •  MICROPHONE RECORDER");
        timer = new TextView(this);
        timer.setText("00:00");
        timer.setTextColor(0xFFFFFFFF);
        timer.setTextSize(36);
        timer.setGravity(Gravity.CENTER);
        timer.setPadding(0, dp(8), 0, dp(8));
        root.addView(timer);
        addButton(root, "●  Start recording", v -> startRecording());
        addButton(root, "■  Stop recording", v -> stopRecording());
        addButton(root, "▶  Play latest recording", v -> playLatest());
        addButton(root, "⇧  Export / share recording", v -> exportLatest());

        cardTitle(root, "02  •  MUSIC STUDIO");
        addButton(root, "＋  Import audio", v -> pickAudio());
        addButton(root, "♫  Share imported audio", v -> shareImported());

        cardTitle(root, "03  •  CALL AUDIO DIAGNOSTICS");
        TextView warning = new TextView(this);
        warning.setText("Android may block remote WhatsApp-call audio. This app does not bypass those protections. Depending on your phone and Android version, a recording may contain only your microphone or may be silent. Get everyone’s consent before recording.");
        warning.setTextColor(0xFFE2C7B7);
        warning.setTextSize(14);
        warning.setPadding(dp(14), dp(14), dp(14), dp(14));
        warning.setBackgroundColor(0xFF211711);
        root.addView(warning);

        status = new TextView(this);
        status.setText("Ready • files stay on this device until you share them");
        status.setTextColor(0xFF9CA3AF);
        status.setTextSize(13);
        status.setPadding(0, dp(20), 0, dp(4));
        root.addView(status);
        setContentView(scroll);
    }

    private void cardTitle(LinearLayout root, String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(16);
        t.setTypeface(null, 1);
        t.setPadding(0, dp(22), 0, dp(10));
        root.addView(t);
    }

    private void addButton(LinearLayout root, String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF24272D));
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.bottomMargin = dp(8);
        root.addView(b, lp);
        b.setOnClickListener(listener);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (recorder != null) { say("Recording is already running."); return; }
        try {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Recordings");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create recordings folder");
            latestRecording = new File(dir, "LaCzek-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".m4a");
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(latestRecording.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recordingStarted = System.currentTimeMillis();
            handler.post(ticker);
            status.setText("Recording microphone audio…");
            say("Recording started.");
        } catch (Exception e) {
            releaseRecorder();
            if (latestRecording != null) latestRecording.delete();
            latestRecording = null;
            status.setText("Could not start recording: " + e.getMessage());
            say("Could not start recording.");
        }
    }

    private void stopRecording() {
        if (recorder == null) { say("No active recording."); return; }
        try {
            recorder.stop();
            status.setText(latestRecording != null && latestRecording.length() > 1024
                    ? "Saved locally • " + latestRecording.getName() + " • " + (latestRecording.length() / 1024) + " KB"
                    : "Recording stopped, but the file may be empty.");
            say("Recording saved.");
        } catch (RuntimeException e) {
            if (latestRecording != null) latestRecording.delete();
            latestRecording = null;
            status.setText("Recording was too short or failed. Try recording for at least one second.");
            say("Recording failed or was too short.");
        } finally {
            handler.removeCallbacks(ticker);
            timer.setText("00:00");
            releaseRecorder();
        }
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.reset(); } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
    }

    private void playLatest() {
        if (latestRecording == null || !latestRecording.exists() || latestRecording.length() == 0) {
            say("Record something first.");
            return;
        }
        try {
            if (player != null) { player.release(); player = null; }
            player = new MediaPlayer();
            player.setDataSource(latestRecording.getAbsolutePath());
            player.setOnCompletionListener(mp -> { mp.release(); if (player == mp) player = null; });
            player.prepare();
            player.start();
            status.setText("Playing " + latestRecording.getName());
        } catch (Exception e) { say("Could not play recording."); }
    }

    private void exportLatest() {
        if (latestRecording == null || !latestRecording.exists() || latestRecording.length() == 0) {
            say("No saved recording to export.");
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, latestRecording.getName());
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/LaCzek Audio Lab");
            Uri uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Could not create export");
            try (FileInputStream in = new FileInputStream(latestRecording);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Could not write export");
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
            }
            shareUri(uri, "audio/mp4", "Share recording");
            status.setText("Exported to Music/LaCzek Audio Lab");
        } catch (Exception e) { say("Export failed: " + e.getMessage()); }
    }

    private void pickAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("audio/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQ_AUDIO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_AUDIO && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            importedAudio = data.getData();
            try { getContentResolver().takePersistableUriPermission(importedAudio, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            String name = importedAudio.getLastPathSegment();
            status.setText("Imported audio selected • " + (name == null ? "audio file" : name));
            say("Audio selected. You can share it to WhatsApp.");
        }
    }

    private void shareImported() {
        if (importedAudio == null) { say("Import an audio file first."); return; }
        shareUri(importedAudio, getContentResolver().getType(importedAudio) == null ? "audio/*" : getContentResolver().getType(importedAudio), "Share audio");
    }

    private void shareUri(Uri uri, String mime, String title) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mime);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(share, title)); }
        catch (Exception e) { say("No compatible sharing app found."); }
    }

    private void say(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MIC && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startRecording();
        else if (requestCode == REQ_MIC) say("Microphone permission is required to record.");
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) {} releaseRecorder(); }
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
