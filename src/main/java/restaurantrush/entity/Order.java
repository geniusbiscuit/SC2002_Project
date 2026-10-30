package restaurantrush.entity;

/**
 * One customer's order. The bill is locked when the order is taken, so later price changes
 * never alter an order that is already placed.
 */
public class Order {

    private final Customer customer;
    private final MenuItem item;
    private final int billCents;
    private int remainingUnits;
    private OrderStatus status = OrderStatus.PLACED;
    private int readyTurn = -1;

    Order(Customer customer, MenuItem item, int billCents) {
        this.customer = customer;
        this.item = item;
        this.billCents = billCents;
        this.remainingUnits = item.getPrepUnits();
    }

    public Customer getCustomer() {
        return customer;
    }

    public MenuItem getItem() {
        return item;
    }

    public int getBillCents() {
        return billCents;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public int getRemainingUnits() {
        return remainingUnits;
    }

    public int getPreparedUnits() {
        return item.getPrepUnits() - remainingUnits;
    }

    public int getReadyTurn() {
        return readyTurn;
    }

    /** The Chef can only work on orders that are placed or in progress. */
    public boolean isPreparable() {
        return status == OrderStatus.PLACED || status == OrderStatus.IN_PROGRESS;
    }

    /** A dish can be served only if it was already READY before the current turn. */
    public boolean isServableOn(int turn) {
        return status == OrderStatus.READY && readyTurn < turn;
    }

    void prepareOneUnit(int turn) {
        remainingUnits--;
        if (remainingUnits == 0) {
            status = OrderStatus.READY;
            readyTurn = turn;
        } else {
            status = OrderStatus.IN_PROGRESS;
        }
    }

    void markServed() {
        status = OrderStatus.SERVED;
    }

    void markPaid() {
        status = OrderStatus.PAID;
    }

    void cancel() {
        status = OrderStatus.CANCELLED;
    }
}
