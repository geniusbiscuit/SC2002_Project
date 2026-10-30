package restaurantrush.boundary.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import restaurantrush.entity.ComboMeal;
import restaurantrush.entity.Customer;
import restaurantrush.entity.MenuItem;

/**
 * All drawing helpers of the GUI: people, dishes, bubbles and bars. Every picture is drawn with
 * Java2D, so the game needs no image files. Pure view code: nothing here changes game state.
 */
final class Art {

    private Art() {
    }

    enum Hat { NONE, CAP, CROWN, BERET, CHEF }

    static final int GLASSES = 1;
    static final int BOWTIE = 2;
    static final int APRON = 4;
    static final int SCARF = 8;
    static final int VEST = 16;
    static final int NOTEPAD = 32;
    static final int CLIPBOARD = 64;
    static final int COLLAR = 128;

    /**
     * How one person looks. {@code hairStyle}: 0 short, 1 side fringe, 2 long, 3 bun.
     * {@code flags} is a bit set of the constants above.
     */
    record Look(Color skin, Color hair, Color shirt, Color pants, Hat hat, int hairStyle, int flags) {
        boolean has(int flag) {
            return (flags & flag) != 0;
        }

        Look withShirt(Color c) {
            return new Look(skin, hair, c, pants, hat, hairStyle, flags);
        }

        Look withoutProps() {
            return new Look(skin, hair, shirt, pants, hat, hairStyle, flags & (COLLAR));
        }
    }

    /** What a person is doing right now (drives the animation). */
    record Pose(double time, double walk, boolean moving, boolean sitting, boolean eating, double eatPhase,
                int mood, int seed) {
        static Pose idle(double time, int mood, int seed) {
            return new Pose(time, 0, false, false, false, 0, mood, seed);
        }
    }

    private static final Color[] SKIN = {
        new Color(0xF6D2B0), new Color(0xE8B48A), new Color(0xCB8F66), new Color(0x9C6A47), new Color(0x74502F)};
    private static final Color[] HAIR = {
        new Color(0x2B1B12), new Color(0x5A3825), new Color(0xB5651D), new Color(0x1C1C1C), new Color(0x8C8C8C),
        new Color(0xD9A441), new Color(0x7B3F2A)};

    static final Color INK = new Color(0x26303B);

    static Look lookFor(Customer c) {
        int id = c.getId();
        Color skin = SKIN[(id * 3 + 1) % SKIN.length];
        Color hair = HAIR[(id * 5 + 2) % HAIR.length];
        int style = (id * 7 + 1) % 4;
        return switch (c.getTypeName()) {
            case "VIP" -> new Look(skin, hair, new Color(0x7D3C98), new Color(0x2B1B3D), Hat.CROWN, style,
                    BOWTIE | COLLAR);
            case "Critic" -> new Look(skin, new Color(0x202020), new Color(0x2F3640), new Color(0x1B1F24), Hat.BERET,
                    0, GLASSES | SCARF | NOTEPAD);
            default -> new Look(skin, hair, id % 3 == 0 ? new Color(0x27AE60) : new Color(0x2E86C1),
                    new Color(0x34495E), id % 2 == 0 ? Hat.CAP : Hat.NONE, style, COLLAR);
        };
    }

    static Look staffLook(String role) {
        Color skin = SKIN[Math.abs(role.hashCode()) % 3];
        return switch (role) {
            case "Chef" -> new Look(skin, new Color(0x3A2A20), Color.WHITE, new Color(0x4A4A4A), Hat.CHEF, 0, COLLAR);
            case "Cashier" -> new Look(skin, new Color(0x7B3F00), new Color(0xF4F1EA), new Color(0x2C3E50), Hat.NONE, 2,
                    APRON | COLLAR);
            case "Host" -> new Look(skin, new Color(0x1C1C1C), new Color(0xC0392B), new Color(0x1C1C1C), Hat.NONE, 1,
                    BOWTIE | CLIPBOARD | COLLAR);
            default -> new Look(skin, new Color(0x2B1B12), Color.WHITE, new Color(0x1C1C1C), Hat.NONE, 1,
                    BOWTIE | VEST | COLLAR);
        };
    }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static Color shade(Color c, double factor) {
        return new Color(Math.max(0, Math.min(255, (int) (c.getRed() * factor))),
                Math.max(0, Math.min(255, (int) (c.getGreen() * factor))),
                Math.max(0, Math.min(255, (int) (c.getBlue() * factor))), c.getAlpha());
    }

