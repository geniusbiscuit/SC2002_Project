package restaurantrush.entity;

import restaurantrush.util.Money;

/**
 * Happy Hour: a pricing policy the manager may switch on once per game, at the start of a turn.
 * It lasts for that turn and the next. While it is active:
 * <ul>
 *   <li>orders taken get 20% off their bill (applied after any customer discount, locked when the order is taken)</li>
 *   <li>customers still waiting for a table or food recover 15 satisfaction at turn start (maximum 100)</li>
 * </ul>
 */
public class HappyHour {

    public static final int DURATION_TURNS = 2;
    public static final int DISCOUNT_PERCENT = 20;
    public static final int RECOVERY_POINTS = 15;

    private final boolean enabled;
    private boolean used;
    private int remainingTurns;

    public HappyHour(boolean enabled) {
        this.enabled = enabled;
    }

    /** Is Happy Hour part of this game at all? (Off in the Stage 1 and 2 settings.) */
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isUsed() {
        return used;
    }

    /** Can the manager still switch it on? Enabled and not yet used this game. */
    public boolean isAvailable() {
        return enabled && !used;
    }

    public boolean isActive() {
        return remainingTurns > 0;
    }

    /** Turns left including the current one (2 on the turn it is switched on, then 1, then 0). */
    public int getRemainingTurns() {
        return remainingTurns;
    }

    public void activate() {
        if (!enabled) {
            throw new InvalidActionException("Happy Hour is not part of this game setting.");
        }
        if (used) {
            throw new InvalidActionException("Happy Hour can only be used once per game.");
        }
        used = true;
        remainingTurns = DURATION_TURNS;
    }

    /** Called at the end of every turn. */
    public void endTurn() {
        if (remainingTurns > 0) {
            remainingTurns--;
        }
    }

    /** The bill after Happy Hour: 80% while active, unchanged otherwise. */
    public int priceFor(int billCents) {
        return isActive() ? Money.percentOf(billCents, 100 - DISCOUNT_PERCENT) : billCents;
    }
}
