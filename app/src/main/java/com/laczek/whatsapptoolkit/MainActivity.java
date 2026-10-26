package com.laczek.whatsapptoolkit;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
    private File latestTrim;
    private Uri importedAudio;
    private TextView status;
    private TextView timer;
    private TextView volumeLabel;
    private TextView playbackTime;
    private SeekBar playbackSeek;
    private AudioLevelView liveWaveform;
    private TextView levelStatus;
    private boolean sawMicSignal;
    private LinearLayout libraryList;
    private boolean userSeeking;
    private EditText trimStart;
    private EditText trimEnd;
    private long recordingStarted;
    private float previewVolume = 1.0f;
    private final android.os.Handler handler = new android.os.Handler();
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (recorder != null) {
                long seconds = (System.currentTimeMillis() - recordingStarted) / 1000;
                timer.setText(String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60));
                int amplitude = 0;
                try { amplitude = recorder.getMaxAmplitude(); } catch (Exception ignored) {}
                if (liveWaveform != null) liveWaveform.addLevel(amplitude);
                if (amplitude > 700) sawMicSignal = true;
                if (levelStatus != null) {
                    if (amplitude > 700) {
                        levelStatus.setText("MIC INPUT DETECTED  •  Peak " + amplitude);
                        levelStatus.setTextColor(0xFF8FE3A1);
                    } else {
                        levelStatus.setText("Listening for microphone input… keep speaking");
                        levelStatus.setTextColor(0xFFFFC078);
                    }
                }
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF08090B);
        getWindow().setNavigationBarColor(0xFF08090B);
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
        intro.setText("Private audio tools. Your files stay on your device unless you export or share them.");
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

        liveWaveform = new AudioLevelView(this);
        LinearLayout.LayoutParams waveLp = new LinearLayout.LayoutParams(-1, dp(76));
        waveLp.bottomMargin = dp(8);
        root.addView(liveWaveform, waveLp);
        levelStatus = new TextView(this);
        levelStatus.setText("LIVE MIC MONITOR  •  Start recording to test input");
        levelStatus.setTextColor(0xFF9CA3AF);
        levelStatus.setTextSize(12);
        levelStatus.setPadding(dp(2), dp(4), dp(2), dp(10));
        root.addView(levelStatus);

        addButton(root, "●  Start recording", v -> startRecording());
        addButton(root, "■  Stop recording", v -> stopRecording());
        addButton(root, "▶  Play latest recording", v -> playFile(latestRecording));
        addButton(root, "⇧  Export / share recording", v -> exportLatest());
        addButton(root, "WhatsApp  •  Send recording", v -> shareLatestToWhatsApp());
        addButton(root, "✕  Delete latest recording", v -> deleteLatestRecording());

        cardTitle(root, "02  •  YOUR AUDIO LIBRARY");
        TextView libraryHelp = new TextView(this);
        libraryHelp.setText("Recordings saved by this app stay on your phone. Select any item to play or share it.");
        libraryHelp.setTextColor(0xFFB7BBC4);
        libraryHelp.setTextSize(13);
        libraryHelp.setPadding(0, 0, 0, dp(8));
        root.addView(libraryHelp);
        libraryList = new LinearLayout(this);
        libraryList.setOrientation(LinearLayout.VERTICAL);
        root.addView(libraryList);
        refreshLibrary();

        cardTitle(root, "03  •  MUSIC STUDIO");
        addButton(root, "＋  Import audio", v -> pickAudio());
        addButton(root, "▶  Preview imported audio", v -> playImported());
        volumeLabel = new TextView(this);
        volumeLabel.setText("Preview volume: 100%");
        volumeLabel.setTextColor(0xFFD7D9DE);
        volumeLabel.setPadding(0, dp(8), 0, dp(2));
        root.addView(volumeLabel);
        SeekBar volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(100);
        volume.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFF8A3D));
        volume.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFF8A3D));
        root.addView(volume);
        playbackTime = new TextView(this);
        playbackTime.setText("00:00 / 00:00");
        playbackTime.setTextColor(0xFFD7D9DE);
        playbackTime.setTextSize(12);
        playbackTime.setPadding(0, dp(8), 0, dp(2));
        root.addView(playbackTime);
        playbackSeek = new SeekBar(this);
        playbackSeek.setMax(1000);
        playbackSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFF8A3D));
        playbackSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFF8A3D));
        root.addView(playbackSeek);
        playbackSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    userSeeking = true;
                    int duration = safeDuration();
                    playbackTime.setText(formatTime((int) (duration * progress / 1000f)) + " / " + formatTime(duration));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (player != null) {
                    try { player.seekTo((int) (safeDuration() * bar.getProgress() / 1000f)); } catch (Exception ignored) {}
                }
                userSeeking = false;
            }
        });
        addButton(root, "Ⅱ / ▶  Pause or resume playback", v -> togglePlayback());
        addButton(root, "■  Stop playback", v -> stopPlayer());

        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                previewVolume = progress / 100f;
                volumeLabel.setText("Preview volume: " + progress + "%");
                applyVolume();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        TextView trimHelp = new TextView(this);
        trimHelp.setText("Trim imported audio (seconds). For compatible audio formats, this creates a new M4A clip without re-encoding.");
        trimHelp.setTextColor(0xFFB7BBC4);
        trimHelp.setTextSize(13);
        trimHelp.setPadding(0, dp(12), 0, dp(6));
        root.addView(trimHelp);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        trimStart = numberField("Start sec");
        trimEnd = numberField("End sec");
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(52), 1);
        half.rightMargin = dp(8);
        fields.addView(trimStart, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(52), 1);
        fields.addView(trimEnd, half2);
        root.addView(fields);
        addButton(root, "✂  Create trimmed M4A clip", v -> trimImported());
        addButton(root, "♫  Share imported audio", v -> shareImported());
        addButton(root, "WhatsApp  •  Send song / audio", v -> shareImportedToWhatsApp());
        addButton(root, "⇧  Share trimmed clip", v -> shareTrimmed());
        addButton(root, "WhatsApp  •  Send trimmed clip", v -> shareTrimmedToWhatsApp());

        cardTitle(root, "04  •  CALL AUDIO DIAGNOSTICS");
        TextView warning = new TextView(this);
        warning.setText("This recorder uses the microphone. Android and WhatsApp may block remote call audio, so the other caller may not be recorded. This app does not bypass those protections. Test locally before relying on any recording, and get everyone’s consent.");
        warning.setTextColor(0xFFE2C7B7);
        warning.setTextSize(14);
        warning.setPadding(dp(14), dp(14), dp(14), dp(14));
        warning.setBackgroundColor(0xFF211711);
        root.addView(warning);

        status = new TextView(this);
        status.setText("Ready • no audio is uploaded automatically");
        status.setTextColor(0xFF9CA3AF);
        status.setTextSize(13);
        status.setPadding(0, dp(20), 0, dp(4));
        root.addView(status);
        setContentView(scroll);
    }

    private EditText numberField(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setHintTextColor(0xFF8A8F98);
        e.setTextColor(0xFFFFFFFF);
        e.setInputType(2);
        e.setPadding(dp(10), 0, dp(10), 0);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF8A3D));
        return e;
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
            if (liveWaveform != null) liveWaveform.clearLevels();
            sawMicSignal = false;
            if (levelStatus != null) {
                levelStatus.setText("Listening for microphone input…");
                levelStatus.setTextColor(0xFFFFC078);
            }
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
            if (levelStatus != null) {
                if (sawMicSignal) {
                    levelStatus.setText("MIC SIGNAL DETECTED  •  This confirms microphone input only, not the other caller.");
                    levelStatus.setTextColor(0xFF8FE3A1);
                } else {
                    levelStatus.setText("LOW OR SILENT MIC INPUT DETECTED  •  Check permission and microphone access.");
                    levelStatus.setTextColor(0xFFFF8A3D);
                }
            }
            refreshLibrary();
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

    private void playLatest() { playFile(latestRecording); }

    private int safeDuration() {
        try { return player == null ? 0 : player.getDuration(); }
        catch (Exception ignored) { return 0; }
    }

    private String formatTime(int milliseconds) {
        int seconds = Math.max(0, milliseconds / 1000);
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void togglePlayback() {
        if (player == null) {
            if (importedAudio != null) playImported();
            else playFile(latestRecording);
            return;
        }
        try {
            if (player.isPlaying()) {
                player.pause();
                status.setText("Playback paused");
            } else {
                player.start();
                status.setText("Playback resumed");
            }
        } catch (Exception e) { say("Playback control failed."); }
    }

    private void refreshLibrary() {
        if (libraryList == null) return;
        libraryList.removeAllViews();
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Recordings");
        File[] files = dir.listFiles((folder, name) -> name.toLowerCase(Locale.ROOT).endsWith(".m4a"));
        if (files == null || files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("No recordings yet. Record something and it will appear here.");
            empty.setTextColor(0xFF8A8F98);
            empty.setTextSize(13);
            empty.setPadding(0, dp(8), 0, dp(12));
            libraryList.addView(empty);
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        int count = Math.min(files.length, 30);
        for (int i = 0; i < count; i++) {
            File file = files[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            item.setBackgroundColor(0xFF15171B);
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-1, -2);
            itemLp.bottomMargin = dp(6);
            libraryList.addView(item, itemLp);
            TextView name = new TextView(this);
            name.setText(file.getName());
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(14);
            name.setTypeface(null, 1);
            item.addView(name);
            TextView meta = new TextView(this);
            meta.setText((file.length() / 1024) + " KB  •  " + new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date(file.lastModified())));
            meta.setTextColor(0xFF9CA3AF);
            meta.setTextSize(12);
            item.addView(meta);
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            item.addView(actions);
            Button play = smallButton("Play");
            play.setOnClickListener(v -> { latestRecording = file; playFile(file); });
            actions.addView(play, new LinearLayout.LayoutParams(0, dp(42), 1));
            Button share = smallButton("Share");
            share.setOnClickListener(v -> { latestRecording = file; exportLatest(); });
            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(0, dp(42), 1);
            actionLp.leftMargin = dp(6);
            actions.addView(share, actionLp);
            Button delete = smallButton("Delete");
            delete.setOnClickListener(v -> {
                if (player != null && file.equals(latestRecording)) stopPlayer();
                if (file.delete()) {
                    if (file.equals(latestRecording)) latestRecording = null;
                    refreshLibrary();
                    status.setText("Deleted " + file.getName());
                } else say("Could not delete this recording.");
            });
            LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, dp(42), 1);
            deleteLp.leftMargin = dp(6);
            actions.addView(delete, deleteLp);
        }
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF30343B));
        return b;
    }

    private void deleteLatestRecording() {
        if (latestRecording == null || !latestRecording.exists()) {
            say("No latest recording to delete.");
            return;
        }
        File target = latestRecording;
        if (player != null) stopPlayer();
        if (target.delete()) {
            latestRecording = null;
            status.setText("Recording deleted");
            refreshLibrary();
            say("Recording deleted.");
        } else say("Could not delete recording.");
    }

    private void playImported() {
        if (importedAudio == null) { say("Import an audio file first."); return; }
        try {
            stopPlayer();
            player = new MediaPlayer();
            player.setDataSource(this, importedAudio);
            player.setOnPreparedListener(mp -> { applyVolume(); mp.start(); updatePlaybackUi(); status.setText("Playing imported audio preview"); });
            player.setOnCompletionListener(mp -> { mp.release(); if (player == mp) player = null; });
            player.setOnErrorListener((mp, what, extra) -> { status.setText("This audio format could not be previewed."); stopPlayer(); return true; });
            player.prepareAsync();
            status.setText("Preparing audio preview…");
        } catch (Exception e) { stopPlayer(); say("Could not preview this audio file."); }
    }

    private void playFile(File file) {
        if (file == null || !file.exists() || file.length() == 0) { say("No saved recording to play."); return; }
        try {
            stopPlayer();
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> { applyVolume(); mp.start(); updatePlaybackUi(); status.setText("Playing " + file.getName()); });
            player.setOnCompletionListener(mp -> { mp.release(); if (player == mp) player = null; });
            player.setOnErrorListener((mp, what, extra) -> { status.setText("Audio playback failed."); stopPlayer(); return true; });
            player.prepareAsync();
        } catch (Exception e) { stopPlayer(); say("Could not play audio."); }
    }

    private void updatePlaybackUi() {
        handler.post(new Runnable() {
            @Override public void run() {
                if (player == null) {
                    if (playbackSeek != null) playbackSeek.setProgress(0);
                    if (playbackTime != null) playbackTime.setText("00:00 / 00:00");
                    return;
                }
                try {
                    int duration = player.getDuration();
                    int position = player.getCurrentPosition();
                    if (!userSeeking && playbackSeek != null && duration > 0) playbackSeek.setProgress((int) (position * 1000L / duration));
                    if (playbackTime != null) playbackTime.setText(formatTime(position) + " / " + formatTime(duration));
                    if (player.isPlaying()) handler.postDelayed(this, 250);
                } catch (Exception ignored) {}
            }
        });
    }

    private void applyVolume() {
        if (player != null) {
            try { player.setVolume(previewVolume, previewVolume); } catch (Exception ignored) {}
        }
    }

    private void stopPlayer() {
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
            if (playbackSeek != null) playbackSeek.setProgress(0);
            if (playbackTime != null) playbackTime.setText("00:00 / 00:00");
        }
    }

    private void exportLatest() {
        if (latestRecording == null || !latestRecording.exists() || latestRecording.length() == 0) {
            say("No saved recording to export."); return;
        }
        exportFile(latestRecording, "Share recording");
    }

    private void exportFile(File file, String chooserTitle) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, file.getName());
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/LaCzek Audio Lab");
            Uri uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Could not create export");
            try (FileInputStream in = new FileInputStream(file);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Could not write export");
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
            }
            shareUri(uri, "audio/mp4", chooserTitle);
            status.setText("Exported to Music/LaCzek Audio Lab");
        } catch (Exception e) { say("Export failed: " + e.getMessage()); }
    }

    private void pickAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("audio/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_AUDIO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_AUDIO && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            importedAudio = data.getData();
            try { getContentResolver().takePersistableUriPermission(importedAudio, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            String name = importedAudio.getLastPathSegment();
            status.setText("Imported audio selected • " + (name == null ? "audio file" : name));
            say("Audio selected.");
        }
    }

    private void trimImported() {
        if (importedAudio == null) { say("Import an audio file first."); return; }
        int startSec, endSec;
        try {
            startSec = Integer.parseInt(trimStart.getText().toString().trim());
            endSec = Integer.parseInt(trimEnd.getText().toString().trim());
        } catch (Exception e) { say("Enter start and end times in seconds."); return; }
        if (!AudioTrimRange.isValid(startSec, endSec)) { say("End time must be greater than start time."); return; }
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean muxerStarted = false;
        try {
            extractor.setDataSource(this, importedAudio, null);
            int audioTrack = -1;
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { audioTrack = i; inputFormat = f; break; }
            }
            if (audioTrack < 0 || inputFormat == null) throw new IllegalArgumentException("No audio track found.");
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null || !mime.equals("audio/mp4a-latm")) {
                throw new IllegalArgumentException("Fast trimming currently supports AAC audio. Import an AAC/M4A file.");
            }
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Edits");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create edit folder.");
            latestTrim = new File(dir, "LaCzek-Clip-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".m4a");
            extractor.selectTrack(audioTrack);
            long startUs = AudioTrimRange.startMicros(startSec);
            long endUs = AudioTrimRange.endMicros(endSec);
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            muxer = new MediaMuxer(latestTrim.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outputTrack = muxer.addTrack(inputFormat);
            muxer.start();
            muxerStarted = true;
            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(1024 * 1024);
            long firstPts = -1;
            while (true) {
                buffer.clear();
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                long pts = extractor.getSampleTime();
                if (pts < startUs) { extractor.advance(); continue; }
                if (pts >= endUs) break;
                if (firstPts < 0) firstPts = pts;
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = pts - firstPts;
                info.flags = extractor.getSampleFlags();
                muxer.writeSampleData(outputTrack, buffer, info);
                extractor.advance();
            }
            muxer.stop();
            muxerStarted = false;
            muxer.release();
            muxer = null;
            extractor.release();
            if (!latestTrim.exists() || latestTrim.length() < 1024) {
                latestTrim.delete();
                latestTrim = null;
                throw new IllegalArgumentException("No usable audio samples in that range. Check the times and file format.");
            }
            status.setText("Trimmed clip saved locally • " + latestTrim.getName());
            say("Trimmed M4A clip created.");
        } catch (Exception e) {
            if (muxer != null) {
                try { if (muxerStarted) muxer.stop(); } catch (Exception ignored) {}
                try { muxer.release(); } catch (Exception ignored) {}
            }
            try { extractor.release(); } catch (Exception ignored) {}
            if (latestTrim != null && latestTrim.exists() && latestTrim.length() < 1024) latestTrim.delete();
            status.setText("Trim failed: " + e.getMessage());
            say(e.getMessage() == null ? "Could not trim this file." : e.getMessage());
        }
    }

    private void shareTrimmed() {
        if (latestTrim == null || !latestTrim.exists() || latestTrim.length() == 0) { say("Create a trimmed clip first."); return; }
        exportFile(latestTrim, "Share trimmed audio");
    }

    private void shareLatestToWhatsApp() {
        if (latestRecording == null || !latestRecording.exists() || latestRecording.length() == 0) {
            say("Record audio first."); return;
        }
        exportFileToWhatsApp(latestRecording);
    }

    private void shareTrimmedToWhatsApp() {
        if (latestTrim == null || !latestTrim.exists() || latestTrim.length() == 0) {
            say("Create a trimmed clip first."); return;
        }
        exportFileToWhatsApp(latestTrim);
    }

    private void shareImportedToWhatsApp() {
        if (importedAudio == null) { say("Import a song or audio file first."); return; }
        String mime = getContentResolver().getType(importedAudio);
        shareUriToWhatsApp(importedAudio, mime == null ? "audio/*" : mime);
    }

    private void exportFileToWhatsApp(File file) {
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, file.getName());
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/LaCzek Audio Lab");
            uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Could not prepare audio for sharing");
            try (FileInputStream in = new FileInputStream(file);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Could not write shared audio");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
            }
            shareUriToWhatsApp(uri, "audio/mp4");
            status.setText("Audio prepared for WhatsApp sharing. WhatsApp decides how it is sent.");
        } catch (Exception e) {
            if (uri != null) {
                try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
            }
            say("Could not prepare audio for WhatsApp: " + e.getMessage());
        }
    }

    private void shareUriToWhatsApp(Uri uri, String mime) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mime);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            share.setPackage("com.whatsapp");
            startActivity(share);
        } catch (Exception whatsappUnavailable) {
            share.setPackage(null);
            try { startActivity(Intent.createChooser(share, "Send audio to WhatsApp or another app")); }
            catch (Exception noApps) { say("No compatible sharing app found."); }
        }
    }

    private void shareImported() {
        if (importedAudio == null) { say("Import an audio file first."); return; }
        String mime = getContentResolver().getType(importedAudio);
        shareUri(importedAudio, mime == null ? "audio/*" : mime, "Share audio");
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
        stopPlayer();
        super.onDestroy();
    }
}
