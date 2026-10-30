package restaurantrush.entity;

/** The Chef advances one order by one preparation unit per turn. */
public class Chef extends Staff {

    public Chef() {
        super("Chef");
    }

    public Order prepare(Customer customer, int turn) {
        ensureAvailable();
        Order order = customer.getOrder();
        if (order == null) {
            throw new InvalidActionException("Customer #" + customer.getId() + " has not ordered yet.");
        }
        if (!order.isPreparable()) {
            throw new InvalidActionException("The order of customer #" + customer.getId()
                    + " cannot be prepared (status " + order.getStatus() + ").");
        }
        order.prepareOneUnit(turn);
        markActed();
        return order;
    }
}
