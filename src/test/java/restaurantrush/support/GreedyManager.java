package restaurantrush.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import restaurantrush.control.GameController;
import restaurantrush.entity.Customer;
import restaurantrush.entity.Order;
import restaurantrush.entity.Restaurant;

/**
 * A simple automatic "manager" used by the tests and to generate the reproducible scripts in
 * {@code scripts/}. It plays through the same {@link GameController} API as the console UI and
 * records the exact lines a human would type, so the script can be piped into the game.
 *
 * <p>Strategy: the Host (Stage 3) seats the least satisfied queued customer; the Waiter serves a
 * READY dish first, then takes an order, then seats a queued customer; the Chef works on the order
 * with the fewest units left. Happy Hour is switched on only when the supplied policy says so.
 */
public class GreedyManager {

    /** Never switch Happy Hour on. */
    public static final Predicate<GameController> NO_HAPPY_HOUR = game -> false;

    /** Switch Happy Hour on at the start of the turn a VIP is waiting to order (the brief's example). */
    public static final Predicate<GameController> WHEN_A_VIP_IS_WAITING = game ->
            game.getRestaurant().getCustomers().stream().anyMatch(c ->
                    c.getTypeName().equals("VIP") && c.getOrder() == null && c.getState().isInRestaurant());

    private final List<String> keystrokes = new ArrayList<>();

    /** Plays a whole game and returns the lines typed. */
    public static List<String> playToEnd(GameController game) {
        return playToEnd(game, NO_HAPPY_HOUR);
    }

    public static List<String> playToEnd(GameController game, Predicate<GameController> happyHourPolicy) {
        GreedyManager manager = new GreedyManager();
        manager.play(game, happyHourPolicy);
        return manager.keystrokes;
    }

    private void play(GameController game, Predicate<GameController> happyHourPolicy) {
        while (!game.isGameOver()) {
            game.beginTurn();
            if (game.getHappyHour().isAvailable()) {
                boolean activate = happyHourPolicy.test(game);
                type(activate ? "1" : "0");
                if (activate) {
                    check(game.activateHappyHour().success());
                }
            }
            if (game.isHostEnabled()) {
                hostAction(game);
            }
            waiterAction(game);
            chefAction(game);
            game.endTurn();
        }
    }

    private void hostAction(GameController game) {
        Restaurant r = game.getRestaurant();
        if (!r.queuedCustomers().isEmpty() && r.hasFreeTable()) {
            Customer c = lowestSatisfaction(r.queuedCustomers());
            type("1", String.valueOf(c.getId()));
            check(game.hostSeat(c.getId()).success());
            return;
        }
        type("0");
        check(game.hostWait().success());
    }

    private void waiterAction(GameController game) {
        Restaurant r = game.getRestaurant();
        List<Order> servable = r.servableOrders(game.getTurn());
        if (!servable.isEmpty()) {
            Customer c = lowestSatisfaction(servable.stream().map(Order::getCustomer).toList());
            type("3", String.valueOf(c.getId()));
            check(game.waiterServe(c.getId()).success());
            return;
        }
        if (!r.customersAwaitingOrder().isEmpty()) {
            Customer c = lowestSatisfaction(r.customersAwaitingOrder());
            type("2", String.valueOf(c.getId()));
            check(game.waiterTakeOrder(c.getId()).success());
            return;
        }
        if (!r.queuedCustomers().isEmpty() && r.hasFreeTable()) {
            Customer c = lowestSatisfaction(r.queuedCustomers());
            type("1", String.valueOf(c.getId()));
            check(game.waiterSeat(c.getId()).success());
            return;
        }
        type("0");
        check(game.waiterWait().success());
    }

    private void chefAction(GameController game) {
        List<Order> preparable = game.getRestaurant().preparableOrders();
        if (preparable.isEmpty()) {
            type("0");
            check(game.chefWait().success());
            return;
        }
        Order next = preparable.stream()
                .min(Comparator.comparingInt(Order::getRemainingUnits)
                        .thenComparingInt(o -> o.getCustomer().getSatisfaction())
                        .thenComparingInt(o -> o.getCustomer().getId()))
                .orElseThrow();
        type("1", String.valueOf(next.getCustomer().getId()));
        check(game.chefPrepare(next.getCustomer().getId()).success());
    }

    private static Customer lowestSatisfaction(List<Customer> customers) {
        return customers.stream()
                .min(Comparator.comparingInt(Customer::getSatisfaction).thenComparingInt(Customer::getId))
                .orElseThrow();
    }

    private void type(String... lines) {
        keystrokes.addAll(List.of(lines));
    }

    private static void check(boolean success) {
        if (!success) {
            throw new IllegalStateException("The greedy manager attempted an invalid action");
        }
    }
}
