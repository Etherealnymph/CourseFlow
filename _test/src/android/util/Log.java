package android.util;

public final class Log {
    public static int e(String tag, String msg, Throwable t) {
        System.err.println(tag + ": " + msg + " " + t);
        return 0;
    }

    public static int e(String tag, String msg) {
        System.err.println(tag + ": " + msg);
        return 0;
    }
}
