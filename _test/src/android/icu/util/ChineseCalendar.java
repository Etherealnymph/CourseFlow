package android.icu.util;

/** Minimal test stub; the real app uses Android's ICU implementation. */
public class ChineseCalendar {
    public static final int MONTH = 2;
    public static final int DAY_OF_MONTH = 5;
    public static final int IS_LEAP_MONTH = 22;
    private long time;

    public void setTimeInMillis(long time) {
        this.time = time;
    }

    public int get(int field) {
        return 0;
    }
}
