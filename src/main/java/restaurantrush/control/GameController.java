package restaurantrush.control;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import restaurantrush.control.GameResult.Outcome;
import restaurantrush.entity.Customer;
import restaurantrush.entity.HappyHour;
import restaurantrush.entity.InvalidActionException;
import restaurantrush.entity.Order;
import restaurantrush.entity.Payment;
import restaurantrush.entity.Restaurant;
import restaurantrush.entity.Table;
import restaurantrush.util.Money;

/**
 * Runs the turn sequence and holds no display code. The boundary (console UI) calls these
 * methods in order: {@link #beginTurn()}, one Waiter action, one Chef action, {@link #endTurn()}.
 *
 * <p>Steps of a turn: 1 arrivals ({@code beginTurn}), 2 manager choices and 3 kitchen and serving
 * (Host if there is one, then Waiter, then Chef, so a dish finished this turn cannot be served until
 * next turn),
 * 4 eating and payment, 5 waiting and abandonment ({@code endTurn}), 6 result ({@link #getResult()}).
 */
public class GameController {

    /** Where we are inside a turn. Calls made in the wrong phase are rejected, not guessed. */
    public enum Phase {
        AWAITING_TURN_START, HOST, WAITER, CHEF, READY_TO_END, FINISHED
    }

    private final GameConfig config;
    private final Restaurant restaurant;
    private int turn = 0;
    private int nextCustomerId = 1;
    private Phase phase = Phase.AWAITING_TURN_START;
    /** True once any staff member has acted this turn (Happy Hour can only start before that). */
    private boolean choicesStarted;

    public GameController(GameConfig config) {
        this.config = config;
        this.restaurant = new Restaurant(config.getMenu(), config.getTableCount(), config.isHappyHourEnabled());
    }

    // ---- read-only access for the boundary ---------------------------------------------

