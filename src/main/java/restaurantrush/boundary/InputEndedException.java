package restaurantrush.boundary;

/** Thrown when the input stream ends before the game does (for example a too-short script). */
public class InputEndedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InputEndedException() {
        super("Input ended before the game finished.");
    }
}
