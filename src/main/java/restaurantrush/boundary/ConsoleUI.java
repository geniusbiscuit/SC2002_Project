package restaurantrush.boundary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

import restaurantrush.control.ActionResult;
import restaurantrush.control.GameController;
import restaurantrush.control.GameResult;
import restaurantrush.control.TurnSummary;
import restaurantrush.entity.ComboMeal;
import restaurantrush.entity.Customer;
import restaurantrush.entity.HappyHour;
import restaurantrush.entity.Order;
import restaurantrush.entity.Restaurant;
import restaurantrush.entity.Table;
import restaurantrush.util.Money;

/**
 * Command-line boundary. It only collects the manager's decisions and displays state; every
 * rule lives in the entity and control classes. Invalid choices are reported by the controller
 * and the same question is asked again.
 */
public class ConsoleUI {

    private final GameController game;
    private final BufferedReader in;
    private final PrintStream out;
    private final boolean echoInput;

    /**
     * @param echoInput print each line read, so a transcript of piped (scripted) input is readable
     */
    public ConsoleUI(GameController game, InputStream input, PrintStream out, boolean echoInput) {
        this.game = game;
        this.in = new BufferedReader(new InputStreamReader(input));
        this.out = out;
        this.echoInput = echoInput;
    }

    public void run() {
        out.println("RESTAURANT RUSH - you are the manager. Staff time is limited: choose wisely.");
        out.println("Goal after turn " + game.getConfig().getMaxTurns() + ": at least "
                + game.getConfig().getRequiredPaidCustomers() + " paid customers, revenue of at least "
                + Money.format(game.getConfig().getRequiredRevenueCents()) + " and average satisfaction of at least "
                + game.getConfig().getRequiredAverageSatisfaction() + ".");
        showCombos();
        while (!game.isGameOver()) {
            playTurn();
        }
        showResult(game.getResult());
    }

    // ---- one turn ------------------------------------------------------------------------

    private void showCombos() {
        if (game.getConfig().getMenu().getCombos().isEmpty()) {
            return;
        }
        out.println("Combos on the menu (two dishes, 10% off the pair):");
        for (ComboMeal combo : game.getConfig().getMenu().getCombos()) {
            out.println("  " + combo.getName() + "  " + Money.format(combo.getPriceCents())
                    + "  (" + combo.getPrepUnits() + " prep units)");
        }
    }

    private void playTurn() {
        List<String> arrivals = game.beginTurn();
        out.println();
        out.println("=========================== TURN " + game.getTurn() + " of "
                + game.getConfig().getMaxTurns() + " ===========================");
        arrivals.forEach(out::println);
        showState();
        offerHappyHour();
        if (game.isHostEnabled()) {
            hostPhase();
            Restaurant r = game.getRestaurant();
            out.println("After the Host - Waiter can: seat " + ids(r.queuedCustomers(), r.hasFreeTable())
                    + " | take order " + ids(r.customersAwaitingOrder(), true)
                    + " | serve " + orderIds(r.servableOrders(game.getTurn())));
        }
        waiterPhase();
        chefPhase();
        TurnSummary summary = game.endTurn();
        out.println("--- End of turn " + summary.turn() + " ---");
        summary.events().forEach(out::println);
        showProgress();
    }

    // ---- display -------------------------------------------------------------------------

