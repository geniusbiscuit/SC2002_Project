package restaurantrush.control;

/** Outcome of one manager choice: success, or a reason it failed (staff task is not used up). */
public record ActionResult(boolean success, String message) {

    public static ActionResult ok(String message) {
        return new ActionResult(true, message);
    }

    public static ActionResult fail(String message) {
        return new ActionResult(false, message);
    }
}
