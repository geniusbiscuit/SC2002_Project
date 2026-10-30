package restaurantrush.entity;

/**
 * A staff member. Staff time is the scarce resource of the game: each member can complete at
 * most one task per turn. The flag is only set after an action fully succeeds, so a rejected
 * choice does not use up the task.
 */
public abstract class Staff {

    private final String role;
    private boolean actedThisTurn;

    protected Staff(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }

    public boolean hasActed() {
        return actedThisTurn;
    }

    /** Called by the restaurant at the beginning of every turn. */
    public void startTurn() {
        actedThisTurn = false;
    }

    /** Choosing "Wait" is a legitimate use of the turn. */
    public void rest() {
        ensureAvailable();
        markActed();
    }

    protected void ensureAvailable() {
        if (actedThisTurn) {
            throw new InvalidActionException("The " + role + " has already acted this turn.");
        }
    }

    protected void markActed() {
        actedThisTurn = true;
    }
}
