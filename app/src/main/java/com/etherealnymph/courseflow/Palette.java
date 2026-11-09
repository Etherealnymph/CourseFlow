package com.etherealnymph.courseflow;

import android.graphics.Color;

/** All colours used by the hand-built UI. Two complete palettes keep the app
 *  legible in both daylight and midnight, without depending on AppCompat. */
public final class Palette {

    public final boolean dark;

    public final int bg;
    public final int bgAlt;
    public final int surface;
    public final int surfaceAlt;
    public final int ink;
    public final int sub;
    public final int faint;
    public final int line;
    public final int primary;
    public final int primarySoft;
    public final int onPrimary;
    public final int accent;
    public final int accentSoft;
    public final int gold;
    public final int blue;
    public final int purple;
    public final int danger;

    public static final int[] COURSE_COLORS = {
            0xFF0F7A64, 0xFF356C8A, 0xFFC08A2E, 0xFF7563A8,
            0xFFE1582E, 0xFF2B7A78, 0xFF8A6D3B, 0xFF9B4D6B
    };

    public static final int[] NOTE_COLORS = {
            0xFF0F7A64, 0xFF356C8A, 0xFFC08A2E, 0xFF7563A8,
            0xFFE1582E, 0xFF6D7D45
    };

    public Palette(boolean dark) {
        this.dark = dark;
        if (dark) {
            bg = 0xFF0C1211;
            bgAlt = 0xFF101917;
            surface = 0xFF16201D;
            surfaceAlt = 0xFF1B2723;
            ink = 0xFFEAF1ED;
            sub = 0xFFA4B2AB;
            faint = 0xFF70807A;
            line = 0xFF29352F;
            primary = 0xFF6AD6B9;
            primarySoft = 0xFF1D3B34;
            onPrimary = 0xFF08201A;
            accent = 0xFFFF8B66;
            accentSoft = 0xFF3A241E;
            gold = 0xFFE7B567;
            blue = 0xFF83B6D9;
            purple = 0xFFB5A4E4;
            danger = 0xFFFF8C8C;
        } else {
            bg = 0xFFF4F1EA;
            bgAlt = 0xFFEAE5DA;
            surface = 0xFFFFFFFF;
            surfaceAlt = 0xFFF8F5EE;
            ink = 0xFF17201D;
            sub = 0xFF5F6D66;
            faint = 0xFF98A29D;
            line = 0xFFE3DDD1;
            primary = 0xFF0F7A64;
            primarySoft = 0xFFDDEFE9;
            onPrimary = 0xFFFFFFFF;
            accent = 0xFFE1582E;
            accentSoft = 0xFFFBE5DC;
            gold = 0xFFA97922;
            blue = 0xFF356C8A;
            purple = 0xFF7563A8;
            danger = 0xFFC7443E;
        }
    }

    public int tinted(int color, float alpha) {
        int bgColor = dark ? surface : surface;
        return blend(color, bgColor, alpha);
    }

    public int soft(int color) {
        return tinted(color, dark ? 0.24f : 0.15f);
    }

    public static int blend(int fg, int bg, float alpha) {
        float a = Math.max(0f, Math.min(1f, alpha));
        int r = Math.round(Color.red(fg) * a + Color.red(bg) * (1f - a));
        int g = Math.round(Color.green(fg) * a + Color.green(bg) * (1f - a));
        int b = Math.round(Color.blue(fg) * a + Color.blue(bg) * (1f - a));
        return Color.rgb(r, g, b);
    }

    public static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}
