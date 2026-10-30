package restaurantrush.entity;

import restaurantrush.util.Money;

/** A VIP: more patient (loses 5 satisfaction per waiting turn) and gets 10% off the bill. */
public class VIPCustomer extends Customer {

    public static final int WAITING_LOSS = 5;
    public static final int BILL_PERCENT = 90;

    public VIPCustomer(int id) {
        super(id);
    }

    @Override
    public String getTypeName() {
        return "VIP";
    }

    @Override
    public int getWaitingLoss() {
        return WAITING_LOSS;
    }

    @Override
    public int billFor(MenuItem item) {
        return Money.percentOf(item.getPriceCents(), BILL_PERCENT);
    }
}
