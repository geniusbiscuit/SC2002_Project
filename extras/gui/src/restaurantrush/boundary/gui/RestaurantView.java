package restaurantrush.boundary.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.ToolTipManager;

import restaurantrush.control.GameController;
import restaurantrush.control.GameController.Phase;
import restaurantrush.entity.Customer;
import restaurantrush.entity.CustomerState;
import restaurantrush.entity.Order;
import restaurantrush.entity.OrderStatus;
import restaurantrush.entity.Restaurant;
import restaurantrush.entity.Table;
import restaurantrush.util.Money;

/**
 * The picture of the restaurant: kitchen at the top, dining room with tables, the queue outside the
 * door on the left. It only READS the game state (through {@link GameController#getRestaurant()}) and
 * animates people walking towards the place their state says they should be.
 */
final class RestaurantView extends JPanel {

    static final int W = 860;
    static final int H = 620;

    private static final double DOOR_X = 170;
    private static final double DOOR_Y = 350;
    private static final Point2D.Double DOOR_IN = new Point2D.Double(190, DOOR_Y);
    private static final Point2D.Double EXIT = new Point2D.Double(-80, DOOR_Y + 10);
    private static final Point2D.Double REGISTER_FRONT = new Point2D.Double(232, 520);
    private static final double CUSTOMER_SCALE = 0.9;
    private static final double SPEED = 160;

