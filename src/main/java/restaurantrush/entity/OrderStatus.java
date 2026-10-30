package restaurantrush.entity;

/** Life cycle of an order. CANCELLED is used when the customer leaves unhappy. */
public enum OrderStatus {
    PLACED,
    IN_PROGRESS,
    READY,
    SERVED,
    PAID,
    CANCELLED
}
