package com.etherealnymph.courseflow;

import android.content.ContentValues;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class RecorderSheet {

    private RecorderSheet() {}

    public interface Saved {
        void onSaved(Store.Recording recording);
    }

    public static void open(MainActivity a, String suggestedTitle, MainActivity.AttachmentSink sink) {
        open(a, suggestedTitle, sink, null);
    }

    public static void openPublic(MainActivity a, String suggestedTitle, Saved callback) {
        open(a, suggestedTitle, null, callback);
    }

    private static void open(MainActivity a, String suggestedTitle,
                             MainActivity.AttachmentSink sink, Saved callback) {
        Palette p = a.palette();
        String saveDir = a.store().data.recordingDir;
        if (saveDir == null || saveDir.trim().isEmpty()) saveDir = "Music/时屿";
        Sheet sheet = new Sheet(a, p, "录音",
                callback == null ? "麦克风 · 保存为笔记附件" : "麦克风 · 保存到手机 " + saveDir);
        final MediaRecorder[] recorder = {null};
        final File[] file = {null};
        final Store.Attachment[] attachment = {null};
        final boolean[] recording = {false};
        final int[] seconds = {0};
        final Handler handler = new Handler(Looper.getMainLooper());
        final MediaPlayer[] player = {null};

        TextView timer = Ui.text(a, "00:00", 34, p.ink, true);
        timer.setGravity(Gravity.CENTER);
        sheet.add(timer);
        TextView status = Ui.text(a, "点击开始录音", 11.5f, p.faint, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 14));
        sheet.add(status);
        LinearLayout row = Ui.row(a);
        TextView record = Ui.primaryButton(a, "开始录音", p);
        TextView play = Ui.secondaryButton(a, "播放", p);
        play.setEnabled(false);
        play.setAlpha(0.4f);
        row.addView(record, new LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        plp.leftMargin = Ui.dp(a, 8);
        row.addView(play, plp);
        sheet.add(row);

        TextView save = Ui.primaryButton(a, callback != null ? "保存到手机"
                : sink == null ? "保存为语音笔记" : "插入到当前笔记", p);
        save.setEnabled(false);
        save.setAlpha(0.4f);
        sheet.addFooter(save);

        record.setOnClickListener(v -> {
            if (!recording[0]) {
                a.requestRecordPermission(() -> start(a, recorder, file, recording, seconds,
                        timer, status, record, play, save, handler));
            } else {
                stop(a, recorder, file, recording, seconds, timer, status, record, play, save,
                        attachment, handler);
            }
        });
        play.setOnClickListener(v -> {
            if (attachment[0] == null) return;
            try {
                if (player[0] != null) {
                    player[0].release();
                    player[0] = null;
                }
                player[0] = new MediaPlayer();
                player[0].setDataSource(attachment[0].path);
                player[0].prepare();
                player[0].start();
                play.setText("停止");
                player[0].setOnCompletionListener(mp -> {
                    play.setText("播放");
                    mp.release();
                    player[0] = null;
                });
            } catch (Exception e) {
                Ui.toast(a, "播放失败");
            }
        });
        save.setOnClickListener(v -> {
            if (attachment[0] == null) return;
            String title = suggestedTitle == null || suggestedTitle.isEmpty()
                    ? "录音 · " + Dates.monthDay(Dates.today()) : suggestedTitle;
            if (callback != null) {
                long size = file[0] == null ? 0 : file[0].length();
                String publicUri = publish(a, file[0], title);
                if (publicUri == null) {
                    Ui.toast(a, "保存到手机失败，请检查存储空间");
                    return;
                }
                Store.Recording r = new Store.Recording();
                r.id = Store.newId("rec");
                r.name = title;
                r.uri = publicUri;
                r.durationMs = seconds[0] * 1000L;
                r.size = size;
                r.createdAt = System.currentTimeMillis();
                a.store().data.recordings.add(r);
                a.store().save();
                callback.onSaved(r);
                String dir = a.store().data.recordingDir;
                Ui.toast(a, "已保存到手机 " + (dir == null || dir.isEmpty() ? "Music/时屿" : dir));
            } else if (sink != null) {
                sink.onAttachment(attachment[0]);
            } else {
                Store.Note note = new Store.Note();
                note.id = Store.newId("note");
                note.title = title;
                note.body = "[录音](" + "attachment:" + attachment[0].id + ")";
                note.attachments.add(attachment[0]);
                note.createdAt = System.currentTimeMillis();
                note.updatedAt = note.createdAt;
                a.store().data.notes.add(note);
                a.store().save();
                Ui.toast(a, "已保存为语音笔记");
            }
            sheet.dismiss();
        });
        sheet.dialog().setOnDismissListener(d -> {
            if (recording[0]) {
                try {
                    recorder[0].stop();
                } catch (Exception ignored) {
                }
                recorder[0].release();
                recorder[0] = null;
            }
            if (player[0] != null) {
                player[0].release();
                player[0] = null;
            }
        });
        sheet.show();
    }

    private static String publish(MainActivity a, File source, String title) {
        if (source == null || !source.exists()) return null;
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            String fileName = "时屿_" + System.currentTimeMillis() + ".m4a";
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Audio.Media.TITLE, title);
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
            String dir = a.store().data.recordingDir;
            if (dir == null || dir.trim().isEmpty()) dir = Environment.DIRECTORY_MUSIC + "/时屿";
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, dir);
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);
            uri = a.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return null;
            try (FileInputStream in = new FileInputStream(source);
                 OutputStream out = a.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("无法写入手机存储");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            values.clear();
            values.put(MediaStore.Audio.Media.IS_PENDING, 0);
            a.getContentResolver().update(uri, values, null, null);
            //noinspection ResultOfMethodCallIgnored
            source.delete();
            return uri.toString();
        } catch (Exception e) {
            if (uri != null) {
                try {
                    a.getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
            return null;
        }
    }

    private static void start(MainActivity a, MediaRecorder[] recorder, File[] file,
                              boolean[] recording, int[] seconds, TextView timer, TextView status,
                              TextView record, TextView play, TextView save, Handler handler) {
        try {
            File dir = AttachmentStore.dir(a);
            file[0] = new File(dir, "audio_" + System.currentTimeMillis() + ".m4a");
            recorder[0] = new MediaRecorder();
            recorder[0].setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder[0].setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder[0].setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder[0].setAudioSamplingRate(44100);
            recorder[0].setAudioEncodingBitRate(96000);
            recorder[0].setOutputFile(file[0].getAbsolutePath());
            recorder[0].prepare();
            recorder[0].start();
            recording[0] = true;
            seconds[0] = 0;
            record.setText("停止录音");
            play.setEnabled(false);
            play.setAlpha(0.4f);
            save.setEnabled(false);
            save.setAlpha(0.4f);
            status.setText("正在录音…");
            status.setTextColor(a.palette().accent);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (!recording[0]) return;
                    seconds[0]++;
                    timer.setText(String.format(Locale.CHINA, "%02d:%02d",
                            seconds[0] / 60, seconds[0] % 60));
                    handler.postDelayed(this, 1000);
                }
            });
        } catch (Exception e) {
            Ui.toast(a, "无法开始录音：" + e.getMessage());
            if (recorder[0] != null) {
                recorder[0].release();
                recorder[0] = null;
            }
        }
    }

    private static void stop(MainActivity a, MediaRecorder[] recorder, File[] file,
                             boolean[] recording, int[] seconds, TextView timer, TextView status,
                             TextView record, TextView play, TextView save,
                             Store.Attachment[] attachment, Handler handler) {
        recording[0] = false;
        try {
            recorder[0].stop();
            recorder[0].release();
        } catch (Exception e) {
            Ui.toast(a, "录音保存失败");
            return;
        }
        recorder[0] = null;
        record.setText("重新录制");
        status.setText("录音完成 · " + timer.getText());
        status.setTextColor(a.palette().primary);
        if (file[0] != null && file[0].exists() && seconds[0] >= 1) {
            Store.Attachment att = new Store.Attachment();
            att.id = Store.newId("att");
            att.type = "audio";
            att.name = "录音 " + timer.getText();
            att.path = file[0].getAbsolutePath();
            att.mime = "audio/mp4";
            att.size = file[0].length();
            attachment[0] = att;
            play.setEnabled(true);
            play.setAlpha(1f);
            save.setEnabled(true);
            save.setAlpha(1f);
        } else {
            Ui.toast(a, "录音太短了");
        }
    }
}