    // ---- people --------------------------------------------------------------------------------

    /** Draws a person with the feet at (x, y), scaled by {@code s}. */
    static void person(Graphics2D g, double x, double y, double s, Look look, Pose pose) {
        boolean moving = pose.moving();
        double breathe = moving ? 0 : Math.sin(pose.time() * 2.2 + pose.seed()) * 0.9 * s;
        double bob = moving ? Math.abs(Math.sin(pose.walk())) * 2.6 * s : 0;
        y -= bob;
        // soft shadow
        g.setColor(new Color(0, 0, 0, 38));
        g.fill(new Ellipse2D.Double(x - 21 * s, y - 5 * s + bob, 42 * s, 12 * s));
        g.setColor(new Color(0, 0, 0, 30));
        g.fill(new Ellipse2D.Double(x - 15 * s, y - 3 * s + bob, 30 * s, 8 * s));
        double swing = moving ? Math.sin(pose.walk()) * 7.5 * s : 0;
        Color outline = new Color(0, 0, 0, 70);

        if (!pose.sitting()) {
            g.setStroke(new BasicStroke((float) (8.5 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(shade(look.pants(), 0.85));
            g.draw(new Line2D.Double(x + 6 * s, y - 22 * s, x + 6 * s - swing, y - 3 * s));
            g.setColor(look.pants());
            g.draw(new Line2D.Double(x - 6 * s, y - 22 * s, x - 6 * s + swing, y - 3 * s));
            g.setColor(new Color(0x2B2B2B));
            g.fill(new RoundRectangle2D.Double(x - 12 * s + swing, y - 6 * s, 11 * s, 6.5 * s, 5 * s, 5 * s));
            g.fill(new RoundRectangle2D.Double(x + 1 * s - swing, y - 6 * s, 11 * s, 6.5 * s, 5 * s, 5 * s));
        }

        // back arm
        double armSwing = moving ? -swing * 0.85 : 0;
        double lift = pose.eating() ? Math.sin(pose.eatPhase()) * 10 * s : 0;
        g.setStroke(new BasicStroke((float) (6.8 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(shade(look.shirt(), 0.78));
        g.draw(new Line2D.Double(x + 16 * s, y - 57 * s + breathe, x + 19 * s - armSwing, y - 33 * s - lift));
        g.setColor(look.skin());
        g.fill(new Ellipse2D.Double(x + 15.5 * s - armSwing, y - 37 * s - lift, 7 * s, 7 * s));

        // long hair behind the head
        if (look.hairStyle() == 2) {
            g.setColor(look.hair());
            g.fill(new RoundRectangle2D.Double(x - 15 * s, y - 86 * s + breathe, 30 * s, 34 * s, 16 * s, 16 * s));
        }

        // torso
        RoundRectangle2D torso = new RoundRectangle2D.Double(x - 17 * s, y - 64 * s + breathe, 34 * s, 44 * s - breathe,
                15 * s, 15 * s);
        g.setPaint(new GradientPaint((float) (x - 17 * s), (float) (y - 64 * s), shade(look.shirt(), 1.12),
                (float) (x + 17 * s), (float) (y - 20 * s), shade(look.shirt(), 0.86)));
        g.fill(torso);
        g.setColor(outline);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(torso);
        if (look.has(APRON)) {
            g.setColor(new Color(0x27AE60));
            g.fill(new RoundRectangle2D.Double(x - 12 * s, y - 50 * s + breathe, 24 * s, 30 * s, 9 * s, 9 * s));
            g.setColor(new Color(255, 255, 255, 90));
            g.fill(new RoundRectangle2D.Double(x - 7 * s, y - 42 * s + breathe, 14 * s, 8 * s, 4 * s, 4 * s));
        }
        if (look.has(VEST)) {
            g.setColor(new Color(0x23262B));
            g.fill(new RoundRectangle2D.Double(x - 16 * s, y - 59 * s + breathe, 12 * s, 38 * s, 7 * s, 7 * s));
            g.fill(new RoundRectangle2D.Double(x + 4 * s, y - 59 * s + breathe, 12 * s, 38 * s, 7 * s, 7 * s));
        }
        if (look.has(COLLAR) && !look.has(SCARF)) {
            g.setColor(shade(look.shirt(), 1.25));
            Path2D collar = new Path2D.Double();
            collar.moveTo(x - 7 * s, y - 64 * s + breathe);
            collar.lineTo(x, y - 55 * s + breathe);
            collar.lineTo(x + 7 * s, y - 64 * s + breathe);
            collar.closePath();
            g.fill(collar);
        }
        if (look.has(SCARF)) {
            g.setColor(new Color(0xC0392B));
            g.fill(new RoundRectangle2D.Double(x - 14 * s, y - 65 * s + breathe, 28 * s, 9 * s, 7 * s, 7 * s));
            g.fill(new RoundRectangle2D.Double(x + 4 * s, y - 62 * s + breathe, 7 * s, 19 * s, 4 * s, 4 * s));
        }

        // head
        double hy = y - 74 * s + breathe;
        g.setPaint(new GradientPaint((float) (x - 14 * s), (float) (hy - 14 * s), shade(look.skin(), 1.06),
                (float) (x + 10 * s), (float) (hy + 14 * s), shade(look.skin(), 0.9)));
        g.fill(new Ellipse2D.Double(x - 14 * s, hy - 14 * s, 28 * s, 28 * s));
        g.setColor(new Color(0, 0, 0, 45));
        g.setStroke(new BasicStroke(1.1f));
        g.draw(new Ellipse2D.Double(x - 14 * s, hy - 14 * s, 28 * s, 28 * s));
        g.setColor(shade(look.skin(), 0.93));
        g.fill(new Ellipse2D.Double(x - 15.5 * s, hy - 2 * s, 3.5 * s, 6 * s));
        g.fill(new Ellipse2D.Double(x + 12 * s, hy - 2 * s, 3.5 * s, 6 * s));
        hairFront(g, x, hy, s, look);
        face(g, x, hy, s, pose, look.has(GLASSES));

        if (look.has(BOWTIE)) {
            g.setColor(look.shirt().equals(Color.WHITE) ? new Color(0x111111) : new Color(0xF4D03F));
            Path2D bow = new Path2D.Double();
            double by = y - 58 * s + breathe;
            bow.moveTo(x, by);
            bow.lineTo(x - 8 * s, by - 5 * s);
            bow.lineTo(x - 8 * s, by + 5 * s);
            bow.closePath();
            bow.moveTo(x, by);
            bow.lineTo(x + 8 * s, by - 5 * s);
            bow.lineTo(x + 8 * s, by + 5 * s);
            bow.closePath();
            g.fill(bow);
        }

        // front arm and hand
        g.setStroke(new BasicStroke((float) (6.8 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(look.shirt());
        g.draw(new Line2D.Double(x - 16 * s, y - 57 * s + breathe, x - 19 * s + armSwing, y - 33 * s));
        g.setColor(look.skin());
        g.fill(new Ellipse2D.Double(x - 22.5 * s + armSwing, y - 37 * s, 7 * s, 7 * s));
        if (look.has(NOTEPAD)) {
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Double(x - 28 * s + armSwing, y - 45 * s, 11 * s, 14 * s, 2, 2));
            g.setColor(new Color(0x7F8C8D));
            g.setStroke(new BasicStroke(1f));
            for (int i = 0; i < 3; i++) {
                g.draw(new Line2D.Double(x - 26 * s + armSwing, y - 42 * s + i * 4 * s, x - 20 * s + armSwing,
                        y - 42 * s + i * 4 * s));
            }
        }
        if (look.has(CLIPBOARD)) {
            g.setColor(new Color(0x8D6E63));
            g.fill(new RoundRectangle2D.Double(x + 14 * s, y - 47 * s, 14 * s, 19 * s, 3, 3));
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Double(x + 16 * s, y - 44 * s, 10 * s, 14 * s, 2, 2));
            g.setColor(new Color(0xBDC3C7));
            g.setStroke(new BasicStroke(1f));
            g.draw(new Line2D.Double(x + 17.5 * s, y - 40 * s, x + 24.5 * s, y - 40 * s));
            g.draw(new Line2D.Double(x + 17.5 * s, y - 36 * s, x + 24.5 * s, y - 36 * s));
        }
    }

    private static void hairFront(Graphics2D g, double x, double hy, double s, Look look) {
        double top = hy - 14 * s;
        g.setColor(look.hair());
        // cap of hair on the top half of the head
        g.fill(new Arc2D.Double(x - 14.6 * s, top - 0.6 * s, 29.2 * s, 28 * s, 0, 180, Arc2D.CHORD));
        switch (look.hairStyle()) {
            case 1 -> {
                Path2D fringe = new Path2D.Double();
                fringe.moveTo(x - 14 * s, hy - 2 * s);
                fringe.quadTo(x - 4 * s, hy - 14 * s, x + 14 * s, hy - 8 * s);
                fringe.lineTo(x + 14 * s, hy - 2 * s);
                fringe.quadTo(x, hy - 9 * s, x - 14 * s, hy - 2 * s);
                fringe.closePath();
                g.fill(fringe);
            }
            case 3 -> g.fill(new Ellipse2D.Double(x - 6 * s, top - 9 * s, 12 * s, 12 * s));
            default -> {
            }
        }
        switch (look.hat()) {
            case CAP -> {
                g.setColor(new Color(0xE74C3C));
                g.fill(new Arc2D.Double(x - 15 * s, top - 2 * s, 30 * s, 26 * s, 0, 180, Arc2D.CHORD));
                g.setColor(new Color(0xC0392B));
                g.fill(new RoundRectangle2D.Double(x - 2 * s, hy - 5 * s, 20 * s, 4.5 * s, 4, 4));
            }
            case CROWN -> {
                g.setColor(new Color(0xF1C40F));
                Path2D crown = new Path2D.Double();
                crown.moveTo(x - 11 * s, top + 4 * s);
                crown.lineTo(x - 12 * s, top - 9 * s);
                crown.lineTo(x - 6 * s, top - 3 * s);
                crown.lineTo(x, top - 12 * s);
                crown.lineTo(x + 6 * s, top - 3 * s);
                crown.lineTo(x + 12 * s, top - 9 * s);
                crown.lineTo(x + 11 * s, top + 4 * s);
                crown.closePath();
                g.fill(crown);
                g.setColor(new Color(0xB7950B));
                g.setStroke(new BasicStroke(1f));
                g.draw(crown);
                g.setColor(new Color(0xE74C3C));
                g.fill(new Ellipse2D.Double(x - 2 * s, top - 3 * s, 4 * s, 4 * s));
            }
            case BERET -> {
                g.setColor(new Color(0x8E2430));
                g.fill(new Ellipse2D.Double(x - 17 * s, top - 6 * s, 32 * s, 15 * s));
                g.setColor(new Color(0x6E1B25));
                g.fill(new Ellipse2D.Double(x - 1 * s, top - 10 * s, 4 * s, 5 * s));
            }
            case CHEF -> {
                g.setColor(Color.WHITE);
                g.fill(new RoundRectangle2D.Double(x - 11 * s, top - 4 * s, 22 * s, 10 * s, 4, 4));
                g.fill(new Ellipse2D.Double(x - 15 * s, top - 20 * s, 14 * s, 18 * s));
                g.fill(new Ellipse2D.Double(x - 7 * s, top - 26 * s, 16 * s, 21 * s));
                g.fill(new Ellipse2D.Double(x + 1 * s, top - 20 * s, 14 * s, 18 * s));
                g.setColor(new Color(0, 0, 0, 40));
                g.setStroke(new BasicStroke(1f));
                g.draw(new RoundRectangle2D.Double(x - 11 * s, top - 4 * s, 22 * s, 10 * s, 4, 4));
            }
            default -> {
            }
        }
    }

    private static void face(Graphics2D g, double cx, double cy, double s, Pose pose, boolean glasses) {
        int mood = pose.mood();
        boolean staff = mood < 0;
        boolean angry = !staff && mood <= 33;
        boolean happy = staff || mood > 66;
        boolean blink = ((pose.time() * 0.85 + pose.seed() * 1.7) % 4.2) < 0.13;
        double eyeY = cy - 1.5 * s;
        for (int side = -1; side <= 1; side += 2) {
            double ex = cx + side * 5.4 * s;
            if (blink) {
                g.setColor(INK);
                g.setStroke(new BasicStroke((float) (1.6 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(ex - 2.6 * s, eyeY + 0.5 * s, ex + 2.6 * s, eyeY + 0.5 * s));
            } else {
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(ex - 2.9 * s, eyeY - 3.1 * s, 5.8 * s, 6.4 * s));
                g.setColor(INK);
                g.fill(new Ellipse2D.Double(ex - 1.7 * s, eyeY - 1.7 * s, 3.5 * s, 4.0 * s));
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(ex - 0.8 * s, eyeY - 1.5 * s, 1.2 * s, 1.2 * s));
            }
            // eyebrow
            g.setColor(new Color(0x3B2A20));
            g.setStroke(new BasicStroke((float) (1.7 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double inner = cx + side * 2.4 * s;
            double outer = cx + side * 8.6 * s;
            double by = eyeY - 6.2 * s;
            if (angry) {
                g.draw(new Line2D.Double(inner, by + 1.8 * s, outer, by - 1.6 * s));
            } else if (happy) {
                g.draw(new Line2D.Double(inner, by - 0.4 * s, outer, by - 0.9 * s));
            } else {
                g.draw(new Line2D.Double(inner, by + 0.6 * s, outer, by - 0.3 * s));
            }
        }
        if (glasses) {
            g.setColor(new Color(0x111111));
            g.setStroke(new BasicStroke((float) (1.5 * s)));
            g.draw(new Ellipse2D.Double(cx - 10.2 * s, eyeY - 4.6 * s, 8.8 * s, 8.8 * s));
            g.draw(new Ellipse2D.Double(cx + 1.4 * s, eyeY - 4.6 * s, 8.8 * s, 8.8 * s));
            g.draw(new Line2D.Double(cx - 1.4 * s, eyeY, cx + 1.4 * s, eyeY));
        }
        // nose
        g.setColor(new Color(0, 0, 0, 38));
        g.fill(new Ellipse2D.Double(cx - 1.3 * s, cy + 1.6 * s, 2.6 * s, 2.2 * s));
        // mouth
        double my = cy + 7 * s;
        g.setColor(INK);
        g.setStroke(new BasicStroke((float) (1.8 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (staff || mood > 80) {
            g.setColor(new Color(0x7B241C));
            g.fill(new Arc2D.Double(cx - 5 * s, my - 4.5 * s, 10 * s, 9 * s, 200, 140, Arc2D.CHORD));
            g.setColor(Color.WHITE);
            g.fill(new Arc2D.Double(cx - 3.6 * s, my - 3.2 * s, 7.2 * s, 3.4 * s, 205, 130, Arc2D.CHORD));
        } else if (mood > 66) {
            g.draw(new Arc2D.Double(cx - 5 * s, my - 4 * s, 10 * s, 8 * s, 200, 140, Arc2D.OPEN));
        } else if (mood > 33) {
            g.draw(new Line2D.Double(cx - 3.6 * s, my, cx + 3.6 * s, my + 0.4 * s));
        } else {
            g.draw(new Arc2D.Double(cx - 5 * s, my - 0.5 * s, 10 * s, 8 * s, 20, 140, Arc2D.OPEN));
        }
        if (happy) {
            g.setColor(new Color(255, 120, 120, 70));
            g.fill(new Ellipse2D.Double(cx - 12 * s, cy + 2.5 * s, 6.5 * s, 4.5 * s));
            g.fill(new Ellipse2D.Double(cx + 5.5 * s, cy + 2.5 * s, 6.5 * s, 4.5 * s));
        } else if (angry) {
            g.setColor(new Color(231, 76, 60, 95));
            g.fill(new Ellipse2D.Double(cx - 12 * s, cy + 2 * s, 7 * s, 5 * s));
            g.fill(new Ellipse2D.Double(cx + 5 * s, cy + 2 * s, 7 * s, 5 * s));
        }
    }

    /** Just the head, for button icons. */
    static void head(Graphics2D g, double cx, double cy, double s, Look look, int mood) {
        Look plain = look.withoutProps();
        g.setPaint(new GradientPaint((float) (cx - 14 * s), (float) (cy - 14 * s), shade(look.skin(), 1.06),
                (float) (cx + 10 * s), (float) (cy + 14 * s), shade(look.skin(), 0.9)));
        g.fill(new Ellipse2D.Double(cx - 14 * s, cy - 14 * s, 28 * s, 28 * s));
        hairFront(g, cx, cy, s, plain);
        face(g, cx, cy, s, Pose.idle(0.5, mood, 3), false);
    }

    // ---- status widgets ----------------------------------------------------------------------

    /** Satisfaction bar, 0..100, centred on x, with a short text (for example the number) inside it. */
    static void satisfactionBar(Graphics2D g, double x, double y, double value, String text) {
        double w = 50;
        double h = 12;
        g.setColor(new Color(0, 0, 0, 130));
        g.fill(new RoundRectangle2D.Double(x - w / 2 - 1.5, y - 1.5, w + 3, h + 3, 10, 10));
        g.setColor(new Color(0x39424E));
        g.fill(new RoundRectangle2D.Double(x - w / 2, y, w, h, 8, 8));
        Color c = value > 66 ? new Color(0x2ECC71) : value > 33 ? new Color(0xF5B041) : new Color(0xE74C3C);
        g.setPaint(new GradientPaint(0, (float) y, c.brighter(), 0, (float) (y + h), c.darker()));
        g.fill(new RoundRectangle2D.Double(x - w / 2, y, Math.max(3, w * value / 100.0), h, 8, 8));
        if (text != null) {
            centredText(g, text, x + 1, y + 9.5, new Font(Font.SANS_SERIF, Font.BOLD, 10), new Color(0, 0, 0, 170));
            centredText(g, text, x, y + 9, new Font(Font.SANS_SERIF, Font.BOLD, 10), Color.WHITE);
        }
    }

    /** Speech bubble with its tail pointing down at (cx, bottom). Content is drawn by the caller. */
    static void bubble(Graphics2D g, double cx, double bottom, double w, double h, Color fill) {
        RoundRectangle2D box = new RoundRectangle2D.Double(cx - w / 2, bottom - h - 7, w, h, 14, 14);
        Path2D tail = new Path2D.Double();
        tail.moveTo(cx - 5, bottom - 8);
        tail.lineTo(cx, bottom);
        tail.lineTo(cx + 5, bottom - 8);
        tail.closePath();
        g.setColor(new Color(0, 0, 0, 40));
        g.fill(new RoundRectangle2D.Double(cx - w / 2 + 2, bottom - h - 5, w, h, 14, 14));
        g.setColor(fill);
        g.fill(box);
        g.fill(tail);
        g.setColor(new Color(0, 0, 0, 70));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(box);
    }

    static void centredText(Graphics2D g, String text, double cx, double baseline, Font font, Color color) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(color);
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) baseline);
    }

    /** A little pill label (for staff roles, table numbers). */
    static void tag(Graphics2D g, String text, double cx, double cy, Color bg, Color fg) {
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, 10);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        double w = fm.stringWidth(text) + 12;
        g.setColor(bg);
        g.fill(new RoundRectangle2D.Double(cx - w / 2, cy - 8, w, 16, 16, 16));
        g.setColor(fg);
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) (cy + 4));
    }

    // ---- food ----------------------------------------------------------------------------------

    /** A dish on a plate, centred at (cx, cy), roughly 2r wide. Combos show both dishes. */
    static void dish(Graphics2D g, double cx, double cy, double r, MenuItem item, boolean plate) {
        if (item instanceof ComboMeal combo) {
            dish(g, cx - r * 0.55, cy, r * 0.72, combo.getFirst(), plate);
            dish(g, cx + r * 0.55, cy, r * 0.72, combo.getSecond(), plate);
            return;
        }
        if (plate) {
            g.setColor(new Color(0, 0, 0, 40));
            g.fill(new Ellipse2D.Double(cx - r, cy - r * 0.45 + 2, r * 2, r * 1.1));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(cx - r, cy - r * 0.55, r * 2, r * 1.1));
            g.setColor(new Color(0xD5D8DC));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Ellipse2D.Double(cx - r, cy - r * 0.55, r * 2, r * 1.1));
            g.draw(new Ellipse2D.Double(cx - r * 0.7, cy - r * 0.38, r * 1.4, r * 0.76));
        }
        String name = item.getName();
        double k = r / 16.0;
        if (name.contains("Salad")) {
            Color[] greens = {new Color(0x27AE60), new Color(0x58D68D), new Color(0x1E8449)};
            for (int i = 0; i < 6; i++) {
                g.setColor(greens[i % 3]);
                double a = i * Math.PI / 3;
                g.fill(new Ellipse2D.Double(cx + Math.cos(a) * 6 * k - 5 * k, cy - 2 * k + Math.sin(a) * 3 * k - 4 * k,
                        10 * k, 8 * k));
            }
            g.setColor(new Color(0xE74C3C));
            g.fill(new Ellipse2D.Double(cx - 2 * k, cy - 6 * k, 5 * k, 5 * k));
            g.fill(new Ellipse2D.Double(cx + 5 * k, cy - 1 * k, 4 * k, 4 * k));
        } else if (name.contains("Burger")) {
            g.setColor(new Color(0xD68910));
            g.fill(new Arc2D.Double(cx - 10 * k, cy - 12 * k, 20 * k, 16 * k, 0, 180, Arc2D.CHORD));
            g.setColor(new Color(0x2ECC71));
            g.fill(new RoundRectangle2D.Double(cx - 11 * k, cy - 3 * k, 22 * k, 3.5 * k, 4, 4));
            g.setColor(new Color(0xF1C40F));
            g.fill(new RoundRectangle2D.Double(cx - 9 * k, cy - 0.5 * k, 18 * k, 2.5 * k, 2, 2));
            g.setColor(new Color(0x6E2C00));
            g.fill(new RoundRectangle2D.Double(cx - 10 * k, cy + 1.5 * k, 20 * k, 4 * k, 4, 4));
            g.setColor(new Color(0xE0A030));
            g.fill(new RoundRectangle2D.Double(cx - 10 * k, cy + 5 * k, 20 * k, 4 * k, 5, 5));
            g.setColor(new Color(255, 255, 255, 180));
            for (int i = -1; i <= 1; i++) {
                g.fill(new Ellipse2D.Double(cx + i * 5 * k - 0.8 * k, cy - 8 * k + Math.abs(i) * 1.5 * k, 1.6 * k, 1 * k));
            }
        } else if (name.contains("Pasta")) {
            g.setColor(new Color(0xF4D03F));
            g.setStroke(new BasicStroke((float) (2.2 * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 4; i++) {
                g.draw(new Arc2D.Double(cx - (12 - i * 2) * k, cy - (6 - i * 1.2) * k, (24 - i * 4) * k,
                        (12 - i * 2.4) * k, i * 40, 280, Arc2D.OPEN));
            }
            g.setColor(new Color(0xC0392B));
            g.fill(new Ellipse2D.Double(cx - 4 * k, cy - 3 * k, 8 * k, 5 * k));
            g.setColor(new Color(0x27AE60));
            g.fill(new Ellipse2D.Double(cx - 1 * k, cy - 5 * k, 3 * k, 2.5 * k));
        } else {
            g.setColor(new Color(0xBDC3C7));
            g.fill(new Ellipse2D.Double(cx - 8 * k, cy - 5 * k, 16 * k, 10 * k));
        }
    }

    static void steam(Graphics2D g, double cx, double cy, double t) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = -1; i <= 1; i++) {
            double ph = t * 2 + i;
            double a = 0.35 + 0.25 * Math.sin(ph);
            g.setColor(new Color(255, 255, 255, (int) (a * 255)));
            Path2D p = new Path2D.Double();
            double bx = cx + i * 6;
            p.moveTo(bx, cy);
            p.curveTo(bx + 4, cy - 6, bx - 4, cy - 10, bx + 1, cy - 16 - 2 * Math.sin(ph));
            g.draw(p);
        }
    }

    static GradientPaint vertical(double y0, double y1, Color a, Color b) {
        return new GradientPaint(0, (float) y0, a, 0, (float) y1, b);
    }

    static int round(double v) {
        return (int) Math.round(v);
    }
}
