package com.etherealnymph.courseflow;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class Ui {

    public static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private Ui() {}

    // ------------------------------------------------------------- dimensions

    public static int dp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                c.getResources().getDisplayMetrics()));
    }

    public static void pad(View v, int left, int top, int right, int bottom) {
        v.setPadding(dp(v.getContext(), left), dp(v.getContext(), top),
                dp(v.getContext(), right), dp(v.getContext(), bottom));
    }

    public static void margin(View v, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams raw = v.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
            lp.setMargins(dp(v.getContext(), left), dp(v.getContext(), top),
                    dp(v.getContext(), right), dp(v.getContext(), bottom));
            v.setLayoutParams(lp);
        }
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static View spacer(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, heightDp)));
        return v;
    }

    public static View hspacer(Context c, int widthDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, widthDp), 1));
        return v;
    }

    // ----------------------------------------------------------------- text

    public static TextView text(Context c, String value, float size, int color, boolean medium) {
        TextView tv = new TextView(c);
        tv.setText(value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        tv.setTextColor(color);
        tv.setTypeface(medium ? MEDIUM : REGULAR);
        tv.setIncludeFontPadding(false);
        tv.setLineSpacing(dp(c, 3), 1f);
        return tv;
    }

    public static TextView title(Context c, String value, Palette p) {
        TextView tv = text(c, value, 27, p.ink, true);
        tv.setLetterSpacing(0.01f);
        return tv;
    }

    public static TextView eyebrow(Context c, String value, Palette p) {
        TextView tv = text(c, value, 10, p.faint, true);
        tv.setLetterSpacing(0.19f);
        return tv;
    }

    public static TextView section(Context c, String value, Palette p) {
        TextView tv = text(c, value, 12, p.sub, true);
        tv.setLetterSpacing(0.08f);
        return tv;
    }

    public static TextView caption(Context c, String value, Palette p) {
        return text(c, value, 11.5f, p.faint, false);
    }

    public static TextView body(Context c, String value, Palette p) {
        return text(c, value, 14, p.sub, false);
    }

    // -------------------------------------------------------------- surfaces

    public static GradientDrawable bg(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp);
        return d;
    }

    public static GradientDrawable bg(Context c, int color, float radiusDp) {
        return bg(color, dp(c, radiusDp));
    }

    public static GradientDrawable stroke(Context c, int fill, int stroke, float widthDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(Math.max(1, dp(c, widthDp)), stroke);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static Drawable ripple(Context c, Drawable shape, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), shape, null);
    }

    public static Drawable pressable(Context c, int fill, float radius, int rippleColor) {
        return ripple(c, bg(c, fill, radius), rippleColor);
    }

    public static LinearLayout card(Context c, Palette p) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(stroke(c, p.surface, p.line, 1, 22));
        box.setElevation(dp(c, 0.5f));
        box.setClipToOutline(false);
        return box;
    }

    public static LinearLayout row(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static LinearLayout column(Context c) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    public static View divider(Context c, Palette p) {
        View v = new View(c);
        v.setBackgroundColor(p.line);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return v;
    }

    public static View verticalDivider(Context c, Palette p) {
        View v = new View(c);
        v.setBackgroundColor(p.line);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 1), ViewGroup.LayoutParams.MATCH_PARENT));
        return v;
    }

    // ---------------------------------------------------------------- icons

    public static ImageView icon(Context c, int res, int sizeDp, int tint) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setColorFilter(tint);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    public static ImageView iconButton(Context c, int res, int tint, Palette p, boolean filled) {
        int size = 40;
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setColorFilter(tint);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, size), dp(c, size)));
        iv.setBackground(pressable(c, filled ? p.surfaceAlt : Color.TRANSPARENT,
                dp(c, 13), Palette.alpha(tint, 26)));
        iv.setClickable(true);
        iv.setFocusable(true);
        return iv;
    }

    public static TextView textButton(Context c, String label, int color, Palette p) {
        TextView tv = text(c, label, 13, color, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 12), dp(c, 9), dp(c, 12), dp(c, 9));
        tv.setBackground(pressable(c, Color.TRANSPARENT, dp(c, 10), Palette.alpha(color, 26)));
        tv.setClickable(true);
        return tv;
    }

    public static TextView primaryButton(Context c, String label, Palette p) {
        TextView tv = text(c, label, 15, p.onPrimary, true);
        tv.setGravity(Gravity.CENTER);
        tv.setMinHeight(dp(c, 50));
        tv.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
        tv.setBackground(pressable(c, p.primary, dp(c, 16), Palette.alpha(p.onPrimary, 42)));
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    public static TextView secondaryButton(Context c, String label, Palette p) {
        TextView tv = text(c, label, 14, p.ink, true);
        tv.setGravity(Gravity.CENTER);
        tv.setMinHeight(dp(c, 46));
        tv.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        tv.setBackground(pressable(c, p.surfaceAlt, dp(c, 14), Palette.alpha(p.ink, 22)));
        tv.setClickable(true);
        return tv;
    }

    public static TextView dangerButton(Context c, String label, Palette p) {
        TextView tv = text(c, label, 14, p.danger, true);
        tv.setGravity(Gravity.CENTER);
        tv.setMinHeight(dp(c, 46));
        tv.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        tv.setBackground(pressable(c, p.surfaceAlt, dp(c, 14), Palette.alpha(p.danger, 28)));
        tv.setClickable(true);
        return tv;
    }

    public static TextView pill(Context c, String label, int color, Palette p) {
        TextView tv = text(c, label, 11.5f, color, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 9), dp(c, 4), dp(c, 9), dp(c, 4));
        tv.setBackground(bg(c, Palette.blend(color, p.surface, p.dark ? 0.20f : 0.12f), dp(c, 20)));
        return tv;
    }

    public static TextView chip(Context c, String label, Palette p, boolean selected) {
        TextView tv = text(c, label, 12.5f, selected ? p.primary : p.sub, selected);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 13), dp(c, 8), dp(c, 13), dp(c, 8));
        tv.setBackground(pressable(c, selected ? p.primarySoft : p.surfaceAlt, dp(c, 20),
                Palette.alpha(p.primary, 28)));
        tv.setClickable(true);
        return tv;
    }

    public static void setChipSelected(TextView tv, boolean selected, Palette p) {
        tv.setTextColor(selected ? p.primary : p.sub);
        tv.setTypeface(selected ? MEDIUM : REGULAR);
        tv.setBackground(pressable(tv.getContext(), selected ? p.primarySoft : p.surfaceAlt,
                dp(tv.getContext(), 20), Palette.alpha(p.primary, 28)));
    }

    // ---------------------------------------------------------------- input

    public static Drawable inputBackground(Context c, Palette p) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                stroke(c, p.surfaceAlt, p.primary, 1.5f, 14));
        states.addState(new int[]{},
                stroke(c, p.surfaceAlt, p.line, 1, 14));
        return states;
    }

    public static EditText input(Context c, String hint, Palette p, boolean multiline) {
        EditText et = new EditText(c);
        et.setHint(hint);
        et.setHintTextColor(p.faint);
        et.setTextColor(p.ink);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        et.setTypeface(REGULAR);
        et.setBackground(inputBackground(c, p));
        et.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        et.setIncludeFontPadding(false);
        et.setMinHeight(dp(c, 48));
        if (multiline) {
            et.setGravity(Gravity.TOP | Gravity.START);
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            et.setMinLines(4);
            et.setMaxLines(14);
        } else {
            et.setSingleLine(true);
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            et.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        }
        return et;
    }

    public static EditText numberInput(Context c, String hint, Palette p) {
        EditText et = input(c, hint, p, false);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        return et;
    }

    public static EditText passwordInput(Context c, String hint, Palette p) {
        EditText et = input(c, hint, p, false);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setSingleLine(true);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        return et;
    }

    public static LinearLayout searchBar(Context c, String hint, Palette p) {
        LinearLayout bar = row(c);
        bar.setBackground(stroke(c, p.surfaceAlt, p.line, 1, 15));
        bar.setPadding(dp(c, 13), dp(c, 0), dp(c, 8), dp(c, 0));
        bar.setMinimumHeight(dp(c, 46));
        ImageView search = icon(c, R.drawable.ic_search, 18, p.faint);
        bar.addView(search);
        EditText et = new EditText(c);
        et.setHint(hint);
        et.setHintTextColor(p.faint);
        et.setTextColor(p.ink);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        et.setBackgroundColor(Color.TRANSPARENT);
        et.setSingleLine(true);
        et.setPadding(dp(c, 9), 0, dp(c, 9), 0);
        et.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(c, 46), 1);
        et.setLayoutParams(lp);
        bar.addView(et);
        bar.setTag(et);
        return bar;
    }

    public static EditText searchField(LinearLayout bar) {
        if (bar == null || !(bar.getTag() instanceof EditText)) return null;
        return (EditText) bar.getTag();
    }

    // --------------------------------------------------------------- layout

    public static LinearLayout sectionHeader(Context c, String label, String action, Palette p,
                                             View.OnClickListener onClick) {
        LinearLayout row = row(c);
        TextView title = section(c, label, p);
        row.addView(title, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (action != null) {
            TextView a = textButton(c, action, p.primary, p);
            a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            a.setOnClickListener(onClick);
            row.addView(a);
        }
        return row;
    }

    public static LinearLayout emptyState(Context c, int iconRes, String title, String subtitle, Palette p) {
        LinearLayout box = column(c);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(c, 20), dp(c, 34), dp(c, 20), dp(c, 34));
        FrameLayout halo = new FrameLayout(c);
        halo.setBackground(bg(c, p.primarySoft, dp(c, 28)));
        halo.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 56), dp(c, 56)));
        ImageView iv = icon(c, iconRes, 26, p.primary);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(c, 26), dp(c, 26));
        ilp.gravity = Gravity.CENTER;
        halo.addView(iv, ilp);
        box.addView(halo);
        TextView t = text(c, title, 15, p.ink, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(c, 14), 0, 0);
        box.addView(t);
        TextView s = text(c, subtitle, 12.5f, p.faint, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(dp(c, 18), dp(c, 7), dp(c, 18), 0);
        box.addView(s);
        return box;
    }

    public static ScrollView screenScroll(Context c, Palette p) {
        ScrollView sc = new ScrollView(c);
        sc.setFillViewport(true);
        sc.setClipToPadding(false);
        sc.setVerticalScrollBarEnabled(false);
        sc.setBackgroundColor(Color.TRANSPARENT);
        return sc;
    }

    public static LinearLayout screenBody(Context c) {
        LinearLayout body = column(c);
        body.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), dp(c, 28));
        return body;
    }

    public static void toast(Context c, String message) {
        Toast.makeText(c, message, Toast.LENGTH_SHORT).show();
    }

    // --------------------------------------------------------------- window

    public static void styleSystemBars(Activity activity, Palette p) {
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(p.dark ? 0 : mask, mask);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!p.dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    public static int blend(int a, int b, float ratio) {
        return Palette.blend(a, b, ratio);
    }
}
