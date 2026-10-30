package restaurantrush.boundary.gui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import restaurantrush.control.GameConfig;

/**
 * Entry point of the windowed game. Same settings as the console game:
 * no argument = Stage 3 (Host, combos, Happy Hour); {@code --classic} = Stage 2; {@code --regular-only} = Stage 1.
 */
public class GuiMain {
    public static void main(String[] args) {
        GameConfig config = GameConfig.fullSetting();
        for (String arg : args) {
            if (arg.equals("--classic")) {
                config = GameConfig.baseSetting();
            } else if (arg.equals("--regular-only")) {
                config = GameConfig.regularOnlySetting();
            }
        }
        final GameConfig chosen = config;
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
                // the default look is fine
            }
            new GameWindow(chosen).setVisible(true);
        });
    }
}
