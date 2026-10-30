package restaurantrush.entity;

/**
 * A customer. Everything that differs between kinds of customer (how fast patience runs out,
 * how the bill is calculated, what is ordered) is a method here that subclasses may override,
 * so the game controller never has to ask "what kind of customer is this?".
 *
 * <p>State is private; only the staff and restaurant classes in this package can move a
 * customer through its life cycle (encapsulation).
 */
public abstract class Customer {

    public static final int START_SATISFACTION = 100;

    private final int id;
    private int satisfaction = START_SATISFACTION;
    private CustomerState state = CustomerState.WAITING_FOR_TABLE;
    private Table table;
    private Order order;
    private int servedTurn = -1;
    private int readyToPayTurn = -1;
    private int paidSatisfaction = -1;

    protected Customer(int id) {
        this.id = id;
    }

    // ---- behaviour that subclasses define --------------------------------------------

    /** Name shown to the manager, e.g. "Regular". */
    public abstract String getTypeName();

    /** Satisfaction lost at the end of every turn in which this customer is still unserved. */
    public abstract int getWaitingLoss();

    /** What this customer orders. Default: the menu's documented deterministic rule. */
    public MenuItem chooseItem(Menu menu) {
        return menu.defaultChoiceFor(id);
    }

    /** The bill for the chosen dish. Default: full price. */
    public int billFor(MenuItem item) {
        return item.getPriceCents();
    }

    // ---- read-only view --------------------------------------------------------------

    public int getId() {
        return id;
    }

    public int getSatisfaction() {
        return satisfaction;
    }

    public CustomerState getState() {
        return state;
    }

    public Table getTable() {
        return table;
    }

    public Order getOrder() {
        return order;
    }

    public int getServedTurn() {
        return servedTurn;
    }

    public int getReadyToPayTurn() {
        return readyToPayTurn;
    }

    /** Satisfaction recorded at the moment of payment (-1 if not paid). */
    public int getPaidSatisfaction() {
        return paidSatisfaction;
    }

    @Override
    public String toString() {
        return "#" + id + " " + getTypeName();
    }

    // ---- life cycle (package-private: only staff/restaurant can drive it) --------------

    void sitAt(Table newTable) {
        requireState(CustomerState.WAITING_FOR_TABLE, "is not waiting in the queue");
        newTable.occupy(this);
        table = newTable;
        state = CustomerState.SEATED;
    }

    void receiveOrder(Order newOrder) {
        requireState(CustomerState.SEATED, "is not seated waiting to order");
        order = newOrder;
        state = CustomerState.AWAITING_FOOD;
    }

    void startEating(int turn) {
        requireState(CustomerState.AWAITING_FOOD, "has no food waiting to be served");
        order.markServed();
        servedTurn = turn;
        state = CustomerState.EATING;
    }

    /** A customer served on an earlier turn finishes one full turn of eating. */
    boolean isDoneEating(int turn) {
        return state == CustomerState.EATING && servedTurn < turn;
    }

    void becomeReadyToPay(int turn) {
        requireState(CustomerState.EATING, "is not eating");
        readyToPayTurn = turn;
        state = CustomerState.READY_TO_PAY;
    }

    /** Can the Cashier charge this customer on {@code turn}? (Not on the turn they became ready.) */
    boolean canPayOn(int turn) {
        return state == CustomerState.READY_TO_PAY && readyToPayTurn < turn;
    }

    void completePayment() {
        requireState(CustomerState.READY_TO_PAY, "is not ready to pay");
        paidSatisfaction = satisfaction;
        order.markPaid();
        table.release();
        state = CustomerState.PAID;
    }

    /** End-of-turn waiting loss; only customers who have not been served lose satisfaction. */
    void waitOneTurn() {
        if (state.isUnserved()) {
            satisfaction = Math.max(0, satisfaction - getWaitingLoss());
        }
    }

    /** Happy Hour: customers still waiting for a table or food regain satisfaction (maximum 100). */
    boolean recoverSatisfaction(int points) {
        if (!state.isUnserved()) {
            return false;
        }
        satisfaction = Math.min(START_SATISFACTION, satisfaction + points);
        return true;
    }

    boolean hasLostPatience() {
        return state.isInRestaurant() && satisfaction == 0;
    }

    /** Leaves without paying: cancel any unfinished order and free the table. */
    void leaveUnhappy() {
        if (order != null) {
            order.cancel();
        }
        if (table != null) {
            table.release();
        }
        state = CustomerState.ABANDONED;
    }

    Order placeOrder(MenuItem item, int billCents) {
        Order placed = new Order(this, item, billCents);
        receiveOrder(placed);
        return placed;
    }

    private void requireState(CustomerState expected, String problem) {
        if (state != expected) {
            throw new InvalidActionException("Customer #" + id + " " + problem + " (currently " + state + ").");
        }
    }
}
