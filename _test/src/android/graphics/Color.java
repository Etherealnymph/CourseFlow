package android.graphics;

public final class Color {
    public static final int TRANSPARENT = 0;

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int red(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int green(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int blue(int c) {
        return c & 0xFF;
    }

    public static int alpha(int c) {
        return (c >>> 24) & 0xFF;
    }

    public static int parseColor(String s) {
        if (s == null) throw new IllegalArgumentException("null");
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 6) return 0xFF000000 | Integer.parseInt(s, 16);
        if (s.length() == 8) return (int) Long.parseLong(s, 16);
        throw new IllegalArgumentException("bad color " + s);
    }
}
