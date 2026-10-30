package restaurantrush.boundary.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import restaurantrush.control.ActionResult;
import restaurantrush.control.GameConfig;
import restaurantrush.control.GameController;
import restaurantrush.control.GameController.Phase;
import restaurantrush.control.GameResult;
import restaurantrush.control.TurnSummary;
import restaurantrush.entity.Customer;
import restaurantrush.entity.HappyHour;
import restaurantrush.entity.Order;
import restaurantrush.entity.Restaurant;
import restaurantrush.util.Money;

/**
 * The windowed boundary class. Like {@code ConsoleUI} it holds no game rules: it asks the
 * {@link GameController} to do what the manager clicked and shows what comes back. The turn is the
 * same: Host (Stage 3), Waiter, Chef, then the automatic end of turn.
 */
final class GameWindow extends JFrame {

    private static final Color NAVY = new Color(0x1B2430);
    private static final Color PANEL = new Color(0x232F3E);
    private static final Color INK = new Color(0xECF0F1);
    private static final Color MUTED = new Color(0x9FB0C0);
    private static final Color GOLD = new Color(0xF4B942);
    private static final Color GREEN = new Color(0x2ECC71);
    private static final Color RED = new Color(0xFF6B5E);

    private GameConfig config;
    private GameController game;
    private final RestaurantView view = new RestaurantView();

    private final JPanel hud = new JPanel(new GridLayout(1, 5, 10, 0));
    private final JLabel turnChip = chip();
    private final JLabel revenueChip = chip();
    private final JLabel paidChip = chip();
    private final JLabel satisfactionChip = chip();
    private final JLabel happyChip = chip();

    private final JPanel stepRow = new JPanel(new GridLayout(1, 4, 6, 0));
    private final JLabel[] stepLabels = new JLabel[4];
    private final JLabel prompt = new JLabel();
    private final JLabel hint = new JLabel();
    private final JPanel actions = new JPanel();
    private final JTextPane log = new JTextPane();

    /** True while the game is animating between turns: clicks are ignored. */
    private boolean busy;
    /** True once any staff member has acted this turn (Happy Hour must be chosen before that). */
    private boolean acted;
    private Timer pending;
    /** The clickable choices currently shown, in order (keys 1-9 press them, W presses the "waits" one). */
    private final List<PillButton> choices = new ArrayList<>();

