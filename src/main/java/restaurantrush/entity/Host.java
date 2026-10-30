package restaurantrush.entity;

import java.util.Collection;

/**
 * The Host seats at most one queued customer per turn, in addition to the Waiter's task. The
 * Host acts before the Waiter, so a customer seated by the Host can have an order taken by the
 * Waiter in the same turn.
 */
public class Host extends Staff {

    public Host() {
        super("Host");
    }

    public Table seat(Customer customer, Collection<Table> tables) {
        ensureAvailable();
        Table table = Seating.seat(customer, tables);
        markActed();
        return table;
    }
}
