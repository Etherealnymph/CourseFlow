package com.etherealnymph.courseflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small WYSIWYG-ish editor. Formatting is stored in the Spannable while the
 * editor is open, then serialized back to the portable CourseFlow markup.
 */
public class RichEditText extends EditText {

    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\(attachment:([^)]+)\\)");
    private static final Pattern FILE = Pattern.compile("\\[([^\\]]*)\\]\\(attachment:([^)]+)\\)");
    private static final Pattern ALIGN = Pattern.compile("^\\{:\\s*(left|center|right)\\s*\\}\\s*");

    private final Palette p;
    private final List<Store.Attachment> attachments;
    private final float density;
    private final List<Object> pendingFormats = new ArrayList<>();
    private int beforeLength;
    private Runnable formatListener;

    public RichEditText(Context context, Palette p, List<Store.Attachment> attachments) {
        super(context);
        this.p = p;
        this.attachments = attachments == null ? new ArrayList<>() : attachments;
        this.density = context.getResources().getDisplayMetrics().scaledDensity;
        setBackground(Ui.inputBackground(context, p));
        setPadding(Ui.dp(context, 13), Ui.dp(context, 11), Ui.dp(context, 13), Ui.dp(context, 11));
        setTextColor(p.ink);
        setHintTextColor(p.faint);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        setGravity(Gravity.TOP | Gravity.START);
        setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        setMinLines(5);
        setMaxLines(30);
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                beforeLength = s.length();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!pendingFormats.isEmpty() && s.length() > beforeLength) {
                    int count = s.length() - beforeLength;
                    int at = Math.max(0, getSelectionStart() - count);
                    for (Object span : pendingFormats) {
                        Object copy = copySpan(span);
                        if (copy != null) {
                            s.setSpan(copy, at, Math.min(s.length(), at + count),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                    notifyFormats();
                }
                beforeLength = s.length();
            }
        });
    }

    public void setFormatListener(Runnable listener) {
        formatListener = listener;
        notifyFormats();
    }

    public void setMarkup(String markup) {
        setText(parse(markup == null ? "" : markup));
        setSelection(Math.min(length(), getText().length()));
        notifyFormats();
    }

    public boolean isBoldActive() {
        return pendingStyle(Typeface.BOLD) || (!isCollapsed() && spanStyle(Typeface.BOLD));
    }

    public boolean isItalicActive() {
        return pendingStyle(Typeface.ITALIC) || (!isCollapsed() && spanStyle(Typeface.ITALIC));
    }

    public boolean isUnderlineActive() {
        return hasPending(UnderlineSpan.class) || (!isCollapsed() && hasSpan(UnderlineSpan.class));
    }

    public boolean isStrikeActive() {
        return hasPending(StrikethroughSpan.class) || (!isCollapsed() && hasSpan(StrikethroughSpan.class));
    }

    public boolean isHighlightActive() {
        return hasPending(BackgroundColorSpan.class)
                || (!isCollapsed() && hasSpan(BackgroundColorSpan.class));
    }

    public boolean isHeadingActive(int level) {
        for (Object s : pendingFormats) {
            if (s instanceof HeadingSpan && ((HeadingSpan) s).level == level) return true;
        }
        if (isCollapsed()) return false;
        int[] range = lineRange();
        if (range == null) return false;
        for (Object s : getText().getSpans(range[0], range[1], HeadingSpan.class)) {
            if (((HeadingSpan) s).level == level) return true;
        }
        return false;
    }

    public boolean isQuoteActive() {
        if (hasPending(QuoteSpan.class)) return true;
        if (isCollapsed()) return false;
        int[] range = lineRange();
        if (range == null) return false;
        return getText().getSpans(range[0], range[1], QuoteSpan.class).length > 0;
    }

    private boolean isCollapsed() {
        return getSelectionStart() == getSelectionEnd();
    }

    private Object copySpan(Object span) {
        if (span instanceof StyleSpan) return new StyleSpan(((StyleSpan) span).getStyle());
        if (span instanceof UnderlineSpan) return new UnderlineSpan();
        if (span instanceof StrikethroughSpan) return new StrikethroughSpan();
        if (span instanceof BackgroundColorSpan) {
            return new BackgroundColorSpan(((BackgroundColorSpan) span).getBackgroundColor());
        }
        if (span instanceof ForegroundColorSpan) {
            return new ForegroundColorSpan(((ForegroundColorSpan) span).getForegroundColor());
        }
        if (span instanceof RelativeSizeSpan) {
            return new RelativeSizeSpan(((RelativeSizeSpan) span).getSizeChange());
        }
        if (span instanceof HeadingSpan) {
            HeadingSpan h = (HeadingSpan) span;
            return new HeadingSpan(h.level, h.sizeSp, h.color);
        }
        if (span instanceof QuoteSpan) return new QuoteSpan(((QuoteSpan) span).color);
        if (span instanceof AlignmentSpan) {
            return new AlignmentSpan.Standard(((AlignmentSpan) span).getAlignment());
        }
        return null;
    }

    private boolean pendingStyle(int style) {
        for (Object s : pendingFormats) {
            if (s instanceof StyleSpan && ((StyleSpan) s).getStyle() == style) return true;
        }
        return false;
    }

    private boolean spanStyle(int style) {
        int[] range = selectionRange();
        for (Object s : getText().getSpans(range[0], range[1], StyleSpan.class)) {
            if (((StyleSpan) s).getStyle() == style) return true;
        }
        return false;
    }

    private boolean hasPending(Class<?> type) {
        for (Object s : pendingFormats) if (type.isInstance(s)) return true;
        return false;
    }

    private boolean hasSpan(Class<?> type) {
        int[] range = selectionRange();
        return getText().getSpans(range[0], range[1], type).length > 0;
    }

    private int[] selectionRange() {
        int start = Math.max(0, getSelectionStart());
        int end = Math.max(start, getSelectionEnd());
        if (start == end) {
            if (end < length()) end++;
            else if (start > 0) start--;
        }
        return new int[]{start, end};
    }

    private void notifyFormats() {
        if (formatListener != null) formatListener.run();
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        notifyFormats();
    }

    private float downX;
    private float downY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (Math.abs(event.getX() - downX) < 12 && Math.abs(event.getY() - downY) < 12) {
                int offset = getOffsetForPosition(event.getX(), event.getY());
                int end = Math.min(length(), offset + 1);
                if (end <= offset) offset = Math.max(0, offset - 1);
                if (offset < 0) offset = 0;
                for (AttachmentSpan span : getText().getSpans(offset, Math.max(end, offset),
                        AttachmentSpan.class)) {
                    if ("image".equals(span.attachment.type)) {
                        showImage(span.attachment);
                        return true;
                    }
                }
            }
        }
        return super.onTouchEvent(event);
    }

    private void showImage(Store.Attachment attachment) {
        if (!(getContext() instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) getContext();
        Bitmap bitmap = AttachmentStore.decodeImage(attachment.path, 1400, 1400);
        if (bitmap == null) return;
        Sheet sheet = new Sheet(activity, activity.palette(), "查看图片", attachment.name);
        ImageView view = new ImageView(activity);
        view.setAdjustViewBounds(true);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImageBitmap(bitmap);
        sheet.add(view);
        sheet.show();
    }

    void setRichAttachments(List<Store.Attachment> list) {
        attachments.clear();
        if (list != null) attachments.addAll(list);
    }

    void removeAttachment(Store.Attachment attachment) {
        AttachmentSpan[] spans = getText().getSpans(0, length(), AttachmentSpan.class);
        for (AttachmentSpan span : spans) {
            if (span.attachment.id.equals(attachment.id)) {
                int a = getText().getSpanStart(span);
                int b = getText().getSpanEnd(span);
                if (a >= 0 && b <= length() && a < b) getText().delete(a, b);
                return;
            }
        }
    }

    public String getMarkup() {
        return serialize(getText(), attachments);
    }

    public void toggleBold() {
        toggleStyle(Typeface.BOLD);
    }

    public void toggleItalic() {
        toggleStyle(Typeface.ITALIC);
    }

    public void toggleUnderline() {
        toggleSimple(UnderlineSpan.class, new UnderlineSpan());
    }

    public void toggleStrike() {
        toggleSimple(StrikethroughSpan.class, new StrikethroughSpan());
    }

    public void toggleHighlight() {
        toggleSimple(BackgroundColorSpan.class, new BackgroundColorSpan(0x66F2C94C));
    }

    public void applyColor(int color) {
        applySpan(new ForegroundColorSpan(color), ForegroundColorSpan.class);
    }

    public void applySize(float sp) {
        applySpan(new RelativeSizeSpan(sp / 14f), RelativeSizeSpan.class);
    }

    public void applyHeading(int level) {
        int[] range = lineRange();
        if (range == null) return;
        if (range[0] == range[1]) {
            queueOrRemove(new HeadingSpan(level, 26 - (level - 1) * 4, p.ink),
                    HeadingSpan.class, span -> true);
            return;
        }
        removeSpans(range[0], range[1], HeadingSpan.class);
        getText().setSpan(new HeadingSpan(level, 26 - (level - 1) * 4, p.ink),
                range[0], range[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        notifyFormats();
    }

    public void toggleQuote() {
        int[] range = lineRange();
        if (range == null) return;
        if (range[0] == range[1]) {
            queueOrRemove(new QuoteSpan(p.primary), QuoteSpan.class, span -> true);
            return;
        }
        Object[] existing = getText().getSpans(range[0], range[1], QuoteSpan.class);
        if (existing.length > 0) {
            for (Object span : existing) getText().removeSpan(span);
        } else {
            getText().setSpan(new QuoteSpan(p.primary), range[0], range[1],
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        notifyFormats();
    }

    public void applyAlignment(int gravity) {
        int[] range = lineRange();
        if (range == null) return;
        Layout.Alignment align = gravity == Gravity.CENTER ? Layout.Alignment.ALIGN_CENTER
                : gravity == Gravity.END ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL;
        if (range[0] == range[1]) {
            queueOrRemove(new AlignmentSpan.Standard(align), AlignmentSpan.class, span -> true);
            return;
        }
        removeSpans(range[0], range[1], AlignmentSpan.class);
        getText().setSpan(new AlignmentSpan.Standard(align), range[0], range[1],
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        notifyFormats();
    }

    public void insertTable() {
        String template = "| 列1 | 列2 |\n|---|---|\n| 内容 | 内容 |";
        insertPlaceholder(new TableSpan(template, p));
    }

    public void insertAttachment(Store.Attachment attachment) {
        if (attachment == null) return;
        insertPlaceholder(new AttachmentSpan(attachment, p));
    }

    private void insertPlaceholder(ReplacementSpan span) {
        int start = Math.max(0, getSelectionStart());
        if (start > length()) start = length();
        boolean needLeading = start > 0 && getText().charAt(start - 1) != '\n';
        String insert = (needLeading ? "\n" : "") + "\uFFFC\n";
        getText().insert(start, insert);
        int at = start + (needLeading ? 1 : 0);
        getText().setSpan(span, at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        setSelection(Math.min(length(), at + 2));
    }

    private void toggleStyle(int style) {
        int start = Math.max(0, getSelectionStart());
        int end = Math.max(start, getSelectionEnd());
        if (start == end) {
            queueOrRemove(new StyleSpan(style), StyleSpan.class, span ->
                    span instanceof StyleSpan && ((StyleSpan) span).getStyle() == style);
            return;
        }
        pendingFormats.clear();
        StyleSpan[] spans = getText().getSpans(start, end, StyleSpan.class);
        for (StyleSpan s : spans) {
            if (s.getStyle() == style) {
                getText().removeSpan(s);
                notifyFormats();
                return;
            }
        }
        getText().setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        notifyFormats();
    }

    private void toggleSimple(Class<?> type, Object newSpan) {
        int start = Math.max(0, getSelectionStart());
        int end = Math.max(start, getSelectionEnd());
        if (start == end) {
            queueOrRemove(newSpan, type, span -> true);
            return;
        }
        pendingFormats.clear();
        Object[] spans = getText().getSpans(start, end, type);
        if (spans.length > 0) {
            for (Object s : spans) getText().removeSpan(s);
            notifyFormats();
            return;
        }
        getText().setSpan(newSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        notifyFormats();
    }

    private void applySpan(Object span, Class<?> type) {
        int start = Math.max(0, getSelectionStart());
        int end = Math.max(start, getSelectionEnd());
        if (start == end) {
            queueOrRemove(span, type, candidate -> true);
            return;
        }
        pendingFormats.clear();
        removeSpans(start, end, type);
        getText().setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        notifyFormats();
    }

    private void removeSpans(int start, int end, Class<?> type) {
        for (Object s : getText().getSpans(start, end, type)) getText().removeSpan(s);
    }

    private void queueOrRemove(Object span, Class<?> type, Predicate<Object> match) {
        for (int i = 0; i < pendingFormats.size(); i++) {
            Object existing = pendingFormats.get(i);
            if (type.isInstance(existing) && match.test(existing)) {
                pendingFormats.remove(i);
                notifyFormats();
                return;
            }
        }
        pendingFormats.add(span);
        notifyFormats();
    }

    private int[] lineRange() {
        int start = Math.max(0, getSelectionStart());
        int end = Math.max(start, getSelectionEnd());
        String text = getText().toString();
        int a = text.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        int b = text.indexOf('\n', end);
        if (b < 0) b = text.length();
        return new int[]{a, b};
    }

    // ------------------------------------------------------------ parse

    private CharSequence parse(String markup) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String[] lines = markup.replace("\r\n", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("|")) {
                int j = i;
                StringBuilder table = new StringBuilder();
                while (j < lines.length && lines[j].trim().startsWith("|")) {
                    table.append(lines[j]).append(j == lines.length - 1 ? "" : "\n");
                    j++;
                }
                int st = sb.length();
                sb.append('\uFFFC');
                sb.setSpan(new TableSpan(table.toString().trim(), p), st, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = j - 1;
                appendNewline(sb, i, lines.length);
                continue;
            }
            Matcher image = IMAGE.matcher(line.trim());
            Matcher file = FILE.matcher(line.trim());
            Store.Attachment att = null;
            if (image.matches()) att = find(image.group(2));
            else if (file.matches()) att = find(file.group(2));
            if (att != null) {
                int st = sb.length();
                sb.append('\uFFFC');
                sb.setSpan(new AttachmentSpan(att, p), st, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                appendNewline(sb, i, lines.length);
                continue;
            }

            String align = null;
            Matcher am = ALIGN.matcher(line);
            if (am.find()) {
                align = am.group(1);
                line = line.substring(am.end());
            }
            int heading = 0;
            if (line.startsWith("### ")) {
                heading = 3;
                line = line.substring(4);
            } else if (line.startsWith("## ")) {
                heading = 2;
                line = line.substring(3);
            } else if (line.startsWith("# ")) {
                heading = 1;
                line = line.substring(2);
            }
            boolean quote = line.startsWith("> ");
            if (quote) line = line.substring(2);
            if (line.startsWith("- [ ] ")) line = "☐ " + line.substring(6);
            else if (line.startsWith("- [x] ") || line.startsWith("- [X] ")) {
                line = "☑ " + line.substring(6);
            } else if (line.startsWith("- ")) {
                line = "• " + line.substring(2);
            }

            int st = sb.length();
            SpannableStringBuilder inline = RichText.inline(line, p, p.ink, false);
            sb.append(inline);
            int en = sb.length();
            if (heading > 0) {
                sb.setSpan(new HeadingSpan(heading, 26 - (heading - 1) * 4, p.ink), st, en,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (quote) {
                sb.setSpan(new QuoteSpan(p.primary), st, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (align != null) {
                Layout.Alignment a = "center".equals(align) ? Layout.Alignment.ALIGN_CENTER
                        : "right".equals(align) ? Layout.Alignment.ALIGN_OPPOSITE
                        : Layout.Alignment.ALIGN_NORMAL;
                sb.setSpan(new AlignmentSpan.Standard(a), st, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            appendNewline(sb, i, lines.length);
        }
        return sb;
    }

    private void appendNewline(SpannableStringBuilder sb, int index, int total) {
        if (index < total - 1) sb.append('\n');
    }

    private Store.Attachment find(String id) {
        for (Store.Attachment a : attachments) if (a.id.equals(id)) return a;
        return null;
    }

    // ------------------------------------------------------------ serialize

    private String serialize(Spanned text, List<Store.Attachment> attachments) {
        StringBuilder out = new StringBuilder();
        int len = text.length();
        int lineStart = 0;
        while (lineStart <= len) {
            int lineEnd = lineStart;
            while (lineEnd < len && text.charAt(lineEnd) != '\n') lineEnd++;
            out.append(serializeLine(text, lineStart, lineEnd));
            if (lineEnd < len) out.append('\n');
            if (lineEnd >= len) break;
            lineStart = lineEnd + 1;
        }
        return out.toString();
    }

    private String serializeLine(Spanned text, int start, int end) {
        // A table / attachment placeholder owns the entire line.
        TableSpan[] tableSpans = text.getSpans(start, end, TableSpan.class);
        for (TableSpan table : tableSpans) {
            if (text.getSpanStart(table) <= start && text.getSpanEnd(table) >= end) {
                return table.markup;
            }
        }
        AttachmentSpan[] attachmentSpans = text.getSpans(start, end, AttachmentSpan.class);
        for (AttachmentSpan a : attachmentSpans) {
            if (text.getSpanStart(a) <= start && text.getSpanEnd(a) >= end) {
                return a.token();
            }
        }

        String prefix = "";
        AlignmentSpan[] aligns = text.getSpans(start, end, AlignmentSpan.class);
        if (aligns.length > 0) {
            Layout.Alignment a = aligns[0].getAlignment();
            if (a == Layout.Alignment.ALIGN_CENTER) prefix += "{: center}";
            else if (a == Layout.Alignment.ALIGN_OPPOSITE) prefix += "{: right}";
        }
        HeadingSpan[] headings = text.getSpans(start, end, HeadingSpan.class);
        if (headings.length > 0) prefix += new String(new char[headings[0].level]).replace('\0', '#') + " ";
        QuoteSpan[] quotes = text.getSpans(start, end, QuoteSpan.class);
        if (quotes.length > 0) prefix += "> ";

        TreeMap<Integer, List<String>> opens = new TreeMap<>();
        TreeMap<Integer, List<String>> closes = new TreeMap<>();
        Object[] spans = text.getSpans(start, end, Object.class);
        for (Object span : spans) {
            if (span instanceof HeadingSpan || span instanceof QuoteSpan
                    || span instanceof AlignmentSpan || span instanceof TableSpan
                    || span instanceof AttachmentSpan) continue;
            int a = Math.max(start, text.getSpanStart(span));
            int b = Math.min(end, text.getSpanEnd(span));
            if (a >= b) continue;
            String[] markers = markersFor(span);
            if (markers == null) continue;
            add(opens, a, markers[0]);
            add(closes, b, markers[1]);
        }

        StringBuilder line = new StringBuilder(prefix);
        for (int i = start; i <= end; i++) {
            if (i < end) {
                List<String> close = closes.get(i);
                if (close != null) {
                    for (int j = close.size() - 1; j >= 0; j--) line.append(close.get(j));
                }
                List<String> open = opens.get(i);
                if (open != null) {
                    for (String s : open) line.append(s);
                }
                char c = text.charAt(i);
                if (c != '\uFFFC') line.append(c);
            }
        }
        List<String> endClose = closes.get(end);
        if (endClose != null) {
            for (int j = endClose.size() - 1; j >= 0; j--) line.append(endClose.get(j));
        }
        return line.toString();
    }

    private void add(TreeMap<Integer, List<String>> map, int pos, String value) {
        map.computeIfAbsent(pos, k -> new ArrayList<>()).add(value);
    }

    private String[] markersFor(Object span) {
        if (span instanceof StyleSpan) {
            int style = ((StyleSpan) span).getStyle();
            if (style == Typeface.BOLD) return new String[]{"**", "**"};
            if (style == Typeface.ITALIC) return new String[]{"*", "*"};
            if (style == Typeface.BOLD_ITALIC) return new String[]{"**", "**"};
        }
        if (span instanceof UnderlineSpan) return new String[]{"__", "__"};
        if (span instanceof StrikethroughSpan) return new String[]{"~~", "~~"};
        if (span instanceof BackgroundColorSpan) return new String[]{"==", "=="};
        if (span instanceof ForegroundColorSpan) {
            String c = String.format("#%06X", 0xFFFFFF & ((ForegroundColorSpan) span).getForegroundColor());
            return new String[]{"[color=" + c + "]", "[/color]"};
        }
        if (span instanceof RelativeSizeSpan) {
            int size = Math.round(14 * ((RelativeSizeSpan) span).getSizeChange());
            return new String[]{"[size=" + size + "]", "[/size]"};
        }
        return null;
    }

    // ------------------------------------------------------------ custom spans

    public static class HeadingSpan extends android.text.style.MetricAffectingSpan {
        final int level;
        final float sizeSp;
        final int color;

        HeadingSpan(int level, float sizeSp, int color) {
            this.level = level;
            this.sizeSp = sizeSp;
            this.color = color;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setTextSize(sizeSp * density());
            tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            tp.setColor(color);
        }

        @Override
        public void updateMeasureState(TextPaint tp) {
            tp.setTextSize(sizeSp * density());
            tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        }

        private float density() {
            return android.content.res.Resources.getSystem().getDisplayMetrics().scaledDensity;
        }
    }

    public static class QuoteSpan extends android.text.style.MetricAffectingSpan {
        final int color;

        QuoteSpan(int color) {
            this.color = color;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setColor(color);
            tp.bgColor = 0x330F7A64;
            tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        }

        @Override
        public void updateMeasureState(TextPaint tp) {
            tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        }
    }

    public static class TableSpan extends ReplacementSpan {
        final String markup;
        final Palette p;

        TableSpan(String markup, Palette p) {
            this.markup = markup;
            this.p = p;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return (int) paint.measureText("▦ 表格") + 10;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top,
                         int y, int bottom, Paint paint) {
            paint.setColor(p.primarySoft);
            canvas.drawRect(x, top + 2, x + getSize(paint, text, start, end, null), bottom - 2, paint);
            paint.setColor(p.primary);
            canvas.drawText("▦ 表格", x + 4, y - 3, paint);
        }
    }

    public static class AttachmentSpan extends ReplacementSpan {
        final Store.Attachment attachment;
        final Palette p;
        private final Bitmap bitmap;
        private final int width;
        private final int height;

        AttachmentSpan(Store.Attachment attachment, Palette p) {
            this.attachment = attachment;
            this.p = p;
            Bitmap b = null;
            int w = 0;
            int h = 0;
            if ("image".equals(attachment.type)) {
                b = AttachmentStore.decodeImage(attachment.path, 720, 720);
                if (b != null) {
                    float density = android.content.res.Resources.getSystem()
                            .getDisplayMetrics().density;
                    float maxW = 112 * density;
                    float maxH = 144 * density;
                    float scale = Math.min(1f, Math.min(maxW / b.getWidth(), maxH / b.getHeight()));
                    w = Math.max(1, (int) (b.getWidth() * scale));
                    h = Math.max(1, (int) (b.getHeight() * scale));
                }
            }
            bitmap = b;
            width = w;
            height = h;
        }

        String token() {
            if ("image".equals(attachment.type)) {
                return "![" + attachment.name + "](attachment:" + attachment.id + ")";
            }
            return "[" + AttachmentStore.typeLabel(attachment) + "：" + attachment.name
                    + "](attachment:" + attachment.id + ")";
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (bitmap != null) {
                if (fm != null) {
                    fm.ascent = -height;
                    fm.top = -height;
                    fm.descent = 0;
                    fm.bottom = 0;
                }
                return width;
            }
            return (int) paint.measureText(label());
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top,
                         int y, int bottom, Paint paint) {
            if (bitmap != null) {
                Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                RectF dst = new RectF(x, y - height, x + width, y);
                canvas.drawBitmap(bitmap, src, dst, paint);
                return;
            }
            String label = label();
            paint.setColor(p.primarySoft);
            canvas.drawRoundRect(x, top + 2, x + paint.measureText(label), bottom - 2, 6, 6, paint);
            paint.setColor(p.primary);
            canvas.drawText(label, x + 3, y - 3, paint);
        }

        private String label() {
            return ("image".equals(attachment.type) ? "▧ " : "▤ ")
                    + (attachment.name.isEmpty() ? AttachmentStore.typeLabel(attachment) : attachment.name);
        }
    }
}
