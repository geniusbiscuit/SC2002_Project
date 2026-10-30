package restaurantrush.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The restaurant: tables, staff, menu and every customer who has ever arrived. It answers the
 * questions the controller needs ("who is queued?", "what can be served?") and performs the
 * automatic end-of-turn steps. Statistics are always derived from customer state, never kept in
 * separate counters, so revenue can never include an unpaid or abandoned order.
 */
public class Restaurant {

    private final Menu menu;
    private final List<Table> tables = new ArrayList<>();
    private final List<Customer> customers = new ArrayList<>();
    private final Host host = new Host();
    private final Waiter waiter = new Waiter();
    private final Chef chef = new Chef();
    private final Cashier cashier = new Cashier();
    private final HappyHour happyHour;

    public Restaurant(Menu menu, int tableCount, boolean happyHourEnabled) {
        this.menu = menu;
        this.happyHour = new HappyHour(happyHourEnabled);
        for (int i = 1; i <= tableCount; i++) {
            tables.add(new Table(i));
        }
    }

    // ---- structure -------------------------------------------------------------------

    public Menu getMenu() {
        return menu;
    }

    public List<Table> getTables() {
        return Collections.unmodifiableList(tables);
    }

    public Host getHost() {
        return host;
    }

    public HappyHour getHappyHour() {
        return happyHour;
    }

    public Waiter getWaiter() {
        return waiter;
    }

    public Chef getChef() {
        return chef;
    }

    public Cashier getCashier() {
        return cashier;
    }

    public void addCustomer(Customer customer) {
        customers.add(customer);
    }

    public List<Customer> getCustomers() {
        return Collections.unmodifiableList(customers);
    }

    public Optional<Customer> findCustomer(int id) {
        return customers.stream().filter(c -> c.getId() == id).findFirst();
    }

    // ---- what the manager can choose from ---------------------------------------------

    /** Customers inside the restaurant (queued, seated, eating, ...), in arrival order. */
    public List<Customer> customersInside() {
        return customers.stream().filter(c -> c.getState().isInRestaurant()).toList();
    }

    public List<Customer> queuedCustomers() {
        return customersWithState(CustomerState.WAITING_FOR_TABLE);
    }

    public List<Customer> customersAwaitingOrder() {
        return customersWithState(CustomerState.SEATED);
    }

    public List<Order> servableOrders(int turn) {
        return customers.stream()
                .filter(c -> c.getState() == CustomerState.AWAITING_FOOD)
                .map(Customer::getOrder)
                .filter(o -> o.isServableOn(turn))
                .toList();
    }

    public List<Order> preparableOrders() {
        return customers.stream()
                .filter(c -> c.getState() == CustomerState.AWAITING_FOOD)
                .map(Customer::getOrder)
                .filter(Order::isPreparable)
                .toList();
    }

    public boolean hasFreeTable() {
        return tables.stream().anyMatch(Table::isFree);
    }

    private List<Customer> customersWithState(CustomerState state) {
        return customers.stream().filter(c -> c.getState() == state).toList();
    }

    // ---- turn mechanics ---------------------------------------------------------------

    public void startStaffTurn() {
        host.startTurn();
        waiter.startTurn();
        chef.startTurn();
        cashier.startTurn();
    }

    /** Happy Hour, at turn start: customers still waiting for a table or food regain satisfaction. */
    public List<Customer> applyHappyHourRecovery() {
        List<Customer> recovered = new ArrayList<>();
        for (Customer customer : customers) {
            if (customer.recoverSatisfaction(HappyHour.RECOVERY_POINTS)) {
                recovered.add(customer);
            }
        }
        return recovered;
    }

    /** Step 4a: the Cashier handles at most one customer who was ready before this turn. */
    public Optional<Payment> collectPayment(int turn) {
        return cashier.collectPayment(customers, turn);
    }

    /** Step 4b: customers served on an earlier turn finish eating. */
    public List<Customer> finishEating(int turn) {
        List<Customer> done = customers.stream().filter(c -> c.isDoneEating(turn)).toList();
        done.forEach(c -> c.becomeReadyToPay(turn));
        return done;
    }

    /**
     * Step 5: every customer who has not been served loses satisfaction (the amount depends on
     * the customer). Anyone reaching 0 leaves without paying. Returns the customers who left.
     */
    public List<Customer> applyWaitingLoss() {
        List<Customer> departed = new ArrayList<>();
        for (Customer customer : customers) {
            if (!customer.getState().isInRestaurant()) {
                continue;
            }
            customer.waitOneTurn();
            if (customer.hasLostPatience()) {
                customer.leaveUnhappy();
                departed.add(customer);
            }
        }
        return departed;
    }

    // ---- statistics (derived) ----------------------------------------------------------

    public int getPaidCount() {
        return (int) customers.stream().filter(c -> c.getState() == CustomerState.PAID).count();
    }

    public int getRevenueCents() {
        return customers.stream()
                .filter(c -> c.getState() == CustomerState.PAID)
                .mapToInt(c -> c.getOrder().getBillCents())
                .sum();
    }

    public int getPaidSatisfactionSum() {
        return customers.stream()
                .filter(c -> c.getState() == CustomerState.PAID)
                .mapToInt(Customer::getPaidSatisfaction)
                .sum();
    }

    /** Average satisfaction recorded at payment; empty when nobody has paid yet. */
    public OptionalDouble getAverageSatisfaction() {
        int paid = getPaidCount();
        return paid == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) getPaidSatisfactionSum() / paid);
    }

    /** Customers who have been served at least once (food reached the table). */
    public int getServedCount() {
        return (int) customers.stream().filter(c -> c.getServedTurn() >= 0).count();
    }

    public int getUnhappyDepartures() {
        return (int) customers.stream().filter(c -> c.getState() == CustomerState.ABANDONED).count();
    }
}
