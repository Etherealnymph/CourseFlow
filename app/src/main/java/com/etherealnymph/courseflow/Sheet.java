package com.etherealnymph.courseflow;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A lightweight bottom sheet built on a plain Dialog, with no external deps. */
public class Sheet {

    private final Activity activity;
    private final Palette p;
    private final Dialog dialog;
    private final LinearLayout content;
    private final LinearLayout footer;
    private final TextView titleView;
    private final TextView subtitleView;

    public Sheet(Activity activity, Palette p, String title, String subtitle) {
        this.activity = activity;
        this.p = p;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout outside = new FrameLayout(activity);
        outside.setBackgroundColor(Color.TRANSPARENT);
        int side = Ui.dp(activity, 10);
        outside.setPadding(side, Ui.dp(activity, 24), side, side);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.bg(activity, p.surface, 26));
        card.setElevation(Ui.dp(activity, 18));
        card.setClipToOutline(true);
        outside.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View handle = new View(activity);
        handle.setBackground(Ui.bg(activity, Palette.alpha(p.ink, 36), 2));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = Ui.dp(activity, 10);
        card.addView(handle, hlp);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.TOP);
        head.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 14),
                Ui.dp(activity, 12), Ui.dp(activity, 10));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleView = Ui.text(activity, title == null ? "" : title, 20, p.ink, true);
        titles.addView(titleView);
        subtitleView = Ui.text(activity, subtitle == null ? "" : subtitle, 12, p.faint, false);
        subtitleView.setPadding(0, Ui.dp(activity, 5), 0, 0);
        subtitleView.setVisibility(subtitle == null || subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        titles.addView(subtitleView);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView close = Ui.iconButton(activity, R.drawable.ic_close, p.faint, p, false);
        close.setContentDescription("关闭");
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close);
        card.addView(head);

        MaxScrollView scroll = new MaxScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(activity, 20), 0, Ui.dp(activity, 20), Ui.dp(activity, 8));
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        footer = new FooterLayout(activity);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 10),
                Ui.dp(activity, 20), Ui.dp(activity, 20));
        card.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(outside);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setDimAmount(0.52f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setWindowAnimations(R.style.BottomSheetAnimation);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        }
        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
    }

    public Palette palette() {
        return p;
    }

    public Dialog dialog() {
        return dialog;
    }

    public LinearLayout content() {
        return content;
    }

    public LinearLayout footer() {
        return footer;
    }

    public void setTitle(String title) {
        titleView.setText(title);
    }

    public void setSubtitle(String subtitle) {
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(subtitle == null || subtitle.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void add(View view) {
        content.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void add(View view, LinearLayout.LayoutParams lp) {
        content.addView(view, lp);
    }

    public void gap(int dp) {
        content.addView(Ui.spacer(activity, dp));
    }

    public TextView label(String value) {
        TextView tv = Ui.text(activity, value, 12, p.sub, true);
        tv.setPadding(0, Ui.dp(activity, 13), 0, Ui.dp(activity, 7));
        content.addView(tv);
        return tv;
    }

    public void divider() {
        View line = Ui.divider(activity, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 1));
        lp.topMargin = Ui.dp(activity, 14);
        lp.bottomMargin = Ui.dp(activity, 10);
        content.addView(line, lp);
    }

    public void addFooter(View view) {
        footer.addView(view);
    }

    public void addFooter(View view, LinearLayout.LayoutParams lp) {
        footer.addView(view, lp);
    }

    public void actions(View left, View right) {
        LinearLayout row = Ui.row(activity);
        if (left != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = Ui.dp(activity, 8);
            row.addView(left, lp);
        }
        if (right != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.leftMargin = Ui.dp(activity, 8);
            row.addView(right, lp);
        }
        footer.addView(row);
    }

    public void show() {
        dialog.show();
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    public void dismiss() {
        try {
            dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    public static Sheet confirm(Activity activity, Palette p, String title, String message,
                                String confirmLabel, boolean danger, Runnable confirmed) {
        Sheet sheet = new Sheet(activity, p, title, null);
        TextView text = Ui.text(activity, message, 14, p.sub, false);
        text.setLineSpacing(Ui.dp(activity, 4), 1f);
        text.setPadding(0, Ui.dp(activity, 4), 0, Ui.dp(activity, 10));
        sheet.add(text);
        TextView cancel = Ui.secondaryButton(activity, "取消", p);
        cancel.setOnClickListener(v -> sheet.dismiss());
        TextView ok = danger ? Ui.dangerButton(activity, confirmLabel, p)
                : Ui.primaryButton(activity, confirmLabel, p);
        ok.setOnClickListener(v -> {
            sheet.dismiss();
            if (confirmed != null) confirmed.run();
        });
        sheet.actions(cancel, ok);
        sheet.show();
        return sheet;
    }

    public static Sheet alert(Activity activity, Palette p, String title, String message) {
        Sheet sheet = new Sheet(activity, p, title, null);
        TextView text = Ui.text(activity, message, 14, p.sub, false);
        text.setLineSpacing(Ui.dp(activity, 4), 1f);
        sheet.add(text);
        TextView ok = Ui.primaryButton(activity, "知道了", p);
        ok.setOnClickListener(v -> sheet.dismiss());
        sheet.footer().addView(ok);
        sheet.show();
        return sheet;
    }

    /** Footer children are action buttons; by default they should stretch. */
    private static class FooterLayout extends LinearLayout {
        FooterLayout(Context context) {
            super(context);
        }

        @Override
        public void addView(View child) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            super.addView(child, lp);
        }
    }

    private static class MaxScrollView extends ScrollView {
        MaxScrollView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int height = getResources().getDisplayMetrics().heightPixels;
            int max = Math.round(height * 0.64f);
            int mode = MeasureSpec.AT_MOST;
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max, mode));
        }
    }
}
