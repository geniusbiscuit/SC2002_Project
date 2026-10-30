package restaurantrush.entity;

/** Where a customer currently is in the restaurant. */
public enum CustomerState {
    /** In the waiting queue, no table yet. */
    WAITING_FOR_TABLE,
    /** Sitting at a table, order not yet taken. */
    SEATED,
    /** Order taken; food not yet served (it may already be READY in the kitchen). */
    AWAITING_FOOD,
    /** Food served; eating. */
    EATING,
    /** Finished eating; waiting for the Cashier. */
    READY_TO_PAY,
    /** Paid and gone. */
    PAID,
    /** Satisfaction hit 0: left without paying. */
    ABANDONED;

    /** True while the customer is still waiting for the food to be served. */
    public boolean isUnserved() {
        return this == WAITING_FOR_TABLE || this == SEATED || this == AWAITING_FOOD;
    }

    /** True while the customer is still inside the restaurant. */
    public boolean isInRestaurant() {
        return this != PAID && this != ABANDONED;
    }
}
