package com.etherealnymph.courseflow;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Formatting toolbar, attachment strip and preview shared by notes and todos. */
public final class RichEditorHelper {

    private final MainActivity a;
    private final Palette p;
    private final RichEditText body;
    private final List<Store.Attachment> attachments;
    private final LinearLayout strip;
    private final LinearLayout preview;
    private final TextView previewToggle;
    private final List<TextView> formatViews = new ArrayList<>();
    private final List<BooleanSupplier> formatStates = new ArrayList<>();

    public RichEditorHelper(MainActivity a, RichEditText body, List<Store.Attachment> attachments) {
        this.a = a;
        this.p = a.palette();
        this.body = body;
        if (attachments == null) attachments = new ArrayList<>();
        this.attachments = attachments;
        body.setRichAttachments(attachments);

        strip = Ui.column(a);
        preview = Ui.column(a);
        preview.setVisibility(View.GONE);
        previewToggle = Ui.textButton(a, "预览", p.primary, p);
    }

    public View toolbar() {
        LinearLayout outer = Ui.column(a);
        LinearLayout attach = Ui.row(a);
        attach.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 5));
        TextView image = toolbarButton("相册");
        image.setOnClickListener(v -> a.pickImage(this::addAttachment));
        attach.addView(image);
        TextView camera = toolbarButton("拍照");
        camera.setOnClickListener(v -> a.captureImage(this::addAttachment));
        attach.addView(camera);
        TextView audio = toolbarButton("录音");
        audio.setOnClickListener(v -> RecorderSheet.open(a, "录音 · " + Dates.monthDay(Dates.today()),
                this::addAttachment));
        attach.addView(audio);
        TextView file = toolbarButton("文件");
        file.setOnClickListener(v -> a.pickFile(this::addAttachment));
        attach.addView(file);
        outer.addView(attach);

        HorizontalScrollView scroll = new HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2));
        addFormatButton(row, "B", body::isBoldActive, body::toggleBold);
        addFormatButton(row, "I", body::isItalicActive, body::toggleItalic);
        addFormatButton(row, "U", body::isUnderlineActive, body::toggleUnderline);
        addFormatButton(row, "S", body::isStrikeActive, body::toggleStrike);
        addFormatButton(row, "H1", () -> body.isHeadingActive(1), () -> body.applyHeading(1));
        addFormatButton(row, "H2", () -> body.isHeadingActive(2), () -> body.applyHeading(2));
        addFormatButton(row, "H3", () -> body.isHeadingActive(3), () -> body.applyHeading(3));
        addFormatButton(row, "注释", body::isQuoteActive, body::toggleQuote);
        addFormatButton(row, "高亮", body::isHighlightActive, body::toggleHighlight);
        TextView color = toolbarButton("颜色");
        color.setOnClickListener(v -> showColorPicker());
        row.addView(color);
        TextView size = toolbarButton("字号");
        size.setOnClickListener(v -> showSizePicker());
        row.addView(size);
        addButton(row, "左", () -> body.applyAlignment(Gravity.START));
        addButton(row, "中", () -> body.applyAlignment(Gravity.CENTER));
        addButton(row, "右", () -> body.applyAlignment(Gravity.END));
        addButton(row, "表格", body::insertTable);
        addButton(row, "•", () -> insertPlain("• "));
        addButton(row, "☐", () -> insertPlain("☐ "));

        previewToggle.setOnClickListener(v -> {
            boolean show = preview.getVisibility() != View.VISIBLE;
            preview.setVisibility(show ? View.VISIBLE : View.GONE);
            previewToggle.setText(show ? "收起预览" : "预览");
            if (show) renderPreview();
        });
        row.addView(previewToggle);
        scroll.addView(row);
        outer.addView(scroll);
        body.setFormatListener(this::refreshFormatButtons);
        refreshFormatButtons();
        return outer;
    }

    private void addFormatButton(LinearLayout row, String label,
                                 BooleanSupplier active, Runnable action) {
        TextView button = toolbarButton(label);
        button.setOnClickListener(v -> {
            action.run();
            refreshFormatButtons();
        });
        row.addView(button);
        formatViews.add(button);
        formatStates.add(active);
    }

    private void refreshFormatButtons() {
        for (int i = 0; i < formatViews.size(); i++) {
            TextView view = formatViews.get(i);
            boolean active = formatStates.get(i).getAsBoolean();
            view.setTextColor(active ? p.primary : p.sub);
            view.setBackground(Ui.pressable(a, active ? p.primarySoft : p.surfaceAlt,
                    Ui.dp(a, 9), Palette.alpha(p.ink, 18)));
        }
    }

    public View strip() {
        updateStrip();
        return strip;
    }

    public View preview() {
        return preview;
    }

    public void addAttachment(Store.Attachment attachment) {
        if (attachment == null) return;
        if (!attachments.contains(attachment)) attachments.add(attachment);
        body.insertAttachment(attachment);
        updateStrip();
        if (preview.getVisibility() == View.VISIBLE) renderPreview();
    }

    public void removeAttachment(Store.Attachment attachment) {
        attachments.remove(attachment);
        body.removeAttachment(attachment);
        updateStrip();
        if (preview.getVisibility() == View.VISIBLE) renderPreview();
    }

    public ArrayList<Store.Attachment> attachments() {
        return new ArrayList<>(attachments);
    }

    private void renderPreview() {
        preview.removeAllViews();
        preview.setPadding(0, Ui.dp(a, 10), 0, 0);
        preview.addView(RichText.render(a, body.getMarkup(), attachments, p, true));
    }

    private void showColorPicker() {
        Sheet sheet = new Sheet(a, p, "字体颜色", "直接应用到选中的文字");
        LinearLayout row = Ui.row(a);
        String[][] colors = {{"红", "#D64545"}, {"橙", "#C47A22"}, {"蓝", "#356C8A"},
                {"绿", "#0F7A64"}, {"紫", "#7563A8"}, {"灰", "#70807A"}};
        for (String[] c : colors) {
            TextView chip = Ui.chip(a, c[0], p, false);
            chip.setOnClickListener(v -> {
                body.applyColor(Color.parseColor(c[1]));
                sheet.dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = Ui.dp(a, 4);
            row.addView(chip, lp);
        }
        sheet.add(row);
        TextView hint = Ui.text(a, "选中文字后点击颜色；格式会直接显示，不再出现标记符号。",
                10.5f, p.faint, false);
        hint.setPadding(0, Ui.dp(a, 10), 0, 0);
        sheet.add(hint);
        sheet.show();
    }

    private void showSizePicker() {
        Sheet sheet = new Sheet(a, p, "字号", "直接应用到选中的文字");
        LinearLayout row = Ui.row(a);
        String[] sizes = {"小", "正文", "大", "标题"};
        float[] values = {12f, 14f, 18f, 22f};
        for (int i = 0; i < sizes.length; i++) {
            final float value = values[i];
            TextView chip = Ui.chip(a, sizes[i], p, false);
            chip.setOnClickListener(v -> {
                body.applySize(value);
                sheet.dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = Ui.dp(a, 4);
            row.addView(chip, lp);
        }
        sheet.add(row);
        sheet.show();
    }

    private void updateStrip() {
        strip.removeAllViews();
        if (attachments.isEmpty()) return;
        TextView label = Ui.text(a, "附件 · " + attachments.size(), 10.5f, p.faint, true);
        label.setPadding(0, Ui.dp(a, 7), 0, Ui.dp(a, 4));
        strip.addView(label);
        HorizontalScrollView scroll = new HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(a);
        scroll.addView(row);
        for (Store.Attachment att : attachments) {
            LinearLayout chip = Ui.row(a);
            chip.setBackground(Ui.bg(a, p.surfaceAlt, 10));
            chip.setPadding(Ui.dp(a, 8), Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5));
            if ("image".equals(att.type)) {
                ImageView thumb = new ImageView(a);
                thumb.setImageBitmap(AttachmentStore.decodeImage(att.path, 80, 80));
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                chip.addView(thumb, new LinearLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)));
            } else {
                chip.addView(Ui.icon(a, "audio".equals(att.type)
                        ? R.drawable.ic_mic : R.drawable.ic_file, 15, p.primary));
            }
            TextView name = Ui.text(a, att.name, 10, p.sub, false);
            name.setMaxWidth(Ui.dp(a, 100));
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setPadding(Ui.dp(a, 5), 0, Ui.dp(a, 4), 0);
            chip.addView(name);
            TextView remove = Ui.text(a, "×", 13, p.faint, true);
            remove.setPadding(Ui.dp(a, 3), 0, Ui.dp(a, 3), 0);
            remove.setOnClickListener(v -> removeAttachment(att));
            chip.addView(remove);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(a, 6);
            row.addView(chip, lp);
        }
        strip.addView(scroll);
    }

    private TextView toolbarButton(String label) {
        TextView tv = Ui.text(a, label, 11, p.sub, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(Ui.dp(a, 9), Ui.dp(a, 7), Ui.dp(a, 9), Ui.dp(a, 7));
        tv.setBackground(Ui.pressable(a, p.surfaceAlt, Ui.dp(a, 9), Palette.alpha(p.ink, 18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(a, 4);
        tv.setLayoutParams(lp);
        tv.setClickable(true);
        return tv;
    }

    private void insertPlain(String value) {
        int at = Math.max(0, body.getSelectionStart());
        if (at > body.length()) at = body.length();
        body.getText().insert(at, value);
        body.setSelection(Math.min(body.length(), at + value.length()));
    }

    private void addButton(LinearLayout row, String label, Runnable action) {
        TextView button = toolbarButton(label);
        button.setOnClickListener(v -> action.run());
        row.addView(button);
    }
}