    private void showState() {
        Restaurant r = game.getRestaurant();
        out.println("Revenue: " + Money.format(r.getRevenueCents()) + " | Paid customers: " + r.getPaidCount()
                + " | Average satisfaction (paid): " + formatAverage(r.getAverageSatisfaction()));
        showHappyHourStatus();
        out.println("Waiting queue: " + (r.queuedCustomers().isEmpty() ? "empty"
                : r.queuedCustomers().stream()
                        .map(c -> c + " (satisfaction " + c.getSatisfaction() + ")")
                        .collect(Collectors.joining(", "))));
        out.println("Tables: " + r.getTables().stream().map(this::describeTable).collect(Collectors.joining(" | ")));
        out.println("Customers in restaurant:");
        if (r.customersInside().isEmpty()) {
            out.println("  (none)");
        }
        for (Customer c : r.customersInside()) {
            out.println(String.format("  %-12s satisfaction %3d  %-17s order: %s",
                    c, c.getSatisfaction(), c.getState(), describeOrder(c.getOrder())));
        }
        out.println("Staff tasks available this turn:");
        if (game.isHostEnabled()) {
            out.println("  Host:   seat " + ids(r.queuedCustomers(), r.hasFreeTable()) + " (acts before the Waiter)");
        }
        out.println("  Waiter: seat " + ids(r.queuedCustomers(), r.hasFreeTable())
                + " | take order " + ids(r.customersAwaitingOrder(), true)
                + " | serve " + orderIds(r.servableOrders(game.getTurn())));
        out.println("  Chef:   prepare " + orderIds(r.preparableOrders()));
        out.println("  Cashier: automatic (one payment per turn, oldest ready first)");
    }

    private void showHappyHourStatus() {
        HappyHour happyHour = game.getHappyHour();
        if (!happyHour.isEnabled()) {
            return;
        }
        if (happyHour.isActive()) {
            out.println("Happy Hour: ACTIVE - " + happyHour.getRemainingTurns() + " turn(s) remaining including this one ("
                    + HappyHour.DISCOUNT_PERCENT + "% off new orders; waiting customers recover "
                    + HappyHour.RECOVERY_POINTS + " satisfaction at turn start)");
        } else if (happyHour.isAvailable()) {
            out.println("Happy Hour: available (once per game, lasts 2 turns)");
        } else {
            out.println("Happy Hour: already used");
        }
    }

    private String describeTable(Table t) {
        return "T" + t.getNumber() + (t.isFree() ? " free" : " " + t.getOccupant());
    }

    private String describeOrder(Order o) {
        if (o == null) {
            return "-";
        }
        String progress = o.getItem().getPrepUnits() > 1 && o.isPreparable()
                ? " " + o.getPreparedUnits() + "/" + o.getItem().getPrepUnits() : "";
        return o.getItem().getName() + " " + o.getStatus() + progress;
    }

    private String ids(List<Customer> customers, boolean available) {
        if (!available || customers.isEmpty()) {
            return "[none]";
        }
        return customers.stream().map(c -> "#" + c.getId()).collect(Collectors.joining(",", "[", "]"));
    }

    private String orderIds(List<Order> orders) {
        if (orders.isEmpty()) {
            return "[none]";
        }
        return orders.stream().map(o -> "#" + o.getCustomer().getId()).collect(Collectors.joining(",", "[", "]"));
    }

    private void showProgress() {
        Restaurant r = game.getRestaurant();
        out.println("Results so far: served " + r.getServedCount() + " | unhappy departures "
                + r.getUnhappyDepartures() + " | revenue " + Money.format(r.getRevenueCents())
                + " | average satisfaction " + formatAverage(r.getAverageSatisfaction())
                + " | turns used " + game.getTurn() + "/" + game.getConfig().getMaxTurns());
    }

    private void showResult(GameResult result) {
        int avgNeeded = game.getConfig().getRequiredAverageSatisfaction();
        out.println();
        out.println("=============================== GAME OVER ===============================");
        out.println(result.outcome());
        out.println(mark(result.paidTargetMet()) + " Paid customers: " + result.paidCount()
                + " (need " + game.getConfig().getRequiredPaidCustomers() + ")");
        out.println(mark(result.revenueTargetMet()) + " Revenue: " + Money.format(result.revenueCents())
                + " (need " + Money.format(game.getConfig().getRequiredRevenueCents()) + ")");
        out.println(mark(result.satisfactionTargetMet()) + " Average satisfaction of paid customers: "
                + formatAverage(result.averageSatisfaction()) + " (need " + avgNeeded + ")");
        out.println("Served: " + result.servedCount() + " | Unhappy departures: " + result.unhappyDepartures()
                + " | Turns used: " + result.turnsUsed());
    }

