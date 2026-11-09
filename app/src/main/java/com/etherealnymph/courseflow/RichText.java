package com.etherealnymph.courseflow;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders the simple Markdown-like note markup into native Android views. */
public final class RichText {

    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\(attachment:([^)]+)\\)");
    private static final Pattern FILE = Pattern.compile("\\[([^\\]]*)\\]\\(attachment:([^)]+)\\)");
    private static final Pattern ALIGN = Pattern.compile("^\\{:\\s*(left|center|right)\\s*\\}\\s*");
    private static MediaPlayer player;

    private RichText() {}

    public static LinearLayout render(Activity a, String markup, List<Store.Attachment> attachments,
                                      Palette p, boolean compact) {
        LinearLayout root = Ui.column(a);
        if (markup == null) markup = "";
        String[] lines = markup.replace("\r\n", "\n").split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (line.trim().startsWith("|")) {
                int j = i;
                while (j < lines.length && lines[j].trim().startsWith("|")) j++;
                try {
                    root.addView(table(a, sub(lines, i, j), p, compact));
                } catch (Exception e) {
                    root.addView(text(a, String.join("\n", sub(lines, i, j)), p.sub,
                            compact ? 11 : 13, p, false));
                }
                i = j;
                continue;
            }
            if (line.trim().isEmpty()) {
                root.addView(Ui.spacer(a, compact ? 3 : 6));
                i++;
                continue;
            }
            Matcher image = IMAGE.matcher(line.trim());
            if (image.matches()) {
                Store.Attachment att = find(attachments, image.group(2));
                if (att != null) root.addView(imageView(a, att, p, compact));
                else root.addView(text(a, "图片已丢失", p.faint, compact ? 11 : 13, p, false));
                i++;
                continue;
            }
            Matcher file = FILE.matcher(line.trim());
            if (file.matches()) {
                Store.Attachment att = find(attachments, file.group(2));
                if (att != null) root.addView(attachmentRow(a, att, p));
                else root.addView(text(a, "附件已丢失", p.faint, compact ? 11 : 13, p, false));
                i++;
                continue;
            }
            String alignment = "left";
            Matcher am = ALIGN.matcher(line);
            if (am.find()) {
                alignment = am.group(1);
                line = line.substring(am.end());
            }
            TextView tv;
            int size = compact ? 12 : 14;
            boolean bold = false;
            if (line.startsWith("### ")) {
                line = line.substring(4);
                size = compact ? 13 : 15;
                bold = true;
            } else if (line.startsWith("## ")) {
                line = line.substring(3);
                size = compact ? 15 : 18;
                bold = true;
            } else if (line.startsWith("# ")) {
                line = line.substring(2);
                size = compact ? 18 : 21;
                bold = true;
            } else if (line.startsWith("> ")) {
                line = line.substring(2);
                size = compact ? 11 : 13;
                TextView quote = text(a, "▏ " + line, p.sub, size, p, false);
                quote.setPadding(Ui.dp(a, 10), Ui.dp(a, 7), Ui.dp(a, 10), Ui.dp(a, 7));
                quote.setBackground(Ui.stroke(a, p.surfaceAlt, p.line, 1, 9));
                root.addView(quote, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                i++;
                continue;
            } else if (line.startsWith("- [x] ") || line.startsWith("- [X] ")) {
                line = "☑ " + line.substring(6);
            } else if (line.startsWith("- [ ] ")) {
                line = "☐ " + line.substring(6);
            } else if (line.startsWith("- ")) {
                line = "• " + line.substring(2);
            }
            tv = text(a, line, bold ? p.ink : p.sub, size, p, bold);
            tv.setGravity("center".equals(alignment) ? Gravity.CENTER
                    : "right".equals(alignment) ? Gravity.END : Gravity.START);
            root.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            i++;
        }
        if (root.getChildCount() == 0) {
            root.addView(text(a, "（空）", p.faint, 12, p, false));
        }
        return root;
    }

    public static SpannableStringBuilder inline(String source, Palette p, int color, boolean bold) {
        SpannableStringBuilder sb = new SpannableStringBuilder(source == null ? "" : source);
        if (bold) sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        applyPair(sb, "**", new SpanFactory() {
            public Object make() {
                return new StyleSpan(Typeface.BOLD);
            }
        });
        applyPair(sb, "__", new SpanFactory() {
            public Object make() {
                return new UnderlineSpan();
            }
        });
        applyPair(sb, "~~", new SpanFactory() {
            public Object make() {
                return new StrikethroughSpan();
            }
        });
        applyPair(sb, "==", new SpanFactory() {
            public Object make() {
                return new BackgroundColorSpan(0x33F2C94C);
            }
        });
        applyPair(sb, "*", new SpanFactory() {
            public Object make() {
                return new StyleSpan(Typeface.ITALIC);
            }
        });
        applyTag(sb, "\\[color=([a-zA-Z#0-9]+)\\](.*?)\\[/color\\]", true);
        applyTag(sb, "\\[size=(\\d+)\\](.*?)\\[/size\\]", false);
        return sb;
    }

    public static String strip(String markup) {
        return WeekSpec.stripForPreview(markup);
    }

    private static TextView text(Activity a, String value, int color, float size, Palette p,
                                 boolean bold) {
        TextView tv = new TextView(a);
        tv.setText(inline(value, p, color, bold));
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size);
        tv.setTextColor(color);
        tv.setLineSpacing(Ui.dp(a, 3), 1f);
        tv.setIncludeFontPadding(false);
        tv.setTextIsSelectable(false);
        return tv;
    }

    private static View table(Activity a, List<String> lines, Palette p, boolean compact) {
        GridLayout grid = new GridLayout(a);
        int cols = 1;
        List<String[]> rows = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.matches("\\|?\\s*[-: ]+\\s*(\\|\\s*[-: ]+\\s*)+\\|?")) continue;
            if (t.startsWith("|")) t = t.substring(1);
            if (t.endsWith("|")) t = t.substring(0, t.length() - 1);
            String[] cells = t.split("\\|", -1);
            cols = Math.max(cols, cells.length);
            rows.add(cells);
        }
        grid.setColumnCount(cols);
        for (int r = 0; r < rows.size(); r++) {
            String[] cells = rows.get(r);
            for (int c = 0; c < cols; c++) {
                TextView cell = text(a, c < cells.length ? cells[c].trim() : "",
                        r == 0 ? p.ink : p.sub, compact ? 10.5f : 12, p, r == 0);
                cell.setPadding(Ui.dp(a, 7), Ui.dp(a, 6), Ui.dp(a, 7), Ui.dp(a, 6));
                cell.setBackground(Ui.stroke(a, r == 0 ? p.surfaceAlt : p.surface,
                        p.line, 1, 4));
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                        GridLayout.spec(r), GridLayout.spec(c, 1f));
                lp.width = 0;
                lp.setMargins(Ui.dp(a, 1), Ui.dp(a, 1), Ui.dp(a, 1), Ui.dp(a, 1));
                grid.addView(cell, lp);
            }
        }
        return grid;
    }

    private static View imageView(Activity a, Store.Attachment att, Palette p, boolean compact) {
        Bitmap bitmap = AttachmentStore.decodeImage(att.path, 1000, compact ? 420 : 900);
        if (bitmap == null) return text(a, "图片无法读取", p.faint, 12, p, false);
        ImageView image = new ImageView(a);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackground(Ui.bg(a, p.surfaceAlt, 10));
        image.setPadding(Ui.dp(a, 2), Ui.dp(a, 2), Ui.dp(a, 2), Ui.dp(a, 2));
        image.setContentDescription(att.name);
        image.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        image.setOnClickListener(v -> {
            Sheet sheet = new Sheet(a, p, att.name.isEmpty() ? "图片" : att.name, null);
            ImageView big = new ImageView(a);
            big.setImageBitmap(AttachmentStore.decodeImage(att.path, 1600, 1600));
            big.setAdjustViewBounds(true);
            big.setScaleType(ImageView.ScaleType.FIT_CENTER);
            sheet.add(big);
            sheet.show();
        });
        return image;
    }

    private static View attachmentRow(Activity a, Store.Attachment att, Palette p) {
        LinearLayout row = Ui.row(a);
        row.setBackground(Ui.stroke(a, p.surfaceAlt, p.line, 1, 12));
        row.setPadding(Ui.dp(a, 11), Ui.dp(a, 9), Ui.dp(a, 11), Ui.dp(a, 9));
        row.addView(Ui.icon(a, "audio".equals(att.type) ? R.drawable.ic_mic : R.drawable.ic_file,
                19, p.primary));
        LinearLayout lines = Ui.column(a);
        lines.setPadding(Ui.dp(a, 9), 0, 0, 0);
        lines.addView(Ui.text(a, att.name.isEmpty() ? AttachmentStore.typeLabel(att) : att.name,
                12, p.ink, true));
        lines.addView(Ui.text(a, AttachmentStore.typeLabel(att), 10, p.faint, false));
        row.addView(lines, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if ("audio".equals(att.type)) playAudio(a, att);
            else AttachmentStore.openFile(a, att);
        });
        return row;
    }

    private static void playAudio(Activity a, Store.Attachment att) {
        try {
            if (player != null) {
                player.release();
                player = null;
            }
            player = new MediaPlayer();
            player.setDataSource(att.path);
            player.prepare();
            player.start();
            Ui.toast(a, "正在播放：" + att.name);
            player.setOnCompletionListener(mp -> {
                mp.release();
                if (player == mp) player = null;
            });
        } catch (Exception e) {
            Ui.toast(a, "无法播放这段录音");
        }
    }

    private static Store.Attachment find(List<Store.Attachment> list, String id) {
        if (list == null) return null;
        for (Store.Attachment a : list) if (a.id.equals(id)) return a;
        return null;
    }

    private static List<String> sub(String[] all, int start, int end) {
        List<String> list = new ArrayList<>();
        for (int i = start; i < end; i++) list.add(all[i]);
        return list;
    }

    private interface SpanFactory {
        Object make();
    }

    private static void applyPair(SpannableStringBuilder sb, String token, SpanFactory factory) {
        int from = 0;
        while (true) {
            int start = sb.toString().indexOf(token, from);
            if (start < 0) break;
            int end = sb.toString().indexOf(token, start + token.length());
            if (end < 0) break;
            String inner = sb.subSequence(start + token.length(), end).toString();
            sb.replace(start, end + token.length(), inner);
            sb.setSpan(factory.make(), start, start + inner.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = start + inner.length();
        }
    }

    private static void applyTag(SpannableStringBuilder sb, String regex, boolean color) {
        Pattern pattern = Pattern.compile(regex);
        int from = 0;
        while (true) {
            Matcher m = pattern.matcher(sb.toString());
            m.region(from, sb.length());
            if (!m.find()) break;
            String tag = m.group(1);
            String inner = m.group(2);
            sb.replace(m.start(), m.end(), inner);
            if (color) {
                int c = colorFor(tag);
                sb.setSpan(new ForegroundColorSpan(c), m.start(), m.start() + inner.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                try {
                    float scale = Math.max(0.6f, Math.min(2.2f,
                            Integer.parseInt(tag) / 14f));
                    sb.setSpan(new RelativeSizeSpan(scale), m.start(), m.start() + inner.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (Exception ignored) {
                }
            }
            from = m.start() + inner.length();
        }
    }

    private static int colorFor(String name) {
        switch (name.toLowerCase()) {
            case "red":
                return 0xFFD94A4A;
            case "orange":
                return 0xFFE07A2D;
            case "blue":
                return 0xFF3B72C4;
            case "green":
                return 0xFF2E8B57;
            case "purple":
                return 0xFF7A5AB5;
            case "gray":
                return 0xFF7B8580;
            default:
                try {
                    return Color.parseColor(name.startsWith("#") ? name : "#" + name);
                } catch (Exception e) {
                    return 0xFF333333;
                }
        }
    }
}
