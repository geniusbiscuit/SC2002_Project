package restaurantrush.control;

import java.util.List;
import java.util.function.IntFunction;

import restaurantrush.entity.CriticCustomer;
import restaurantrush.entity.Customer;
import restaurantrush.entity.Menu;
import restaurantrush.entity.RegularCustomer;
import restaurantrush.entity.VIPCustomer;

/**
 * All the numbers of a game in one place. {@link #baseSetting()} is the required setting a TA
 * can reproduce: 18 turns, 2 tables, 1 Waiter / Chef / Cashier, six arrivals on turns
 * 1, 4, 7, 10, 13 and 16, and the three targets.
 */
public final class GameConfig {

    /** A scheduled arrival: on {@code turn}, a customer is built from the given id. */
    public record ArrivalSlot(int turn, IntFunction<Customer> factory) {
    }

    private final int maxTurns;
    private final int tableCount;
    private final int requiredPaidCustomers;
    private final int requiredRevenueCents;
    private final int requiredAverageSatisfaction;
    private final Menu menu;
    private final List<ArrivalSlot> arrivals;
    private final boolean hostEnabled;
    private final boolean happyHourEnabled;

    /** A setting without the Stage 3 extras (no Host, no Happy Hour). */
    public GameConfig(int maxTurns, int tableCount, int requiredPaidCustomers, int requiredRevenueCents,
                      int requiredAverageSatisfaction, Menu menu, List<ArrivalSlot> arrivals) {
        this(maxTurns, tableCount, requiredPaidCustomers, requiredRevenueCents, requiredAverageSatisfaction,
                menu, arrivals, false, false);
    }

    public GameConfig(int maxTurns, int tableCount, int requiredPaidCustomers, int requiredRevenueCents,
                      int requiredAverageSatisfaction, Menu menu, List<ArrivalSlot> arrivals,
                      boolean hostEnabled, boolean happyHourEnabled) {
        this.hostEnabled = hostEnabled;
        this.happyHourEnabled = happyHourEnabled;
        this.maxTurns = maxTurns;
        this.tableCount = tableCount;
        this.requiredPaidCustomers = requiredPaidCustomers;
        this.requiredRevenueCents = requiredRevenueCents;
        this.requiredAverageSatisfaction = requiredAverageSatisfaction;
        this.menu = menu;
        this.arrivals = List.copyOf(arrivals);
    }

    /**
     * The required base setting with a mixed crowd: the second arrival is a VIP and the fourth
     * is a Critic (two of the six regular arrivals replaced). Still six arrivals on the same turns.
     */
    public static GameConfig baseSetting() {
        return new GameConfig(18, 2, 4, 4500, 60, Menu.classic(), List.of(
                new ArrivalSlot(1, RegularCustomer::new),
                new ArrivalSlot(4, VIPCustomer::new),
                new ArrivalSlot(7, RegularCustomer::new),
                new ArrivalSlot(10, CriticCustomer::new),
                new ArrivalSlot(13, RegularCustomer::new),
                new ArrivalSlot(16, RegularCustomer::new)));
    }

    /**
     * The Stage 3 game: the mixed crowd of Stage 2 plus a Host, combos on the menu and Happy Hour.
     * Arrival turns, tables, the other staff and the three targets are unchanged.
     */
    public static GameConfig fullSetting() {
        GameConfig base = baseSetting();
        return new GameConfig(base.maxTurns, base.tableCount, base.requiredPaidCustomers,
                base.requiredRevenueCents, base.requiredAverageSatisfaction, Menu.withCombos(),
                base.arrivals, true, true);
    }

    /** The Stage 1 crowd (six Regular customers), kept so earlier behaviour can still be replayed. */
    public static GameConfig regularOnlySetting() {
        return new GameConfig(18, 2, 4, 4500, 60, Menu.classic(), List.of(
                new ArrivalSlot(1, RegularCustomer::new),
                new ArrivalSlot(4, RegularCustomer::new),
                new ArrivalSlot(7, RegularCustomer::new),
                new ArrivalSlot(10, RegularCustomer::new),
                new ArrivalSlot(13, RegularCustomer::new),
                new ArrivalSlot(16, RegularCustomer::new)));
    }

    public int getMaxTurns() {
        return maxTurns;
    }

    public int getTableCount() {
        return tableCount;
    }

    public int getRequiredPaidCustomers() {
        return requiredPaidCustomers;
    }

    public int getRequiredRevenueCents() {
        return requiredRevenueCents;
    }

    public int getRequiredAverageSatisfaction() {
        return requiredAverageSatisfaction;
    }

    public Menu getMenu() {
        return menu;
    }

    public List<ArrivalSlot> getArrivals() {
        return arrivals;
    }

    /** Is there a Host who seats one extra customer per turn? */
    public boolean isHostEnabled() {
        return hostEnabled;
    }

    public boolean isHappyHourEnabled() {
        return happyHourEnabled;
    }
}