    private static String mark(boolean met) {
        return met ? "[OK]  " : "[MISS]";
    }

    private static String formatAverage(OptionalDouble average) {
        return average.isPresent() ? String.format("%.1f", average.getAsDouble()) : "N/A";
    }

    // ---- manager choices -------------------------------------------------------------------

    private void offerHappyHour() {
        if (!game.getHappyHour().isAvailable()) {
            return;
        }
        out.println("Happy Hour is available (once per game; this turn and the next: "
                + HappyHour.DISCOUNT_PERCENT + "% off new orders, waiting customers recover "
                + HappyHour.RECOVERY_POINTS + " satisfaction at turn start).");
        out.println("Switch it on now?  1) Yes  0) No");
        if (readChoice("Happy Hour", Set.of(0, 1)) == 1) {
            ActionResult result = game.activateHappyHour();
            out.println((result.success() ? "  -> " : "  ! Not allowed: ") + result.message());
            if (result.success()) {
                showState();
            }
        }
    }

    private void hostPhase() {
        while (true) {
            out.println("Host task:  1) Seat a queued customer  0) Wait");
            ActionResult result = switch (readChoice("Host", Set.of(0, 1))) {
                case 1 -> game.hostSeat(readInt("Customer id to seat"));
                default -> game.hostWait();
            };
            if (report(result)) {
                return;
            }
        }
    }

    private void waiterPhase() {
        while (true) {
            out.println("Waiter task:  1) Seat a queued customer  2) Take an order  3) Serve a READY order  0) Wait");
            ActionResult result = switch (readChoice("Waiter", Set.of(0, 1, 2, 3))) {
                case 1 -> game.waiterSeat(readInt("Customer id to seat"));
                case 2 -> game.waiterTakeOrder(readInt("Customer id to take the order from"));
                case 3 -> game.waiterServe(readInt("Customer id whose order to serve"));
                default -> game.waiterWait();
            };
            if (report(result)) {
                return;
            }
        }
    }

    private void chefPhase() {
        // The Waiter has already acted, so an order taken this turn is already on the Chef's list.
        out.println("Orders the Chef can prepare now: " + orderIds(game.getRestaurant().preparableOrders()));
        while (true) {
            out.println("Chef task:  1) Prepare one unit of an order  0) Wait");
            ActionResult result = switch (readChoice("Chef", Set.of(0, 1))) {
                case 1 -> game.chefPrepare(readInt("Customer id whose order to prepare"));
                default -> game.chefWait();
            };
            if (report(result)) {
                return;
            }
        }
    }

    /** Prints the outcome; returns true when the staff task was used. */
    private boolean report(ActionResult result) {
        if (result.success()) {
            out.println("  -> " + result.message());
            return true;
        }
        out.println("  ! Not allowed: " + result.message() + " Your staff task was not used - choose again.");
        return false;
    }

    // ---- input -------------------------------------------------------------------------------

    private int readChoice(String who, Set<Integer> allowed) {
        while (true) {
            int value = readInt(who + " choice");
            if (allowed.contains(value)) {
                return value;
            }
            out.println("  ! Please choose one of " + allowed.stream().sorted().toList() + ".");
        }
    }

    private int readInt(String prompt) {
        while (true) {
            out.print(prompt + "> ");
            out.flush();
            String line = readLine();
            try {
                return Integer.parseInt(line.trim());
            } catch (NumberFormatException e) {
                out.println("  ! '" + line.trim() + "' is not a whole number. Please try again.");
            }
        }
    }

    private String readLine() {
        try {
            String line = in.readLine();
            if (line == null) {
                throw new InputEndedException();
            }
            if (echoInput) {
                out.println(line);
            }
            return line;
        } catch (IOException e) {
            throw new InputEndedException();
        }
    }
}
