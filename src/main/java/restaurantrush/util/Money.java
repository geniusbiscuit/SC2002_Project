package restaurantrush.util;

/**
 * Helpers for money. All amounts in the game are stored as integer cents so that
 * no floating-point rounding can ever creep into revenue.
 */
public final class Money {

    private Money() {
        // static helpers only
    }

    /** Formats cents as dollars with two decimals, e.g. 2430 -> "$24.30". */
    public static String format(int cents) {
        return String.format("$%d.%02d", cents / 100, Math.abs(cents % 100));
    }

    /** Returns {@code percent}% of {@code cents}, rounded half-up to the nearest cent. */
    public static int percentOf(int cents, int percent) {
        return (cents * percent + 50) / 100;
    }
}
