package restaurantrush.entity;

/**
 * A food critic: very impatient (loses 12 satisfaction per waiting turn) and pays full price.
 * Special behaviour (our documented choice): a critic always orders the most expensive dish on
 * the menu, which puts the longest preparation on the Chef's plate and makes the manager decide
 * whether the critic is worth the staff time.
 */
public class CriticCustomer extends Customer {

    public static final int WAITING_LOSS = 12;

    public CriticCustomer(int id) {
        super(id);
    }

    @Override
    public String getTypeName() {
        return "Critic";
    }

    @Override
    public int getWaitingLoss() {
        return WAITING_LOSS;
    }

    @Override
    public MenuItem chooseItem(Menu menu) {
        return menu.mostExpensive();
    }
}
