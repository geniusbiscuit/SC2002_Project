package restaurantrush.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The restaurant menu. Besides holding the dishes it owns the documented rule that decides
 * what a customer orders (customers choose their own food; the manager never does).
 */
public class Menu {

    /** With combos on the menu, every third customer (ids 3, 6, ...) orders a combo. */
    public static final int COMBO_EVERY = 3;

    private final List<MenuItem> items;
    private final List<ComboMeal> combos;

    public Menu(List<MenuItem> items) {
        this(items, List.of());
    }

    public Menu(List<MenuItem> items, List<ComboMeal> combos) {
        this.items = List.copyOf(items);
        this.combos = List.copyOf(combos);
    }

    /** The three dishes of the required base setting. */
    public static Menu classic() {
        return new Menu(List.of(
                new MenuItem("Salad", 900, 1),
                new MenuItem("Burger", 1500, 1),
                new MenuItem("Pasta", 1800, 2)));
    }

    /** The classic menu plus two combos: Salad + Pasta ($24.30, 3 units) and Burger + Pasta ($29.70, 3 units). */
    public static Menu withCombos() {
        Menu classic = classic();
        MenuItem salad = classic.items.get(0);
        MenuItem burger = classic.items.get(1);
        MenuItem pasta = classic.items.get(2);
        return new Menu(classic.items, List.of(new ComboMeal(salad, pasta), new ComboMeal(burger, pasta)));
    }

    /** The single dishes (without combos). */
    public List<MenuItem> getItems() {
        return items;
    }

    public List<ComboMeal> getCombos() {
        return combos;
    }

    /** Every dish a customer could order: single dishes first, then combos. */
    public List<MenuItem> getAllDishes() {
        List<MenuItem> all = new ArrayList<>(items);
        all.addAll(combos);
        return all;
    }

    /**
     * Default ordering rule (deterministic, so every run is reproducible):
     * <ul>
     *   <li>without combos, the n-th customer to arrive orders single dish ((n - 1) mod number-of-dishes),
     *       i.e. customer 1 Salad, 2 Burger, 3 Pasta, 4 Salad, 5 Burger, 6 Pasta;</li>
     *   <li>with combos on the menu, customers whose id is a multiple of 3 order a combo instead
     *       (customer 3 the first combo, customer 6 the second); everyone else follows the rule above.</li>
     * </ul>
     */
    public MenuItem defaultChoiceFor(int customerId) {
        if (!combos.isEmpty() && customerId % COMBO_EVERY == 0) {
            return combos.get((customerId / COMBO_EVERY - 1) % combos.size());
        }
        return items.get((customerId - 1) % items.size());
    }

    /** The dish with the highest price on the whole menu, combos included (what a critic orders). */
    public MenuItem mostExpensive() {
        return getAllDishes().stream()
                .max(Comparator.comparingInt(MenuItem::getPriceCents))
                .orElseThrow();
    }
}
