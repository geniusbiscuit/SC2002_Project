package restaurantrush.entity;

/**
 * Thrown when a manager choice breaks a game rule (wrong customer state, staff already
 * acted, no free table, ...). The message is shown to the manager, who then chooses again.
 * The staff member is only marked as "acted" after an action has fully succeeded, so a
 * rejected choice never uses up a staff task.
 */
public class InvalidActionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidActionException(String message) {
        super(message);
    }
}
