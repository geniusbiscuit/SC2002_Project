package restaurantrush.boundary;

import restaurantrush.control.GameConfig;
import restaurantrush.control.GameController;

/**
 * Entry point. With no argument it starts the Stage 3 game (mixed crowd, Host, combos, Happy Hour).
 * Earlier settings can be replayed exactly:
 * <ul>
 *   <li>{@code --classic}: the Stage 2 base setting (VIP and Critic, no Host, no combos, no Happy Hour)</li>
 *   <li>{@code --regular-only}: the Stage 1 setting (six Regular customers)</li>
 * </ul>
 */
public class Main {

    public static void main(String[] args) {
        GameConfig config = GameConfig.fullSetting();
        for (String arg : args) {
            if (arg.equals("--classic")) {
                config = GameConfig.baseSetting();
            } else if (arg.equals("--regular-only")) {
                config = GameConfig.regularOnlySetting();
            }
        }
        GameController game = new GameController(config);
        // When input is piped (a scripted run) echo it so the transcript shows every choice.
        boolean scripted = System.console() == null;
        try {
            new ConsoleUI(game, System.in, System.out, scripted).run();
        } catch (InputEndedException e) {
            System.out.println();
            System.out.println(e.getMessage() + " (game aborted at turn " + game.getTurn() + ")");
        }
    }
}