    GameWindow(GameConfig config) {
        super("Restaurant Rush");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(NAVY);
        setLayout(new BorderLayout(0, 0));
        add(buildHud(), BorderLayout.NORTH);
        add(view, BorderLayout.CENTER);
        add(buildSide(), BorderLayout.EAST);
        add(buildLog(), BorderLayout.SOUTH);
        setJMenuBar(buildMenu());
        setIconImage(appIcon());
        installKeys();
        pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(getWidth(), screen.width - 40), Math.min(getHeight(), screen.height - 80));
        setLocationRelativeTo(null);
        startNewGame(config);
    }

    // ---- package access for the screenshot tool -----------------------------------------------------------

    GameController game() {
        return game;
    }

    RestaurantView view() {
        return view;
    }

    boolean isBusy() {
        return busy;
    }

    List<JButton> actionButtons() {
        List<JButton> found = new ArrayList<>();
        for (Component c : actions.getComponents()) {
            if (c instanceof JButton b) {
                found.add(b);
            }
        }
        return found;
    }

    // ---- building the window -----------------------------------------------------------------------------------

    private JPanel buildHud() {
        hud.setBackground(NAVY);
        hud.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        for (JLabel l : List.of(turnChip, revenueChip, paidChip, satisfactionChip, happyChip)) {
            hud.add(l);
        }
        return hud;
    }

    private static JLabel chip() {
        JLabel l = new JLabel("", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Art.smooth(g2);
                g2.setColor(getBackground());
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 16, 16));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setOpaque(false);
        l.setBackground(PANEL);
        l.setForeground(INK);
        l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        l.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return l;
    }

    private JPanel buildSide() {
        JPanel side = new JPanel(new BorderLayout(0, 8));
        side.setBackground(NAVY);
        side.setBorder(BorderFactory.createEmptyBorder(0, 6, 8, 12));
        side.setPreferredSize(new Dimension(350, 600));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(NAVY);

        stepRow.setBackground(NAVY);
        String[] names = {"Host", "Waiter", "Chef", "Service"};
        for (int i = 0; i < 4; i++) {
            stepLabels[i] = new JLabel(names[i], SwingConstants.CENTER);
            stepLabels[i].setOpaque(true);
            stepLabels[i].setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            stepLabels[i].setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
            stepRow.add(stepLabels[i]);
        }
        stepRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(stepRow);
        top.add(Box.createVerticalStrut(10));

        prompt.setForeground(INK);
        prompt.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        prompt.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(prompt);
        hint.setForeground(MUTED);
        hint.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(hint);
        side.add(top, BorderLayout.NORTH);

        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.setBackground(NAVY);
        JScrollPane scroll = new JScrollPane(actions);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(NAVY);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        side.add(scroll, BorderLayout.CENTER);
        return side;
    }

    private JScrollPane buildLog() {
        log.setEditable(false);
        log.setBackground(new Color(0x141B24));
        log.setForeground(INK);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setMargin(new Insets(6, 10, 6, 10));
        JScrollPane sp = new JScrollPane(log);
        sp.setPreferredSize(new Dimension(800, 130));
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x34445A)));
        return sp;
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu game = new JMenu("Game");
        game.add(menuItem("New game - Stage 3 (Host, combos, Happy Hour)", () -> startNewGame(GameConfig.fullSetting())));
        game.add(menuItem("New game - Stage 2 (VIP + Critic)", () -> startNewGame(GameConfig.baseSetting())));
        game.add(menuItem("New game - Stage 1 (Regular customers only)",
                () -> startNewGame(GameConfig.regularOnlySetting())));
        game.addSeparator();
        game.add(menuItem("Quit", () -> System.exit(0)));
        JMenu help = new JMenu("Help");
        help.add(menuItem("How to play", this::showHelp));
        bar.add(game);
        bar.add(help);
        return bar;
    }

    private static JMenuItem menuItem(String text, Runnable r) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> r.run());
        return item;
    }

    private static java.awt.image.BufferedImage appIcon() {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(64, 64,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Art.smooth(g);
        g.setColor(new Color(0xE74C3C));
        g.fill(new Ellipse2D.Double(2, 2, 60, 60));
        Art.person(g, 32, 66, 0.72, Art.staffLook("Chef"), Art.Pose.idle(0.3, -1, 1));
        g.dispose();
        return img;
    }

    /** Keyboard play: 1-9 choose the numbered button, W makes the current staff member wait. */
    private void installKeys() {
        javax.swing.JRootPane root = getRootPane();
        for (int i = 1; i <= 9; i++) {
            final int index = i - 1;
            root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(javax.swing.KeyStroke.getKeyStroke(Character.forDigit(i, 10)), "choice" + i);
            root.getActionMap().put("choice" + i, new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (!busy && index < choices.size() && choices.get(index).isEnabled()) {
                        choices.get(index).doClick();
                    }
                }
            });
        }
        root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke('w'), "wait");
        root.getActionMap().put("wait", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (busy) {
                    return;
                }
                for (PillButton b : choices) {
                    if (b.getText().endsWith("waits") && b.isEnabled()) {
                        b.doClick();
                        return;
                    }
                }
            }
        });
    }

    // ---- game flow -----------------------------------------------------------------------------------------------

    void startNewGame(GameConfig newConfig) {
        if (pending != null) {
            pending.stop();
        }
        this.config = newConfig;
        this.game = new GameController(newConfig);
        this.busy = false;
        this.acted = false;
        log.setText("");
        view.bind(game);
        say("RESTAURANT RUSH - you are the manager. Each staff member can do ONE task per turn.", GOLD, true);
        say("Tip: click a button, or press 1-9 for the numbered choices and W to make the staff member wait.", MUTED,
                false);
        say("Goal after turn " + config.getMaxTurns() + ": " + config.getRequiredPaidCustomers()
                + "+ paid customers, " + Money.format(config.getRequiredRevenueCents())
                + "+ revenue, average satisfaction " + config.getRequiredAverageSatisfaction() + "+.", MUTED, false);
        beginTurn();
    }

    private void beginTurn() {
        List<String> arrivals = game.beginTurn();
        acted = false;
        view.onTurnStart();
        say("", MUTED, false);
        say("=== TURN " + game.getTurn() + " of " + config.getMaxTurns() + " ===", GOLD, true);
        for (String a : arrivals) {
            say(a, INK, false);
        }
        view.sync();
        refresh();
    }

    private void perform(String role, int customerId, Supplier<ActionResult> action) {
        if (busy) {
            return;
        }
        ActionResult result = action.get();
        if (!result.success()) {
            say("Not allowed: " + result.message(), RED, false);
            refresh();
            return;
        }
        say(result.message(), INK, false);
        acted = true;
        if (customerId > 0) {
            view.onAction(role, customerId);
        } else {
            view.onWait(role);
        }
        view.sync();
        if (game.getPhase() == Phase.READY_TO_END) {
            busy = true;
            refresh();
            after(1100, this::finishTurn);
        } else {
            refresh();
        }
    }

    private void finishTurn() {
        TurnSummary summary = game.endTurn();
        for (String e : summary.events()) {
            boolean bad = e.contains("leaves without paying");
            say(e, bad ? RED : new Color(0x82E0AA), false);
        }
        view.sync();
        refresh();
        if (game.isGameOver()) {
            after(2200, this::showResult);
        } else {
            after(1500, () -> {
                busy = false;
                beginTurn();
            });
        }
    }

    private void after(int ms, Runnable r) {
        pending = new Timer(ms, e -> r.run());
        pending.setRepeats(false);
        pending.start();
    }

    private void showResult() {
        GameResult r = game.getResult();
        OptionalDouble avg = r.averageSatisfaction();
        boolean win = r.outcome() == GameResult.Outcome.VICTORY;
        say("", MUTED, false);
        say("=== " + (win ? "VICTORY" : "DEFEAT") + " ===", win ? GREEN : RED, true);
        String html = "<html><body style='width:300px'><h2 style='color:" + (win ? "#1E8449" : "#C0392B") + "'>"
                + (win ? "Victory! The restaurant is a hit." : "Defeat - the restaurant did not make it.") + "</h2>"
                + line("Paid customers", r.paidCount() + " (need " + config.getRequiredPaidCustomers() + ")",
                        r.paidTargetMet())
                + line("Revenue", Money.format(r.revenueCents()) + " (need "
                        + Money.format(config.getRequiredRevenueCents()) + ")", r.revenueTargetMet())
                + line("Average satisfaction", (avg.isPresent() ? String.format("%.1f", avg.getAsDouble()) : "-")
                        + " (need " + config.getRequiredAverageSatisfaction() + ")", r.satisfactionTargetMet())
                + "<p>Customers who left unhappy: " + r.unhappyDepartures() + "</p></body></html>";
        say("Paid " + r.paidCount() + ", revenue " + Money.format(r.revenueCents()) + ", unhappy departures "
                + r.unhappyDepartures() + ".", INK, false);
        int choice = JOptionPane.showOptionDialog(this, html, win ? "You won!" : "Game over",
                JOptionPane.DEFAULT_OPTION, win ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE, null,
                new String[] {"Play again", "Quit"}, "Play again");
        if (choice == 0) {
            startNewGame(config);
        } else if (choice == 1) {
            System.exit(0);
        }
    }

    private static String line(String label, String value, boolean ok) {
        return "<p>" + (ok ? "<b style='color:#1E8449'>[met]</b> " : "<b style='color:#C0392B'>[missed]</b> ")
                + label + ": " + value + "</p>";
    }

    private void showHelp() {
        JOptionPane.showMessageDialog(this, "<html><body style='width:380px'>"
                + "<h3>How to play</h3>"
                + "<p>You manage the restaurant for " + config.getMaxTurns() + " turns. Every turn each staff member "
                + "can do <b>one</b> task, in this order: Host (Stage 3) &rarr; Waiter &rarr; Chef. Then customers eat, "
                + "pay, and the unserved ones lose patience.</p>"
                + "<ul><li><b>Seat</b> a queued guest at a free table (Host or Waiter)</li>"
                + "<li><b>Take order</b> from a seated guest (Waiter)</li>"
                + "<li><b>Cook</b> one unit of a dish (Chef). Bigger dishes need more units.</li>"
                + "<li><b>Serve</b> a READY dish (Waiter) - not on the turn it was finished.</li>"
                + "<li>The Cashier collects payment automatically, one guest per turn.</li></ul>"
                + "<p>A customer who hits 0 satisfaction leaves without paying. Hover a guest in the scene for details; "
                + "hover an action button to highlight that guest.</p></body></html>", "How to play",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // ---- refreshing the controls ------------------------------------------------------------------------------------

    private void refresh() {
        refreshHud();
        refreshSteps();
        buildActions();
    }

    private void refreshHud() {
        Restaurant r = game.getRestaurant();
        int turn = Math.max(1, game.getTurn());
        turnChip.setText("TURN " + turn + " / " + config.getMaxTurns());
        revenueChip.setText("REVENUE " + Money.format(r.getRevenueCents()) + " / "
                + Money.format(config.getRequiredRevenueCents()));
        paidChip.setText("PAID " + r.getPaidCount() + " / " + config.getRequiredPaidCustomers());
        OptionalDouble avg = r.getAverageSatisfaction();
        satisfactionChip.setText("AVG SATISFACTION " + (avg.isPresent() ? String.format("%.0f", avg.getAsDouble()) : "-")
                + " / " + config.getRequiredAverageSatisfaction());
        HappyHour hh = game.getHappyHour();
        happyChip.setText(!hh.isEnabled() ? "HAPPY HOUR: not in this stage"
                : hh.isActive() ? "HAPPY HOUR ON (" + hh.getRemainingTurns() + " left)"
                : hh.isUsed() ? "HAPPY HOUR: used" : "HAPPY HOUR: ready");
        tint(revenueChip, r.getRevenueCents() >= config.getRequiredRevenueCents());
        tint(paidChip, r.getPaidCount() >= config.getRequiredPaidCustomers());
        tint(satisfactionChip, avg.isPresent() && avg.getAsDouble() >= config.getRequiredAverageSatisfaction());
        turnChip.setBackground(PANEL);
        happyChip.setBackground(hh.isActive() ? new Color(0xB9770E) : PANEL);
    }

    private static void tint(JLabel chip, boolean met) {
        chip.setBackground(met ? new Color(0x1E8449) : PANEL);
    }

    private void refreshSteps() {
        Phase ph = game.getPhase();
        boolean hostOn = game.isHostEnabled();
        stepLabels[0].setVisible(hostOn);
        Phase[] order = {Phase.HOST, Phase.WAITER, Phase.CHEF, Phase.READY_TO_END};
        for (int i = 0; i < 4; i++) {
            boolean current = ph == order[i] || (i == 3 && ph == Phase.FINISHED);
            boolean done = ph.ordinal() > order[i].ordinal() && ph != Phase.AWAITING_TURN_START;
            stepLabels[i].setBackground(current ? GOLD : done ? new Color(0x2E4A3D) : PANEL);
            stepLabels[i].setForeground(current ? Color.BLACK : done ? new Color(0x82E0AA) : MUTED);
        }
    }

    private void buildActions() {
        actions.removeAll();
        choices.clear();
        view.setHighlight(-1);
        Phase ph = game.getPhase();
        Restaurant r = game.getRestaurant();
        if (busy || ph == Phase.READY_TO_END || ph == Phase.FINISHED || ph == Phase.AWAITING_TURN_START) {
            prompt.setText(game.isGameOver() ? "That's a wrap!" : "Service in progress...");
            hint.setText("Customers eat, pay and lose patience.");
            actions.revalidate();
            actions.repaint();
            return;
        }
        addHappyHour();
        switch (ph) {
            case HOST -> {
                prompt.setText("Host: choose a task");
                hint.setText("The Host acts first and seats one guest. Waiter is next.");
                addSection("Seat a waiting guest");
                for (Customer c : r.queuedCustomers()) {
                    if (r.hasFreeTable()) {
                        addChoice(c, "Seat #" + c.getId() + " " + c.getTypeName(), "Host", c.getId(),
                                () -> game.hostSeat(c.getId()), new Color(0x2980B9));
                    }
                }
                addSection("Or");
                addChoice(null, "Host waits", "Host", 0, game::hostWait, new Color(0x5D6D7E));
            }
            case WAITER -> {
                prompt.setText("Waiter: choose a task");
                hint.setText("Serve a READY dish, take an order, or seat a guest. Chef is next.");
                List<Customer> queued = r.queuedCustomers();
                List<Customer> orders = r.customersAwaitingOrder();
                List<Order> servable = r.servableOrders(game.getTurn());
                if (!servable.isEmpty()) {
                    addSection("Serve food (it is READY)");
                    for (Order o : servable) {
                        Customer c = o.getCustomer();
                        addChoice(c, "Serve #" + c.getId() + " " + o.getItem().getName(), "Waiter", c.getId(),
                                () -> game.waiterServe(c.getId()), new Color(0x27AE60));
                    }
                }
                if (!orders.isEmpty()) {
                    addSection("Take an order");
                    for (Customer c : orders) {
                        addChoice(c, "Take order from #" + c.getId() + " " + c.getTypeName(), "Waiter", c.getId(),
                                () -> game.waiterTakeOrder(c.getId()), new Color(0xE67E22));
                    }
                }
                if (!queued.isEmpty() && r.hasFreeTable()) {
                    addSection("Seat a waiting guest");
                    for (Customer c : queued) {
                        addChoice(c, "Seat #" + c.getId() + " " + c.getTypeName(), "Waiter", c.getId(),
                                () -> game.waiterSeat(c.getId()), new Color(0x2980B9));
                    }
                }
                addSection("Or");
                addChoice(null, "Waiter waits", "Waiter", 0, game::waiterWait, new Color(0x5D6D7E));
            }
            case CHEF -> {
                prompt.setText("Chef: choose a task");
                hint.setText("Cook one unit. A dish finished now is served next turn.");
                List<Order> prep = r.preparableOrders();
                if (!prep.isEmpty()) {
                    addSection("Cook");
                }
                for (Order o : prep) {
                    Customer c = o.getCustomer();
                    addChoice(c, "Cook #" + c.getId() + " " + o.getItem().getName() + " (" + o.getRemainingUnits()
                            + " left)", "Chef", c.getId(), () -> game.chefPrepare(c.getId()), new Color(0xC0392B));
                }
                addSection("Or");
                addChoice(null, "Chef waits", "Chef", 0, game::chefWait, new Color(0x5D6D7E));
            }
            default -> {
            }
        }
        actions.add(Box.createVerticalGlue());
        actions.revalidate();
        actions.repaint();
    }

    private void addHappyHour() {
        HappyHour hh = game.getHappyHour();
        if (!hh.isEnabled()) {
            return;
        }
        boolean canUse = hh.isAvailable() && !acted;
        String text = hh.isActive() ? "Happy Hour is ON (" + hh.getRemainingTurns() + " turn(s) left)"
                : hh.isUsed() ? "Happy Hour already used"
                : acted ? "Happy Hour (only at the start of a turn)"
                : "Switch ON Happy Hour: -20%, 2 turns";
        PillButton b = new PillButton(text, new Color(0xD68910), null);
        b.setEnabled(canUse);
        b.setToolTipText("Once per game, before any staff acts. Waiting guests recover 15 satisfaction on both turns.");
        b.addActionListener(e -> {
            if (busy) {
                return;
            }
            ActionResult res = game.activateHappyHour();
            say(res.success() ? res.message() : "Not allowed: " + res.message(), res.success() ? GOLD : RED, false);
            view.sync();
            refresh();
        });
        actions.add(b);
        actions.add(Box.createVerticalStrut(8));
    }

    private void addSection(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setForeground(MUTED);
        l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        l.setBorder(BorderFactory.createEmptyBorder(6, 2, 4, 0));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(l);
        actions.add(row);
    }

    private void addChoice(Customer who, String text, String role, int customerId, Supplier<ActionResult> action,
                           Color color) {
        PillButton b = new PillButton(text, color, who);
        choices.add(b);
        b.setHotkey(text.endsWith("waits") ? "W" : choices.size() <= 9 ? Integer.toString(choices.size()) : null);
        b.addActionListener(e -> perform(role, customerId, action));
        if (who != null) {
            b.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    view.setHighlight(who.getId());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    view.setHighlight(-1);
                }
            });
        }
        actions.add(b);
        actions.add(Box.createVerticalStrut(6));
    }

    // ---- log --------------------------------------------------------------------------------------------------------------

    private void say(String text, Color color, boolean bold) {
        StyledDocument doc = log.getStyledDocument();
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, color);
        StyleConstants.setBold(a, bold);
        try {
            doc.insertString(doc.getLength(), text + "\n", a);
        } catch (BadLocationException ignored) {
            // cannot happen: always inserting at the end
        }
        log.setCaretPosition(doc.getLength());
    }

    // ---- a pill-shaped button ------------------------------------------------------------------------------------------

    private static final class PillButton extends JButton {
        private final Color base;
        private final Customer who;
        private boolean hover;
        private String hotkey;

        void setHotkey(String key) {
            this.hotkey = key;
            repaint();
        }

        PillButton(String text, Color base, Customer who) {
            super(text);
            this.base = base;
            this.who = who;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setForeground(Color.WHITE);
            setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            setHorizontalAlignment(SwingConstants.LEFT);
            setMargin(new Insets(10, 14, 10, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            setPreferredSize(new Dimension(300, 42));
            if (who != null) {
                setIcon(new HeadIcon(who));
                setIconTextGap(10);
            }
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Art.smooth(g2);
            Color c = !isEnabled() ? new Color(0x3B4756) : hover ? base.brighter() : base;
            g2.setColor(c);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 20, 20));
            if (hotkey != null) {
                g2.setColor(new Color(0, 0, 0, 55));
                g2.fill(new RoundRectangle2D.Double(getWidth() - 34, getHeight() / 2.0 - 11, 24, 22, 8, 8));
                Art.centredText(g2, hotkey, getWidth() - 22, getHeight() / 2.0 + 5, new Font(Font.SANS_SERIF, Font.BOLD, 12),
                        new Color(255, 255, 255, 210));
            }
            g2.dispose();
            setForeground(isEnabled() ? Color.WHITE : new Color(0x8795A5));
            super.paintComponent(g);
        }
    }

    /** A small portrait of the customer, so the buttons read as people, not numbers. */
    private static final class HeadIcon implements Icon {
        private final Customer c;

        HeadIcon(Customer c) {
            this.c = c;
        }

        @Override
        public void paintIcon(Component comp, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            Art.smooth(g2);
            g2.setColor(new Color(255, 255, 255, 235));
            g2.fill(new Ellipse2D.Double(x, y, 30, 30));
            g2.setClip(new Ellipse2D.Double(x, y, 30, 30));
            Art.head(g2, x + 15, y + 17, 0.8, Art.lookFor(c), c.getSatisfaction());
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 30;
        }

        @Override
        public int getIconHeight() {
            return 30;
        }
    }
}
