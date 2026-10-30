package restaurantrush.entity;

/** Receipt of one payment, handed back to the controller so it can report what happened. */
public record Payment(Customer customer, int amountCents, int tableNumber, int satisfaction) {
}
