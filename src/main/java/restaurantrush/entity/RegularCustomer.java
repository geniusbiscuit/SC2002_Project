package restaurantrush.entity;

/** A regular customer: loses 8 satisfaction per waiting turn and pays full price. */
public class RegularCustomer extends Customer {

    public static final int WAITING_LOSS = 8;

    public RegularCustomer(int id) {
        super(id);
    }

    @Override
    public String getTypeName() {
        return "Regular";
    }

    @Override
    public int getWaitingLoss() {
        return WAITING_LOSS;
    }
}
