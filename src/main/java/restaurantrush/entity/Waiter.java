package restaurantrush.entity;

import java.util.Collection;

/**
 * The Waiter completes at most one task per turn: seat a queued customer, take one seated
 * customer's order, or serve one READY order. Every method validates the rule first and only
 * then changes state and marks the Waiter as having acted.
 */
public class Waiter extends Staff {

    public Waiter() {
        super("Waiter");
    }

    /** Seats the customer at the lowest-numbered free table (same rules as the Host). */
    public Table seat(Customer customer, Collection<Table> tables) {
        ensureAvailable();
        Table table = Seating.seat(customer, tables);
        markActed();
        return table;
    }

    /**
     * The customer chooses the dish; the bill is locked now: the customer's own price first (for
     * example the VIP discount), then Happy Hour if it is active.
     */
    public Order takeOrder(Customer customer, Menu menu, HappyHour happyHour) {
        ensureAvailable();
        if (customer.getState() != CustomerState.SEATED) {
            throw new InvalidActionException("Customer #" + customer.getId()
                    + " is not seated waiting to order (currently " + customer.getState() + ").");
        }
        MenuItem item = customer.chooseItem(menu);
        Order order = customer.placeOrder(item, happyHour.priceFor(customer.billFor(item)));
        markActed();
        return order;
    }

    /** Serves a dish that was already READY before this turn. */
    public Order serve(Customer customer, int turn) {
        ensureAvailable();
        Order order = customer.getOrder();
        if (order == null || customer.getState() != CustomerState.AWAITING_FOOD) {
            throw new InvalidActionException("Customer #" + customer.getId()
                    + " has no order waiting to be served (currently " + customer.getState() + ").");
        }
        if (!order.isServableOn(turn)) {
            throw new InvalidActionException("The order of customer #" + customer.getId()
                    + " is not READY to serve yet (status " + order.getStatus() + ").");
        }
        customer.startEating(turn);
        markActed();
        return order;
    }
}
