package restaurantrush.entity;

import java.util.Collection;

/**
 * Seating rules shared by the Waiter and the Host (extracted in Stage 3 so the two roles cannot
 * drift apart): the customer must be in the queue and a table must be free.
 */
final class Seating {

    private Seating() {
    }

    /** Seats the customer at the lowest-numbered free table and returns that table. */
    static Table seat(Customer customer, Collection<Table> tables) {
        if (customer.getState() != CustomerState.WAITING_FOR_TABLE) {
            throw new InvalidActionException("Customer #" + customer.getId()
                    + " is not in the waiting queue (currently " + customer.getState() + ").");
        }
        Table free = tables.stream()
                .filter(Table::isFree)
                .findFirst()
                .orElseThrow(() -> new InvalidActionException("There is no free table."));
        customer.sitAt(free);
        return free;
    }
}
