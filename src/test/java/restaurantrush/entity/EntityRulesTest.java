package restaurantrush.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Rules that live inside the entity classes (these tests may use package-private methods). */
class EntityRulesTest {

    private static final MenuItem SALAD = new MenuItem("Salad", 900, 1);

    @Test
    void menuAssignsDishesByTheDocumentedDeterministicRule() {
        Menu menu = Menu.classic();
        String[] expected = {"Salad", "Burger", "Pasta", "Salad", "Burger", "Pasta"};
        for (int id = 1; id <= 6; id++) {
            assertEquals(expected[id - 1], new RegularCustomer(id).chooseItem(menu).getName());
        }
    }

    @Test
    void orderCannotBeServedOnTheTurnItBecameReady() {
        Customer customer = new RegularCustomer(1);
        customer.sitAt(new Table(1));
        Order order = customer.placeOrder(new MenuItem("Pasta", 1800, 2), 1800);
        order.prepareOneUnit(2);
        assertEquals(OrderStatus.IN_PROGRESS, order.getStatus());
        order.prepareOneUnit(3);
        assertEquals(OrderStatus.READY, order.getStatus());
        assertFalse(order.isServableOn(3), "finished this turn: serve next turn at the earliest");
        assertTrue(order.isServableOn(4));
    }

    @Test
    void waiterRejectsServingAnOrderThatIsNotReady() {
        Waiter waiter = new Waiter();
        Customer customer = new RegularCustomer(1);
        customer.sitAt(new Table(1));
        customer.placeOrder(SALAD, 900);
        InvalidActionException error = assertThrows(InvalidActionException.class, () -> waiter.serve(customer, 2));
        assertTrue(error.getMessage().contains("not READY"));
        assertFalse(waiter.hasActed(), "a rejected choice must not use up the Waiter's task");
    }

    @Test
    void staffCanOnlyActOncePerTurn() {
        Waiter waiter = new Waiter();
        waiter.startTurn();
        waiter.rest();
        assertThrows(InvalidActionException.class, waiter::rest);
        waiter.startTurn();
        waiter.rest(); // a new turn gives a new task
    }

    @Test
    void waiterCannotSeatWithoutAFreeTable() {
        Waiter waiter = new Waiter();
        Table table = new Table(1);
        Customer first = new RegularCustomer(1);
        Customer second = new RegularCustomer(2);
        waiter.startTurn();
        waiter.seat(first, List.of(table));
        waiter.startTurn();
        InvalidActionException error = assertThrows(InvalidActionException.class,
                () -> waiter.seat(second, List.of(table)));
        assertEquals("There is no free table.", error.getMessage());
        assertFalse(waiter.hasActed());
    }

    @Test
    void cashierPaysOneCustomerPerTurnOldestReadyFirstAndNeverOnTheTurnTheyBecameReady() {
        Customer older = readyToPay(1, 5);
        Customer newer = readyToPay(2, 6);
        List<Customer> customers = List.of(newer, older); // order in the list must not matter
        Cashier cashier = new Cashier();

        cashier.startTurn();
        assertTrue(cashier.collectPayment(customers, 5).isEmpty(), "became ready this turn: pays next turn");

        cashier.startTurn();
        Optional<Payment> first = cashier.collectPayment(customers, 7);
        assertTrue(first.isPresent());
        assertEquals(1, first.get().customer().getId(), "oldest ready first");
        assertTrue(cashier.collectPayment(customers, 7).isEmpty(), "only one payment per turn");

        cashier.startTurn();
        assertEquals(2, cashier.collectPayment(customers, 8).orElseThrow().customer().getId());
    }

    @Test
    void paymentFreesTheTableAndRecordsSatisfactionAtThatMoment() {
        Customer customer = readyToPay(1, 5);
        Table table = customer.getTable();
        customer.waitOneTurn(); // no loss: already served
        Cashier cashier = new Cashier();
        cashier.startTurn();
        Payment payment = cashier.collectPayment(List.of(customer), 6).orElseThrow();
        assertEquals(900, payment.amountCents());
        assertEquals(100, payment.satisfaction());
        assertTrue(table.isFree());
        assertEquals(CustomerState.PAID, customer.getState());
    }

    @Test
    void servedCustomersDoNotLoseSatisfactionButUnservedOnesDo() {
        Customer waiting = new RegularCustomer(1);
        waiting.waitOneTurn();
        assertEquals(92, waiting.getSatisfaction());

        Customer eating = readyToPay(2, 5);
        eating.waitOneTurn();
        assertEquals(100, eating.getSatisfaction());
    }

    @Test
    void satisfactionNeverDropsBelowZero() {
        Customer customer = new RegularCustomer(1);
        for (int i = 0; i < 20; i++) {
            customer.waitOneTurn();
        }
        assertEquals(0, customer.getSatisfaction());
    }

    @Test
    void leavingUnhappyCancelsTheOrderAndFreesTheTable() {
        Table table = new Table(1);
        Customer customer = new RegularCustomer(1);
        customer.sitAt(table);
        Order order = customer.placeOrder(SALAD, 900);
        customer.leaveUnhappy();
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(table.isFree());
        assertEquals(CustomerState.ABANDONED, customer.getState());
    }

    /** A customer who was served, finished eating and became READY_TO_PAY on {@code readyTurn}. */
    private static Customer readyToPay(int id, int readyTurn) {
        Customer customer = new RegularCustomer(id);
        customer.sitAt(new Table(id));
        customer.placeOrder(SALAD, 900);
        customer.startEating(readyTurn - 1);
        customer.becomeReadyToPay(readyTurn);
        return customer;
    }
}
