package restaurantrush.entity;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * The Cashier works automatically: at most one payment per turn, oldest READY_TO_PAY customer
 * first. A customer who became READY_TO_PAY this turn can only pay next turn.
 */
public class Cashier extends Staff {

    public Cashier() {
        super("Cashier");
    }

    public Optional<Payment> collectPayment(Collection<Customer> customers, int turn) {
        if (hasActed()) {
            return Optional.empty();
        }
        Optional<Customer> next = customers.stream()
                .filter(c -> c.canPayOn(turn))
                .min(Comparator.comparingInt(Customer::getReadyToPayTurn).thenComparingInt(Customer::getId));
        if (next.isEmpty()) {
            return Optional.empty();
        }
        Customer customer = next.get();
        int amount = customer.getOrder().getBillCents();
        int tableNumber = customer.getTable().getNumber();
        customer.completePayment();
        markActed();
        return Optional.of(new Payment(customer, amount, tableNumber, customer.getPaidSatisfaction()));
    }
}
