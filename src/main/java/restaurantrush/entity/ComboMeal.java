package restaurantrush.entity;

import restaurantrush.util.Money;

/**
 * A combo is a menu item made of two existing menu items. Because it IS a {@link MenuItem},
 * orders, the Chef and the Cashier treat it exactly like any other dish.
 *
 * <ul>
 *   <li>preparation units = sum of the two components</li>
 *   <li>price = 90% of the components' combined price, rounded to the nearest cent
 *       (Salad + Pasta = ($9.00 + $18.00) x 0.9 = $24.30, 3 units)</li>
 * </ul>
 */
public class ComboMeal extends MenuItem {

    public static final int PRICE_PERCENT = 90;

    private final MenuItem first;
    private final MenuItem second;

    public ComboMeal(MenuItem first, MenuItem second) {
        super(first.getName() + " + " + second.getName(),
                Money.percentOf(first.getPriceCents() + second.getPriceCents(), PRICE_PERCENT),
                first.getPrepUnits() + second.getPrepUnits());
        this.first = first;
        this.second = second;
    }

    public MenuItem getFirst() {
        return first;
    }

    public MenuItem getSecond() {
        return second;
    }
}