    public GameConfig getConfig() {
        return config;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public int getTurn() {
        return turn;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isGameOver() {
        return phase == Phase.FINISHED;
    }

    // ---- step 1: arrivals ------------------------------------------------------------

    public boolean isHostEnabled() {
        return config.isHostEnabled();
    }

    public HappyHour getHappyHour() {
        return restaurant.getHappyHour();
    }

    /** Starts the next turn and returns the start-of-turn messages (Happy Hour recovery, arrivals). */
    public List<String> beginTurn() {
        requirePhase(Phase.AWAITING_TURN_START);
        turn++;
        choicesStarted = false;
        restaurant.startStaffTurn();
        List<String> arrivals = new ArrayList<>();
        if (getHappyHour().isActive()) {
            arrivals.add(describeHappyHourRecovery());
        }
        for (GameConfig.ArrivalSlot slot : config.getArrivals()) {
            if (slot.turn() == turn) {
                Customer customer = slot.factory().apply(nextCustomerId++);
                restaurant.addCustomer(customer);
                arrivals.add("Customer " + customer + " arrives and joins the waiting queue (satisfaction "
                        + customer.getSatisfaction() + ").");
            }
        }
        phase = config.isHostEnabled() ? Phase.HOST : Phase.WAITER;
        return arrivals;
    }

    // ---- Happy Hour (Stage 3): only at the very start of a turn ---------------------------

    /**
     * Switches Happy Hour on for this turn and the next. Allowed once per game, and only before any
     * staff member has acted this turn. Waiting customers recover satisfaction immediately.
     */
    public ActionResult activateHappyHour() {
        if (phase != Phase.HOST && phase != Phase.WAITER || choicesStarted) {
            return ActionResult.fail("Happy Hour can only be switched on at the start of a turn,"
                    + " before any staff member acts.");
        }
        try {
            getHappyHour().activate();
        } catch (InvalidActionException e) {
            return ActionResult.fail(e.getMessage());
        }
        return ActionResult.ok("Happy Hour is ON for this turn and the next: new orders get "
                + HappyHour.DISCOUNT_PERCENT + "% off. " + describeHappyHourRecovery());
    }

    private String describeHappyHourRecovery() {
        List<Customer> recovered = restaurant.applyHappyHourRecovery();
        String who = recovered.isEmpty() ? "nobody is waiting"
                : recovered.stream().map(Customer::toString).toList().toString();
        return "Happy Hour (" + getHappyHour().getRemainingTurns() + " turn(s) left): waiting customers recover "
                + HappyHour.RECOVERY_POINTS + " satisfaction (max 100) - " + who + ".";
    }

    // ---- step 2/3: Host (Stage 3, acts before the Waiter) -------------------------------------

    public ActionResult hostWait() {
        return performHost(() -> {
            restaurant.getHost().rest();
            return "The Host waits.";
        });
    }

    public ActionResult hostSeat(int customerId) {
        return performHost(() -> {
            Customer customer = requireCustomer(customerId);
            Table table = restaurant.getHost().seat(customer, restaurant.getTables());
            return "The Host seats customer " + customer + " at table " + table.getNumber() + ".";
        });
    }

    // ---- step 2/3: Waiter (acts first) --------------------------------------------------

    public ActionResult waiterWait() {
        return performWaiter(() -> {
            restaurant.getWaiter().rest();
            return "The Waiter waits.";
        });
    }

    public ActionResult waiterSeat(int customerId) {
        return performWaiter(() -> {
            Customer customer = requireCustomer(customerId);
            Table table = restaurant.getWaiter().seat(customer, restaurant.getTables());
            return "The Waiter seats customer " + customer + " at table " + table.getNumber() + ".";
        });
    }

    public ActionResult waiterTakeOrder(int customerId) {
        return performWaiter(() -> {
            Customer customer = requireCustomer(customerId);
            Order order = restaurant.getWaiter().takeOrder(customer, restaurant.getMenu(), getHappyHour());
            int listPrice = order.getItem().getPriceCents();
            String price = Money.format(order.getBillCents())
                    + (order.getBillCents() < listPrice ? ", list price " + Money.format(listPrice) : "");
            return "The Waiter takes the order of customer " + customer + ": " + order.getItem().getName()
                    + " (" + price + ", " + order.getItem().getPrepUnits() + " prep unit(s)).";
        });
    }

    public ActionResult waiterServe(int customerId) {
        return performWaiter(() -> {
            Customer customer = requireCustomer(customerId);
            Order order = restaurant.getWaiter().serve(customer, turn);
            return "The Waiter serves " + order.getItem().getName() + " to customer " + customer + ".";
        });
    }

    // ---- step 2/3: Chef (acts after the Waiter) -------------------------------------------

    public ActionResult chefWait() {
        return performChef(() -> {
            restaurant.getChef().rest();
            return "The Chef waits.";
        });
    }

    public ActionResult chefPrepare(int customerId) {
        return performChef(() -> {
            Customer customer = requireCustomer(customerId);
            Order order = restaurant.getChef().prepare(customer, turn);
            String progress = order.getPreparedUnits() + "/" + order.getItem().getPrepUnits();
            String suffix = order.getRemainingUnits() == 0
                    ? " The dish is READY (it can be served next turn at the earliest)." : "";
            return "The Chef prepares " + order.getItem().getName() + " for customer " + customer
                    + " (unit " + progress + ")." + suffix;
        });
    }

    // ---- steps 4 and 5: automatic ----------------------------------------------------------

    /** Eating and payment, then waiting and abandonment. */
    public TurnSummary endTurn() {
        requirePhase(Phase.READY_TO_END);
        List<String> events = new ArrayList<>();

        // Step 4: pay first (only customers ready BEFORE this turn), then let diners finish eating.
        restaurant.collectPayment(turn).ifPresent(p -> events.add(describePayment(p)));
        for (Customer customer : restaurant.finishEating(turn)) {
            events.add("Customer " + customer + " finishes eating and is ready to pay.");
        }

        // Step 5: unserved customers lose satisfaction; anyone at 0 leaves.
        for (Customer customer : restaurant.applyWaitingLoss()) {
            events.add("Customer " + customer + " loses patience and leaves without paying"
                    + " (order cancelled, table freed).");
        }

        getHappyHour().endTurn();

        TurnSummary summary = new TurnSummary(turn, events);
        phase = turn >= config.getMaxTurns() ? Phase.FINISHED : Phase.AWAITING_TURN_START;
        return summary;
    }

    private String describePayment(Payment p) {
        return "The Cashier collects " + Money.format(p.amountCents()) + " from customer " + p.customer()
                + " (satisfaction " + p.satisfaction() + " recorded). Table " + p.tableNumber() + " is free.";
    }

    // ---- step 6: result --------------------------------------------------------------------

    public GameResult getResult() {
        requirePhase(Phase.FINISHED);
        int paid = restaurant.getPaidCount();
        int revenue = restaurant.getRevenueCents();
        int satisfactionSum = restaurant.getPaidSatisfactionSum();
        boolean paidMet = paid >= config.getRequiredPaidCustomers();
        boolean revenueMet = revenue >= config.getRequiredRevenueCents();
        // Integer comparison of the average: sum / paid >= required  <=>  sum >= required * paid.
        boolean satisfactionMet = paid > 0
                && satisfactionSum >= config.getRequiredAverageSatisfaction() * paid;
        Outcome outcome = paidMet && revenueMet && satisfactionMet ? Outcome.VICTORY : Outcome.DEFEAT;
        return new GameResult(outcome, paid, revenue, satisfactionSum, restaurant.getServedCount(),
                restaurant.getUnhappyDepartures(), turn, paidMet, revenueMet, satisfactionMet);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private ActionResult performHost(Supplier<String> action) {
        if (phase != Phase.HOST) {
            return ActionResult.fail(config.isHostEnabled()
                    ? "The Host has already acted this turn."
                    : "There is no Host in this setting.");
        }
        try {
            String message = action.get();
            choicesStarted = true;
            phase = Phase.WAITER;
            return ActionResult.ok(message);
        } catch (InvalidActionException e) {
            return ActionResult.fail(e.getMessage());
        }
    }

    private ActionResult performWaiter(Supplier<String> action) {
        if (phase == Phase.HOST) {
            return ActionResult.fail("The Host acts before the Waiter - choose the Host's task first.");
        }
        if (phase != Phase.WAITER) {
            return ActionResult.fail("It is not the Waiter's turn to act (phase " + phase + ").");
        }
        try {
            String message = action.get();
            choicesStarted = true;
            phase = Phase.CHEF;
            return ActionResult.ok(message);
        } catch (InvalidActionException e) {
            return ActionResult.fail(e.getMessage());
        }
    }

    private ActionResult performChef(Supplier<String> action) {
        if (phase != Phase.CHEF) {
            return ActionResult.fail(phase == Phase.WAITER || phase == Phase.HOST
                    ? "The Waiter acts before the Chef - choose the Waiter task first."
                    : "It is not the Chef's turn to act (phase " + phase + ").");
        }
        try {
            String message = action.get();
            phase = Phase.READY_TO_END;
            return ActionResult.ok(message);
        } catch (InvalidActionException e) {
            return ActionResult.fail(e.getMessage());
        }
    }

    private Customer requireCustomer(int customerId) {
        return restaurant.findCustomer(customerId)
                .orElseThrow(() -> new InvalidActionException("There is no customer with id " + customerId + "."));
    }

    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Expected phase " + expected + " but was " + phase);
        }
    }
}
