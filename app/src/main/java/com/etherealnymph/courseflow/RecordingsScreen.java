package com.etherealnymph.courseflow;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/** Standalone recordings, stored in the phone's Music/时屿 folder. */
public class RecordingsScreen extends FrameLayout implements MainActivity.Screen {
    private final MainActivity a;
    private final Palette p;
    private final ScrollView scroll;
    private final LinearLayout content;
    private MediaPlayer player;
    private String playingId = "";
    private TextView playingButton;

    public RecordingsScreen(MainActivity a) {
        super(a);
        this.a = a;
        this.p = a.palette();
        setBackgroundColor(p.bg);
        content = Ui.column(a);
        content.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 20));
        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rebuild();
    }

    @Override
    public View build() {
        return this;
    }

    @Override
    public void onStop() {
        stopPlayback();
    }

    public void refresh() {
        rebuild();
    }

    private void rebuild() {
        stopPlayback();
        content.removeAllViews();
        LinearLayout head = Ui.row(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = Ui.column(a);
        titles.addView(Ui.eyebrow(a, "RECORDINGS · 录音", p));
        String dir = a.store().data.recordingDir;
        if (dir == null || dir.isEmpty()) dir = "Music/时屿";
        TextView meta = Ui.text(a, "保存到手机 · " + dir, 11, p.faint, false);
        meta.setPadding(0, Ui.dp(a, 3), 0, 0);
        titles.addView(meta);
        head.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView record = Ui.primaryButton(a, "开始录音", p);
        record.setMinWidth(Ui.dp(a, 92));
        record.setOnClickListener(v -> RecorderSheet.openPublic(a,
                "录音 · " + Dates.monthDay(Dates.today()), r -> rebuild()));
        head.addView(record, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(head);

        ArrayList<Store.Recording> list = new ArrayList<>(a.store().data.recordings);
        list.sort(Comparator.comparingLong((Store.Recording r) -> r.createdAt).reversed());
        if (list.isEmpty()) {
            LinearLayout empty = Ui.column(a);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(a, 46), 0, Ui.dp(a, 46));
            empty.addView(Ui.icon(a, R.drawable.ic_mic, 32, p.faint));
            TextView e = Ui.text(a, "还没有录音", 15, p.sub, true);
            e.setPadding(0, Ui.dp(a, 10), 0, 0);
            empty.addView(e);
            TextView s = Ui.text(a, "点右上角开始录音，文件会保存到手机", 11, p.faint, false);
            s.setPadding(0, Ui.dp(a, 4), 0, 0);
            empty.addView(s);
            content.addView(empty);
            return;
        }
        for (Store.Recording r : list) content.addView(card(r));
    }

    private View card(Store.Recording r) {
        LinearLayout card = Ui.row(a);
        card.setBackground(Ui.stroke(a, p.surface, p.line, 1, 16));
        card.setPadding(Ui.dp(a, 12), Ui.dp(a, 11), Ui.dp(a, 8), Ui.dp(a, 11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(a, 10);
        card.setLayoutParams(lp);

        TextView play = Ui.text(a, playingId.equals(r.id) ? "■" : "▶", 17, p.primary, true);
        play.setGravity(Gravity.CENTER);
        play.setBackground(Ui.bg(a, p.primarySoft, 12));
        card.addView(play, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));

        LinearLayout info = Ui.column(a);
        info.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 5), 0);
        info.addView(Ui.text(a, r.name, 13.5f, p.ink, true));
        LinearLayout meta = Ui.row(a);
        meta.setPadding(0, Ui.dp(a, 4), 0, 0);
        String duration = String.format(Locale.CHINA, "%02d:%02d",
                r.durationMs / 60000, (r.durationMs / 1000) % 60);
        meta.addView(Ui.text(a, duration, 10.5f, p.primary, true));
        String date = new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
                .format(new Date(r.createdAt));
        TextView d = Ui.text(a, "  ·  " + date, 10.5f, p.faint, false);
        meta.addView(d);
        info.addView(meta);
        card.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView share = Ui.iconButton(a, R.drawable.ic_share, p.sub, p, false);
        share.setContentDescription("分享录音");
        share.setOnClickListener(v -> share(r));
        card.addView(share);
        ImageView delete = Ui.iconButton(a, R.drawable.ic_close, p.faint, p, false);
        delete.setContentDescription("删除录音");
        delete.setOnClickListener(v -> confirmDelete(r));
        card.addView(delete);

        card.setOnClickListener(v -> toggle(r, play));
        play.setOnClickListener(v -> toggle(r, play));
        return card;
    }

    private void toggle(Store.Recording r, TextView icon) {
        if (playingId.equals(r.id) && player != null) {
            stopPlayback();
            icon.setText("▶");
            return;
        }
        stopPlayback();
        if (r.uri.isEmpty()) {
            Ui.toast(a, "找不到录音文件");
            return;
        }
        try {
            player = new MediaPlayer();
            player.setDataSource(a, Uri.parse(r.uri));
            player.prepare();
            player.start();
            playingId = r.id;
            playingButton = icon;
            icon.setText("■");
            player.setOnCompletionListener(mp -> {
                stopPlayback();
                icon.setText("▶");
            });
        } catch (Exception e) {
            stopPlayback();
            Ui.toast(a, "无法播放这条录音");
        }
    }

    private void stopPlayback() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (playingButton != null) playingButton.setText("▶");
        playingButton = null;
        playingId = "";
    }

    private void share(Store.Recording r) {
        if (r.uri.isEmpty()) return;
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("audio/mp4");
            send.putExtra(Intent.EXTRA_STREAM, Uri.parse(r.uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(send, "分享录音"));
        } catch (Exception e) {
            Ui.toast(a, "没有可用的分享应用");
        }
    }

    private void confirmDelete(Store.Recording r) {
        Sheet.confirm(a, p, "删除这条录音？", r.name + " 会从手机存储和应用列表中移除。",
                "删除", true, () -> {
                    stopPlayback();
                    try {
                        a.getContentResolver().delete(Uri.parse(r.uri), null, null);
                    } catch (Exception ignored) {
                    }
                    a.store().data.recordings.remove(r);
                    a.store().save();
                    rebuild();
                });
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPlayback();
        super.onDetachedFromWindow();
    }
}