    private static final Font SMALL_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    private static final Font BIG_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 13);

    // ---- moving things ------------------------------------------------------------------------

    private static final class Person {
        final Customer customer;
        final Art.Look look;
        double x;
        double y;
        double walk;
        boolean moving;
        boolean gone;
        boolean sitting;
        double eatPhase;
        double speed;
        double shownSatisfaction;
        String overlayKey = "";
        double overlayStart;
        final List<Point2D.Double> route = new ArrayList<>();
        String key = "";
        CustomerState lastState;
        OrderStatus lastOrder;

        Person(Customer customer, double x, double y) {
            this.customer = customer;
            this.look = Art.lookFor(customer);
            this.x = x;
            this.y = y;
            this.lastState = customer.getState();
            this.shownSatisfaction = customer.getSatisfaction();
        }
    }

    private static final class Actor {
        final String role;
        final Art.Look look;
        final double homeX;
        final double homeY;
        double x;
        double y;
        double destX;
        double destY;
        double walk;
        double speed;
        boolean moving;
        double busyUntil;

        Actor(String role, double homeX, double homeY) {
            this.role = role;
            this.look = Art.staffLook(role);
            this.homeX = homeX;
            this.homeY = homeY;
            this.x = homeX;
            this.y = homeY;
            this.destX = homeX;
            this.destY = homeY;
        }
    }

    private static final class Floater {
        final String text;
        final double x;
        final double y;
        final Color color;
        final double born;

        Floater(String text, double x, double y, Color color, double born) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.born = born;
        }
    }

    private record Drawable(double z, Consumer<Graphics2D> paint) {
    }

    private GameController game;
    private final Map<Integer, Person> people = new HashMap<>();
    private final List<Floater> floaters = new ArrayList<>();
    private final Actor host = new Actor("Host", 214, 322);
    private final Actor waiter = new Actor("Waiter", 640, 228);
    private final Actor chef = new Actor("Chef", 705, 160);
    private final Actor cashier = new Actor("Cashier", 229, 468);
    private final List<Point2D.Double> tablePos = new ArrayList<>();
    private BufferedImage background;
    private double time;
    private long lastNanos;
    private int highlightId = -1;
    private double scale = 1;
    private double offX;
    private double offY;

    RestaurantView() {
        setPreferredSize(new Dimension(W, H));
        setMinimumSize(new Dimension(520, 380));
        setBackground(new Color(0x1B2430));
        setDoubleBuffered(true);
        Timer timer = new Timer(16, e -> {
            long now = System.nanoTime();
            double dt = lastNanos == 0 ? 0.016 : Math.min(0.1, (now - lastNanos) / 1e9);
            lastNanos = now;
            step(dt);
            repaint();
        });
        timer.start();
        ToolTipManager.sharedInstance().registerComponent(this);
        ToolTipManager.sharedInstance().setInitialDelay(250);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        return tooltipAt(e.getX(), e.getY());
    }

    // ---- binding to the game ------------------------------------------------------------------

    void bind(GameController newGame) {
        this.game = newGame;
        people.clear();
        floaters.clear();
        for (Actor a : List.of(host, waiter, chef, cashier)) {
            a.x = a.homeX;
            a.y = a.homeY;
            a.destX = a.homeX;
            a.destY = a.homeY;
            a.busyUntil = 0;
        }
        layoutTables(newGame.getRestaurant().getTables().size());
        background = buildBackground();
        highlightId = -1;
        sync();
    }

    void setHighlight(int customerId) {
        this.highlightId = customerId;
    }

    /** Call after any change in the game: recompute where everybody should be and spot new events. */
    void sync() {
        if (game == null) {
            return;
        }
        Restaurant r = game.getRestaurant();
        List<Customer> queued = r.queuedCustomers();
        int spawn = 0;
        for (Customer c : r.getCustomers()) {
            Person p = people.get(c.getId());
            if (p == null) {
                p = new Person(c, -70 - 26 * spawn++, DOOR_Y + 16 + (c.getId() % 3) * 8);
                people.put(c.getId(), p);
            }
            String key = routeKey(c, queued);
            if (!key.equals(p.key)) {
                p.key = key;
                buildRoute(p, c, queued);
            }
            spotEvents(p, c);
            p.lastState = c.getState();
            p.lastOrder = c.getOrder() == null ? null : c.getOrder().getStatus();
        }
    }

    /** The staff member the manager just used walks to the customer; the Chef starts to cook. */
    void onAction(String role, int customerId) {
        Person p = people.get(customerId);
        switch (role) {
            case "Host", "Waiter" -> {
                Actor a = role.equals("Host") ? host : waiter;
                if (p != null && !p.route.isEmpty()) {
                    Point2D.Double dest = p.route.get(p.route.size() - 1);
                    boolean atTable = dest.x > DOOR_X + 10;
                    a.destX = dest.x + (atTable ? 52 : 44);
                    a.destY = dest.y + (atTable ? 12 : 4);
                }
            }
            case "Chef" -> {
                chef.busyUntil = time + 1.6;
                floaters.add(new Floater("Cooking #" + customerId, chef.x - 70, chef.y - 120,
                        new Color(0xF39C12), time));
            }
            default -> {
            }
        }
    }

    void onWait(String role) {
        Actor a = actor(role);
        if (a != null) {
            floaters.add(new Floater("zzz", a.x + 18, a.y - 100, new Color(0xBFD7EA), time));
        }
    }

    void onTurnStart() {
        for (Actor a : List.of(host, waiter)) {
            a.destX = a.homeX;
            a.destY = a.homeY;
        }
    }

    private Actor actor(String role) {
        return switch (role) {
            case "Host" -> host;
            case "Waiter" -> waiter;
            case "Chef" -> chef;
            case "Cashier" -> cashier;
            default -> null;
        };
    }

    // ---- routes ------------------------------------------------------------------------------------

    private static String routeKey(Customer c, List<Customer> queued) {
        return switch (c.getState()) {
            case WAITING_FOR_TABLE -> "Q" + queued.indexOf(c);
            case PAID -> "PAID";
            case ABANDONED -> "LEFT";
            default -> "T" + c.getTable().getNumber();
        };
    }

    private void buildRoute(Person p, Customer c, List<Customer> queued) {
        p.route.clear();
        boolean outside = p.x < DOOR_X + 8;
        switch (c.getState()) {
            case WAITING_FOR_TABLE -> p.route.add(queueSlot(queued.indexOf(c)));
            case PAID -> {
                p.route.add(REGISTER_FRONT);
                p.route.add(DOOR_IN);
                p.route.add(EXIT);
            }
            case ABANDONED -> {
                if (!outside) {
                    p.route.add(DOOR_IN);
                }
                p.route.add(EXIT);
            }
            default -> {
                if (outside) {
                    p.route.add(DOOR_IN);
                }
                p.route.add(seat(c.getTable()));
            }
        }
    }

    private static Point2D.Double queueSlot(int index) {
        return new Point2D.Double(104 + (index % 2) * 40, Math.min(602, 346 + index * 54));
    }

    private Point2D.Double seat(Table t) {
        Point2D.Double tp = tablePos.get(t.getNumber() - 1);
        return new Point2D.Double(tp.x, tp.y + 4);
    }

    private void layoutTables(int n) {
        tablePos.clear();
        int cols = n <= 3 ? Math.max(1, n) : 3;
        int rows = (n + cols - 1) / cols;
        double[] xs = switch (cols) {
            case 1 -> new double[] {560};
            case 2 -> new double[] {430, 690};
            default -> new double[] {365, 550, 735};
        };
        double[] ys = rows == 1 ? new double[] {410} : new double[] {330, 510};
        for (int i = 0; i < n; i++) {
            tablePos.add(new Point2D.Double(xs[i % cols], ys[Math.min(rows - 1, i / cols)]));
        }
    }

    private void spotEvents(Person p, Customer c) {
        CustomerState now = c.getState();
        if (now != p.lastState) {
            if (now == CustomerState.PAID) {
                floaters.add(new Floater("+" + Money.format(c.getOrder().getBillCents()), p.x, p.y - 118,
                        new Color(0xF1C40F), time));
                floaters.add(new Floater("sat " + c.getPaidSatisfaction(), p.x, p.y - 100,
                        new Color(0x82E0AA), time));
            } else if (now == CustomerState.ABANDONED) {
                floaters.add(new Floater("Left angry!", p.x, p.y - 118, new Color(0xFF6B5E), time));
            } else if (now == CustomerState.EATING) {
                floaters.add(new Floater("Yum!", p.x, p.y - 118, new Color(0xFFB6C1), time));
            }
        }
        OrderStatus os = c.getOrder() == null ? null : c.getOrder().getStatus();
        if (os == OrderStatus.READY && p.lastOrder != OrderStatus.READY) {
            floaters.add(new Floater("DING! #" + c.getId() + " ready", 470, 128, new Color(0xFFE08A), time));
        }
    }

    // ---- animation ---------------------------------------------------------------------------------

    void advance(double seconds) {
        double left = seconds;
        while (left > 0) {
            double dt = Math.min(0.016, left);
            step(dt);
            left -= dt;
        }
    }

    private void step(double dt) {
        time += dt;
        for (Person p : people.values()) {
            double goal = p.customer.getSatisfaction();
            p.shownSatisfaction += Math.max(-45 * dt, Math.min(45 * dt, goal - p.shownSatisfaction));
            if (p.gone || p.route.isEmpty()) {
                p.moving = false;
                continue;
            }
            Point2D.Double target = p.route.get(0);
            double dx = target.x - p.x;
            double dy = target.y - p.y;
            double dist = Math.hypot(dx, dy);
            boolean last = p.route.size() == 1;
            double desired = last ? Math.min(SPEED, 24 + dist * 5.5) : SPEED;
            p.speed += (desired - p.speed) * Math.min(1, dt * 9);
            double move = Math.max(10, p.speed) * dt;
            if (dist <= move) {
                p.x = target.x;
                p.y = target.y;
                if (p.route.size() > 1) {
                    p.route.remove(0);
                } else if (p.customer.getState() == CustomerState.PAID
                        || p.customer.getState() == CustomerState.ABANDONED) {
                    p.gone = true;
                }
                p.moving = !last;
                if (last) {
                    p.speed = 0;
                }
            } else {
                p.x += dx / dist * move;
                p.y += dy / dist * move;
                p.moving = true;
                p.walk += dt * (4 + p.speed / 14);
            }
            CustomerState st = p.customer.getState();
            p.sitting = !p.moving && p.route.size() == 1 && st != CustomerState.WAITING_FOR_TABLE
                    && st != CustomerState.PAID && st != CustomerState.ABANDONED;
            if (st == CustomerState.EATING) {
                p.eatPhase += dt * 6;
            }
        }
        for (Actor a : List.of(host, waiter)) {
            double dx = a.destX - a.x;
            double dy = a.destY - a.y;
            double dist = Math.hypot(dx, dy);
            double desired = Math.min(SPEED * 1.3, 24 + dist * 5.5);
            a.speed += (desired - a.speed) * Math.min(1, dt * 9);
            double move = Math.max(10, a.speed) * dt;
            if (dist <= move) {
                a.x = a.destX;
                a.y = a.destY;
                a.moving = false;
                a.speed = 0;
            } else {
                a.x += dx / dist * move;
                a.y += dy / dist * move;
                a.moving = true;
                a.walk += dt * (4 + a.speed / 14);
            }
        }
        floaters.removeIf(f -> time - f.born > 2.4);
    }

    // ---- painting -----------------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        Art.smooth(g);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        scale = Math.min(getWidth() / (double) W, getHeight() / (double) H);
        offX = (getWidth() - W * scale) / 2;
        offY = (getHeight() - H * scale) / 2;
        g.translate(offX, offY);
        g.scale(scale, scale);
        g.setClip(0, 0, W, H);
        paintScene(g);
        g.dispose();
    }

    /** Paints the scene in logical (860 x 620) coordinates. Also used by the screenshot tool. */
    void paintScene(Graphics2D g) {
        if (background == null || game == null) {
            return;
        }
        g.drawImage(background, 0, 0, null);
        boolean happy = game.getHappyHour().isActive();
        if (happy) {
            g.setColor(new Color(255, 190, 60, 34));
            g.fill(new Rectangle2D.Double(DOOR_X, 176, W - DOOR_X, H - 176));
        }
        List<Drawable> items = new ArrayList<>();
        Restaurant r = game.getRestaurant();
        // tables with chairs
        for (Table t : r.getTables()) {
            Point2D.Double tp = tablePos.get(t.getNumber() - 1);
            items.add(new Drawable(tp.y - 40, gg -> paintChair(gg, tp)));
            items.add(new Drawable(tp.y + 24, gg -> paintTable(gg, t, tp)));
        }
        // customers
        for (Person p : people.values()) {
            if (p.gone) {
                continue;
            }
            double z = p.sitting ? tablePos.get(p.customer.getTable().getNumber() - 1).y - 10 : p.y;
            items.add(new Drawable(z, gg -> paintPerson(gg, p)));
        }
        // staff
        items.add(new Drawable(host.y, gg -> paintActor(gg, host)));
        items.add(new Drawable(waiter.y, gg -> paintActor(gg, waiter)));
        items.add(new Drawable(chef.y, gg -> paintActor(gg, chef)));
        items.add(new Drawable(cashier.y, gg -> paintActor(gg, cashier)));
        items.add(new Drawable(172, this::paintKitchenCounter));
        items.add(new Drawable(500, this::paintRegister));
        items.sort(Comparator.comparingDouble(Drawable::z));
        for (Drawable d : items) {
            d.paint().accept(g);
        }
        paintTickets(g);
        if (happy) {
            paintBunting(g);
        }
        for (Person p : people.values()) {
            if (!p.gone) {
                paintOverlay(g, p);
            }
        }
        paintStaffTags(g);
        for (Floater f : floaters) {
            double age = time - f.born;
            double alpha = Math.max(0, Math.min(1, 1.6 - age / 1.5));
            double fy = f.y - age * 22;
            g.setFont(BIG_BOLD);
            g.setColor(new Color(0, 0, 0, (int) (alpha * 170)));
            Art.centredText(g, f.text, f.x + 1, fy + 1, BIG_BOLD, new Color(0, 0, 0, (int) (alpha * 170)));
            Art.centredText(g, f.text, f.x, fy, BIG_BOLD,
                    new Color(f.color.getRed(), f.color.getGreen(), f.color.getBlue(), (int) (alpha * 255)));
        }
    }

    // ---- scene parts ---------------------------------------------------------------------------------

    private void paintChair(Graphics2D g, Point2D.Double tp) {
        g.setColor(new Color(0x7B4B2A));
        g.fill(new RoundRectangle2D.Double(tp.x - 26, tp.y - 82, 52, 62, 14, 14));
        g.setColor(new Color(0x5E3920));
        g.fill(new RoundRectangle2D.Double(tp.x - 26, tp.y - 28, 52, 12, 6, 6));
    }

    private void paintTable(Graphics2D g, Table t, Point2D.Double tp) {
        g.setColor(new Color(0, 0, 0, 55));
        g.fill(new Ellipse2D.Double(tp.x - 66, tp.y - 24, 132, 66));
        g.setColor(new Color(0x7A5230));
        g.fill(new Ellipse2D.Double(tp.x - 62, tp.y - 22, 124, 56));
        g.setPaint(Art.vertical(tp.y - 30, tp.y + 28, new Color(0xE0B27C), new Color(0xC9955C)));
        g.fill(new Ellipse2D.Double(tp.x - 62, tp.y - 30, 124, 56));
        g.setColor(new Color(0x9C6B3C));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Ellipse2D.Double(tp.x - 62, tp.y - 30, 124, 56));
        Art.tag(g, "Table " + t.getNumber(), tp.x, tp.y + 36, t.isFree() ? new Color(0x2ECC71) : new Color(0x34495E),
                Color.WHITE);
        Customer c = t.getOccupant();
        if (c != null && c.getOrder() != null) {
            Order o = c.getOrder();
            if (c.getState() == CustomerState.EATING) {
                Art.dish(g, tp.x, tp.y + 2, 16, o.getItem(), true);
                Art.steam(g, tp.x, tp.y - 12, time + c.getId());
            } else if (c.getState() == CustomerState.READY_TO_PAY) {
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(tp.x - 16, tp.y - 8, 32, 17));
                g.setColor(new Color(0xD5D8DC));
                g.draw(new Ellipse2D.Double(tp.x - 16, tp.y - 8, 32, 17));
            }
        } else if (c == null) {
            g.setColor(new Color(255, 255, 255, 120));
            g.fill(new Ellipse2D.Double(tp.x - 9, tp.y - 6, 18, 10));
        }
    }

    private void paintPerson(Graphics2D g, Person p) {
        Customer c = p.customer;
        boolean eating = c.getState() == CustomerState.EATING && p.sitting;
        double s = CUSTOMER_SCALE;
        if (c.getId() == highlightId) {
            double pulse = 1 + 0.08 * Math.sin(time * 8);
            g.setColor(new Color(255, 230, 90, 80));
            g.fill(new Ellipse2D.Double(p.x - 28 * pulse, p.y - 10 * pulse, 56 * pulse, 19 * pulse));
            g.setColor(new Color(255, 230, 90, 220));
            g.setStroke(new BasicStroke(3f));
            g.draw(new Ellipse2D.Double(p.x - 26 * pulse, p.y - 9 * pulse, 52 * pulse, 17 * pulse));
        }
        Art.Look look = p.look;
        int mood = c.getSatisfaction();
        if (c.getState() == CustomerState.ABANDONED) {
            look = look.withShirt(new Color(0xC0392B));
            mood = 0;
        }
        Art.Pose pose = new Art.Pose(time, p.walk, p.moving, p.sitting, eating, p.eatPhase, mood, c.getId());
        Art.person(g, p.x, p.y, s, look, pose);
    }

    private void paintActor(Graphics2D g, Actor a) {
        Phase ph = game.getPhase();
        boolean active = (a == host && ph == Phase.HOST) || (a == waiter && ph == Phase.WAITER)
                || (a == chef && ph == Phase.CHEF);
        if (active) {
            double pulse = 0.5 + 0.5 * Math.sin(time * 5);
            g.setColor(new Color(255, 220, 60, (int) (110 + 90 * pulse)));
            g.fill(new Ellipse2D.Double(a.x - 30, a.y - 10, 60, 20));
            g.setColor(new Color(255, 245, 160, 230));
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Ellipse2D.Double(a.x - 30, a.y - 10, 60, 20));
        }
        double shake = a == chef && time < a.busyUntil ? Math.sin(time * 40) * 1.5 : 0;
        Art.person(g, a.x + shake, a.y, 1.0, a.look,
                new Art.Pose(time, a.walk, a.moving, false, false, 0, -1, a.role.length() * 7));
        if (a == chef) {
            paintPan(g, chef.x - 48, chef.y - 38);
        }
    }

    private void paintPan(Graphics2D g, double x, double y) {
        boolean cooking = time < chef.busyUntil;
        g.setColor(new Color(0x333333));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(round(x + 18), round(y), round(x + 42), round(y - 6));
        g.setColor(new Color(0x555555));
        g.fill(new Ellipse2D.Double(x - 6, y - 8, 30, 16));
        if (cooking) {
            for (int i = 0; i < 4; i++) {
                double ph = (time * 2.2 + i * 0.27) % 1;
                g.setColor(new Color(255, 160 - i * 20, 40, (int) (230 * (1 - ph))));
                g.fill(new Ellipse2D.Double(x + 2 + i * 5, y - 12 - ph * 28, 6 - ph * 3, 6 - ph * 3));
            }
        }
    }

    private void paintKitchenCounter(Graphics2D g) {
        g.setColor(new Color(0x6E7A86));
        g.fill(new RoundRectangle2D.Double(176, 168, W - 176, 14, 4, 4));
        g.setPaint(Art.vertical(140, 170, new Color(0xB8C4CE), new Color(0x8D9AA6)));
        g.fill(new Rectangle2D.Double(176, 142, W - 176, 28));
        g.setColor(new Color(0x5D6975));
        g.fill(new Rectangle2D.Double(176, 170, W - 176, 6));
        // pass-through hatch with ready dishes
        g.setColor(new Color(0xFFE08A));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Rectangle2D.Double(330, 144, 250, 24));
        Art.tag(g, "PASS: ready dishes", 455, 186, new Color(0x34495E), Color.WHITE);
        List<Customer> ready = new ArrayList<>();
        for (Customer c : game.getRestaurant().customersInside()) {
            if (c.getOrder() != null && c.getOrder().getStatus() == OrderStatus.READY) {
                ready.add(c);
            }
        }
        ready.sort(Comparator.comparingInt((Customer c) -> c.getOrder().getReadyTurn()).thenComparingInt(Customer::getId));
        for (int i = 0; i < ready.size() && i < 5; i++) {
            Customer c = ready.get(i);
            double px = 365 + i * 50;
            Art.dish(g, px, 153, 17, c.getOrder().getItem(), true);
            Art.tag(g, "#" + c.getId(), px, 166, new Color(0x27AE60), Color.WHITE);
            if (i == 0) {
                // service bell
                g.setColor(new Color(0xF1C40F));
                g.fill(new Ellipse2D.Double(px - 36, 150, 14, 9));
            }
        }
    }

    private void paintRegister(Graphics2D g) {
        g.setPaint(Art.vertical(450, 500, new Color(0x9C6B3C), new Color(0x6F4A28)));
        g.fill(new RoundRectangle2D.Double(190, 466, 78, 34, 8, 8));
        g.setColor(new Color(0x2C3E50));
        g.fill(new RoundRectangle2D.Double(206, 450, 30, 18, 4, 4));
        g.setColor(new Color(0x7FD6A0));
        g.fill(new Rectangle2D.Double(210, 453, 22, 8));
        Art.tag(g, "CASHIER", 229, 512, new Color(0x34495E), Color.WHITE);
    }

    private void paintTickets(Graphics2D g) {
        List<Customer> cooking = new ArrayList<>();
        for (Customer c : game.getRestaurant().customersInside()) {
            Order o = c.getOrder();
            if (o != null && (o.getStatus() == OrderStatus.PLACED || o.getStatus() == OrderStatus.IN_PROGRESS)) {
                cooking.add(c);
            }
        }
        cooking.sort(Comparator.comparingInt(Customer::getId));
        for (int i = 0; i < cooking.size() && i < 7; i++) {
            Customer c = cooking.get(i);
            Order o = c.getOrder();
            double x = 196 + i * 64;
            double sway = Math.sin(time * 2 + i) * 1.2;
            g.setColor(new Color(0, 0, 0, 40));
            g.fill(new RoundRectangle2D.Double(x + 2, 22, 56, 70, 4, 4));
            g.setColor(new Color(0xFFFBEA));
            g.fill(new RoundRectangle2D.Double(x + sway, 20, 56, 70, 4, 4));
            g.setColor(new Color(0xC0392B));
            g.fill(new Rectangle2D.Double(x + sway, 20, 56, 11));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            g.setColor(Color.WHITE);
            g.drawString("ORDER #" + c.getId(), (float) (x + 5 + sway), 29);
            Art.dish(g, x + 28 + sway, 56, 12, o.getItem(), false);
            int total = o.getItem().getPrepUnits();
            double startX = x + 28 - total * 5.5 + sway;
            for (int u = 0; u < total; u++) {
                g.setColor(u < o.getPreparedUnits() ? new Color(0x2ECC71) : new Color(0xD0D3D4));
                g.fill(new Ellipse2D.Double(startX + u * 11, 76, 8, 8));
            }
        }
    }

    private void paintBunting(Graphics2D g) {
        g.setColor(new Color(0x8E5B3A));
        g.setStroke(new BasicStroke(1.6f));
        Path2D rope = new Path2D.Double();
        rope.moveTo(180, 196);
        rope.quadTo(520, 232, 850, 196);
        g.draw(rope);
        Color[] cols = {new Color(0xE74C3C), new Color(0xF1C40F), new Color(0x3498DB), new Color(0x2ECC71),
            new Color(0xE67E22), new Color(0x9B59B6)};
        for (int i = 0; i < 16; i++) {
            double t = (i + 0.5) / 16.0;
            double x = 180 + t * 670;
            double y = 196 + 2 * 0.5 * (1 - (2 * t - 1) * (2 * t - 1)) * 18;
            Path2D flag = new Path2D.Double();
            flag.moveTo(x - 9, y);
            flag.lineTo(x + 9, y);
            flag.lineTo(x, y + 20 + Math.sin(time * 3 + i) * 1.5);
            flag.closePath();
            g.setColor(cols[i % cols.length]);
            g.fill(flag);
        }
        g.setColor(new Color(0xFFF3C4));
        g.fill(new RoundRectangle2D.Double(420, 214, 200, 28, 14, 14));
        g.setColor(new Color(0xD35400));
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(420, 214, 200, 28, 14, 14));
        Art.centredText(g, "HAPPY HOUR  -20%  (" + game.getHappyHour().getRemainingTurns() + " left)", 520, 233,
                SMALL_BOLD, new Color(0xA04000));
    }

    private void paintStaffTags(Graphics2D g) {
        Phase ph = game.getPhase();
        for (Actor a : List.of(host, waiter, chef, cashier)) {
            if ((a == host && !game.isHostEnabled()) || a == cashier) {
                continue;
            }
            boolean active = (a == host && ph == Phase.HOST) || (a == waiter && ph == Phase.WAITER)
                    || (a == chef && ph == Phase.CHEF);
            double ty = a == cashier ? a.y + 14 : a.y + 16;
            if (a == chef) {
                ty = 192;
            }
            Art.tag(g, a.role.toUpperCase() + (active ? "  <- your call" : ""), a.x, ty,
                    active ? new Color(0xF1C40F) : new Color(0x26303B), active ? Color.BLACK : Color.WHITE);
        }
    }

    private void paintOverlay(Graphics2D g, Person p) {
        Customer c = p.customer;
        CustomerState st = c.getState();
        double s = CUSTOMER_SCALE;
        double top = p.y - 90 * s - (p.sitting ? 0 : 0);
        if (st == CustomerState.PAID) {
            return;
        }
        if (st == CustomerState.ABANDONED) {
            Art.centredText(g, "!!", p.x, top - 6 + Math.sin(time * 14) * 2, BIG_BOLD, new Color(0xFF5A4D));
            return;
        }
        int shown = (int) Math.round(p.shownSatisfaction);
        if (st == CustomerState.WAITING_FOR_TABLE) {
            Art.satisfactionBar(g, p.x, top - 16, p.shownSatisfaction, "#" + c.getId() + "  " + shown);
            return;
        }
        Art.satisfactionBar(g, p.x, top - 16, p.shownSatisfaction, Integer.toString(shown));
        Art.tag(g, "#" + c.getId() + " " + c.getTypeName(), p.x, top - 26, new Color(0, 0, 0, 150), Color.WHITE);
        Order o = c.getOrder();
        String key = st + (o == null ? "" : ":" + o.getStatus() + o.getPreparedUnits());
        if (!key.equals(p.overlayKey)) {
            p.overlayKey = key;
            p.overlayStart = time;
        }
        if (st != CustomerState.SEATED && st != CustomerState.AWAITING_FOOD && st != CustomerState.READY_TO_PAY) {
            return;
        }
        // speech bubble that pops in whenever its content changes
        double t = Math.min(1, (time - p.overlayStart) / 0.28);
        double pop = t >= 1 ? 1 : 1 + 2.4 * Math.pow(t - 1, 3) + 1.4 * Math.pow(t - 1, 2);
        Graphics2D b = (Graphics2D) g.create();
        b.translate(p.x, top - 34);
        b.scale(pop, pop);
        switch (st) {
            case SEATED -> {
                Art.bubble(b, 0, 0, 62, 32, Color.WHITE);
                b.setColor(new Color(0xF5E6C8));
                b.fill(new RoundRectangle2D.Double(-24, -31, 16, 22, 3, 3));
                b.setColor(new Color(0xC0392B));
                b.setStroke(new BasicStroke(1.2f));
                b.draw(new RoundRectangle2D.Double(-24, -31, 16, 22, 3, 3));
                Art.centredText(b, "order?", 12, -15, SMALL_BOLD, Art.INK);
            }
            case AWAITING_FOOD -> {
                boolean ready = o.getStatus() == OrderStatus.READY;
                Art.bubble(b, 0, 0, 66, 32, ready ? new Color(0xE8F8EE) : Color.WHITE);
                Art.dish(b, -19, -17, 11, o.getItem(), false);
                if (ready) {
                    Art.centredText(b, "ready!", 14, -14, SMALL_BOLD, new Color(0x1E8449));
                } else {
                    Art.centredText(b, o.getPreparedUnits() + "/" + o.getItem().getPrepUnits(), 17, -14, SMALL_BOLD,
                            Art.INK);
                }
            }
            default -> {
                Art.bubble(b, 0, 0, 62, 32, new Color(0xFFF6D6));
                Art.centredText(b, "$ bill", 0, -14, BIG_BOLD, new Color(0x9A7D0A));
            }
        }
        b.dispose();
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }

    // ---- static background ------------------------------------------------------------------------------

    private BufferedImage buildBackground() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Art.smooth(g);
        // pavement
        g.setPaint(Art.vertical(0, H, new Color(0xCFC9BC), new Color(0xB4AE9F)));
        g.fill(new Rectangle2D.Double(0, 0, DOOR_X, H));
        g.setColor(new Color(0, 0, 0, 25));
        g.setStroke(new BasicStroke(1f));
        for (int y = 0; y < H; y += 40) {
            g.drawLine(0, y, (int) DOOR_X, y);
        }
        for (int x = 0; x < DOOR_X; x += 42) {
            g.drawLine(x, 0, x, H);
        }
        // red carpet for the queue
        g.setColor(new Color(0x9B2335));
        g.fill(new RoundRectangle2D.Double(82, 318, 80, 292, 10, 10));
        g.setColor(new Color(0xE8B4BC));
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(86, 322, 72, 284, 8, 8));
        // stanchions
        for (int i = 0; i < 4; i++) {
            double y = 330 + i * 92;
            g.setColor(new Color(0x7F6A2D));
            g.fill(new Ellipse2D.Double(60, y + 14, 16, 8));
            g.setColor(new Color(0xD4AF37));
            g.fill(new RoundRectangle2D.Double(65, y - 6, 6, 24, 3, 3));
            g.fill(new Ellipse2D.Double(63, y - 10, 10, 10));
            if (i > 0) {
                g.setColor(new Color(0x7B1E2B));
                g.setStroke(new BasicStroke(3f));
                g.drawLine(68, (int) (y - 92 + 2), 68, (int) (y + 2));
            }
        }
        // sign
        g.setColor(new Color(0x1E2A38));
        g.fill(new RoundRectangle2D.Double(10, 16, 146, 74, 14, 14));
        g.setColor(new Color(0xFFD166));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new RoundRectangle2D.Double(14, 20, 138, 66, 10, 10));
        Art.centredText(g, "RESTAURANT", 83, 49, new Font(Font.SANS_SERIF, Font.BOLD, 17), new Color(0xFFD166));
        Art.centredText(g, "RUSH", 83, 74, new Font(Font.SANS_SERIF, Font.BOLD, 22), new Color(0xFF6B6B));
        // plants on the pavement
        plant(g, 30, 140);
        plant(g, 30, 250);
        plant(g, 135, 600);
        // outer wall
        g.setColor(new Color(0x5D4037));
        g.fill(new Rectangle2D.Double(DOOR_X - 4, 0, 14, 296));
        g.fill(new Rectangle2D.Double(DOOR_X - 4, 408, 14, H - 408));
        g.setColor(new Color(0xFFF3D6));
        g.fill(new Rectangle2D.Double(DOOR_X - 4, 296, 14, 112));
        g.setColor(new Color(0x8D6E63));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Rectangle2D.Double(DOOR_X - 4, 296, 14, 112));
        // kitchen wall and tiles
        g.setPaint(Art.vertical(0, 176, new Color(0xF2E9D8), new Color(0xE3D5BB)));
        g.fill(new Rectangle2D.Double(DOOR_X + 10, 0, W - DOOR_X - 10, 176));
        g.setColor(new Color(0, 0, 0, 18));
        for (int x = 180; x < W; x += 28) {
            g.drawLine(x, 0, x, 176);
        }
        for (int y = 0; y < 176; y += 28) {
            g.drawLine(180, y, W, y);
        }
        // rail for order tickets
        g.setColor(new Color(0x5D6975));
        g.fill(new RoundRectangle2D.Double(190, 12, 450, 6, 3, 3));
        Art.centredText(g, "KITCHEN ORDERS", 415, 108, SMALL_BOLD, new Color(0x7A6A55));
        // stove with hood
        g.setColor(new Color(0x8A97A3));
        Path2D hood = new Path2D.Double();
        hood.moveTo(650, 16);
        hood.lineTo(812, 16);
        hood.lineTo(840, 52);
        hood.lineTo(626, 52);
        hood.closePath();
        g.fill(hood);
        g.setColor(new Color(0x37424D));
        g.fill(new RoundRectangle2D.Double(640, 60, 180, 80, 6, 6));
        g.setColor(new Color(0x1F262D));
        for (int i = 0; i < 3; i++) {
            g.fill(new Ellipse2D.Double(656 + i * 56, 118, 40, 12));
        }
        g.setColor(new Color(0xC0C8CF));
        g.fill(new RoundRectangle2D.Double(782, 70, 26, 36, 4, 4));
        g.setColor(new Color(0x95A5A6));
        g.fill(new Rectangle2D.Double(774, 70, 8, 5));
        // dining floor
        for (int ty = 0; ty < 12; ty++) {
            for (int tx = 0; tx < 18; tx++) {
                double x = DOOR_X + 10 + tx * 40;
                double y = 182 + ty * 40;
                if (x > W || y > H) {
                    continue;
                }
                g.setColor((tx + ty) % 2 == 0 ? new Color(0xEBDDBF) : new Color(0xDECCA6));
                g.fill(new Rectangle2D.Double(x, y, 40, 40));
            }
        }
        g.setColor(new Color(0, 0, 0, 30));
        g.fill(new Rectangle2D.Double(DOOR_X + 10, 176, W - DOOR_X, 8));
        plant(g, 300, 600);
        plant(g, 215, 215);
        menuBoard(g, 700, H - 46 - game.getConfig().getMenu().getAllDishes().size() * 17 - 14);
        g.dispose();
        return img;
    }

    /** A standing chalkboard with today's menu, taken from the game's own menu. */
    private void menuBoard(Graphics2D g, double x, double y) {
        List<restaurantrush.entity.MenuItem> dishes = game.getConfig().getMenu().getAllDishes();
        double h = 46 + dishes.size() * 17;
        g.setColor(new Color(0, 0, 0, 40));
        g.fill(new Ellipse2D.Double(x - 4, y + h - 6, 150, 14));
        g.setColor(new Color(0x7B4B2A));
        g.fill(new RoundRectangle2D.Double(x - 4, y - 4, 148, h + 8, 10, 10));
        g.setColor(new Color(0x24352C));
        g.fill(new RoundRectangle2D.Double(x, y, 140, h, 6, 6));
        Art.centredText(g, "TODAY'S MENU", x + 70, y + 18, SMALL_BOLD, new Color(0xF6E58D));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        double ty = y + 36;
        for (restaurantrush.entity.MenuItem m : dishes) {
            String name = m.getName().replace(" + ", "+");
            g.setColor(new Color(0xF5F5F0));
            g.drawString(name, (float) (x + 8), (float) ty);
            String price = Money.format(m.getPriceCents());
            g.drawString(price, (float) (x + 132 - g.getFontMetrics().stringWidth(price)), (float) ty);
            ty += 17;
        }
    }

    private static void plant(Graphics2D g, double x, double y) {
        g.setColor(new Color(0, 0, 0, 40));
        g.fill(new Ellipse2D.Double(x - 14, y - 3, 28, 9));
        g.setColor(new Color(0xB5651D));
        Path2D pot = new Path2D.Double();
        pot.moveTo(x - 11, y - 18);
        pot.lineTo(x + 11, y - 18);
        pot.lineTo(x + 8, y);
        pot.lineTo(x - 8, y);
        pot.closePath();
        g.fill(pot);
        Color[] greens = {new Color(0x2E8B57), new Color(0x3CB371), new Color(0x228B22)};
        for (int i = 0; i < 5; i++) {
            g.setColor(greens[i % 3]);
            double a = -Math.PI / 2 + (i - 2) * 0.55;
            g.fill(new Ellipse2D.Double(x + Math.cos(a) * 12 - 7, y - 30 + Math.sin(a) * 12 - 10, 14, 22));
        }
    }

    // ---- hover -----------------------------------------------------------------------------------------------

    private String tooltipAt(int mx, int my) {
        if (game == null) {
            return null;
        }
        double lx = (mx - offX) / scale;
        double ly = (my - offY) / scale;
        for (Person p : people.values()) {
            if (p.gone) {
                continue;
            }
            if (Math.abs(lx - p.x) < 20 && ly > p.y - 95 && ly < p.y + 6) {
                Customer c = p.customer;
                String what = switch (c.getState()) {
                    case WAITING_FOR_TABLE -> "waiting for a table";
                    case SEATED -> "seated, wants to order";
                    case AWAITING_FOOD -> "waiting for " + c.getOrder().getItem().getName() + " ("
                            + c.getOrder().getStatus().toString().toLowerCase().replace('_', ' ') + ")";
                    case EATING -> "eating " + c.getOrder().getItem().getName();
                    case READY_TO_PAY -> "ready to pay " + Money.format(c.getOrder().getBillCents());
                    case PAID -> "paid";
                    case ABANDONED -> "left unhappy";
                };
                return "#" + c.getId() + " " + c.getTypeName() + ": " + what + " - satisfaction "
                        + c.getSatisfaction() + " (loses " + c.getWaitingLoss() + " per turn while unserved)";
            }
        }
        return null;
    }
}
