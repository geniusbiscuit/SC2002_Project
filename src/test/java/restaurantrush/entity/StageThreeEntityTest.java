package restaurantrush.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Stage 3 rules that live in entity classes: ComboMeal, Menu choice rule, HappyHour, Host. */
class StageThreeEntityTest {

    private static final MenuItem SALAD = new MenuItem("Salad", 900, 1);
    private static final MenuItem BURGER = new MenuItem("Burger", 1500, 1);
    private static final MenuItem PASTA = new MenuItem("Pasta", 1800, 2);

    // ---- ComboMeal ---------------------------------------------------------------------

    @Test
    void comboPriceIsNinetyPercentOfThePairAndUnitsAreTheSum() {
        ComboMeal saladPasta = new ComboMeal(SALAD, PASTA);
        assertEquals("Salad + Pasta", saladPasta.getName());
        assertEquals(2430, saladPasta.getPriceCents()); // ($9 + $18) x 0.9 = $24.30
        assertEquals(3, saladPasta.getPrepUnits());

        ComboMeal burgerPasta = new ComboMeal(BURGER, PASTA);
        assertEquals(2970, burgerPasta.getPriceCents());
        assertEquals(3, burgerPasta.getPrepUnits());
    }

    @Test
    void comboPriceRoundsToTheNearestCent() {
        // 105 + 100 = 205 cents; 90% = 184.5 -> 185
        assertEquals(185, new ComboMeal(new MenuItem("A", 105, 1), new MenuItem("B", 100, 1)).getPriceCents());
        // 101 + 100 = 201 cents; 90% = 180.9 -> 181
        assertEquals(181, new ComboMeal(new MenuItem("A", 101, 1), new MenuItem("B", 100, 1)).getPriceCents());
    }

    @Test
    void aComboIsOrderedPreparedAndPaidLikeAnyOtherDish() {
        ComboMeal combo = new ComboMeal(SALAD, PASTA);
        Customer customer = new RegularCustomer(3);
        customer.sitAt(new Table(1));
        Order order = customer.placeOrder(combo, customer.billFor(combo));
        assertEquals(2430, order.getBillCents());

        Chef chef = new Chef();
        for (int turn = 1; turn <= 3; turn++) {
            chef.startTurn();
            assertFalse(order.getStatus() == OrderStatus.READY);
            chef.prepare(customer, turn);
        }
        assertEquals(OrderStatus.READY, order.getStatus());
        assertEquals(3, order.getPreparedUnits());
        assertFalse(order.isServableOn(3));
        assertTrue(order.isServableOn(4));
    }

    // ---- Menu: who orders what ---------------------------------------------------------------

    @Test
    void withCombosEveryThirdCustomerOrdersAComboOthersFollowTheOldRule() {
        Menu menu = Menu.withCombos();
        String[] expected = {"Salad", "Burger", "Salad + Pasta", "Salad", "Burger", "Burger + Pasta"};
        for (int id = 1; id <= 6; id++) {
            assertEquals(expected[id - 1], new RegularCustomer(id).chooseItem(menu).getName(), "customer " + id);
        }
    }

    @Test
    void theClassicMenuIsUnchangedByStage3() {
        Menu menu = Menu.classic();
        assertTrue(menu.getCombos().isEmpty());
        String[] expected = {"Salad", "Burger", "Pasta", "Salad", "Burger", "Pasta"};
        for (int id = 1; id <= 6; id++) {
            assertEquals(expected[id - 1], new RegularCustomer(id).chooseItem(menu).getName());
        }
    }

    @Test
    void theCriticStillOrdersTheMostExpensiveDishWhichNowMayBeACombo() {
        assertEquals("Pasta", new CriticCustomer(1).chooseItem(Menu.classic()).getName());
        assertEquals("Burger + Pasta", new CriticCustomer(1).chooseItem(Menu.withCombos()).getName());
    }

    // ---- HappyHour ---------------------------------------------------------------------------

    @Test
    void vipDiscountIsAppliedFirstThenHappyHour() {
        HappyHour happyHour = new HappyHour(true);
        happyHour.activate();
        VIPCustomer vip = new VIPCustomer(2);
        // $15.00 x 0.9 = $13.50, then x 0.8 = $10.80
        assertEquals(1080, happyHour.priceFor(vip.billFor(BURGER)));
        // a regular customer just gets 20% off
        assertEquals(1200, happyHour.priceFor(new RegularCustomer(1).billFor(BURGER)));
    }

    @Test
    void happyHourLastsTwoTurnsAndCanBeUsedOnlyOnce() {
        HappyHour happyHour = new HappyHour(true);
        assertTrue(happyHour.isAvailable());
        assertFalse(happyHour.isActive());
        assertEquals(1500, happyHour.priceFor(1500));

        happyHour.activate();
        assertTrue(happyHour.isActive());
        assertEquals(2, happyHour.getRemainingTurns());
        assertFalse(happyHour.isAvailable());
        happyHour.endTurn();
        assertEquals(1, happyHour.getRemainingTurns());
        assertTrue(happyHour.isActive());
        happyHour.endTurn();
        assertFalse(happyHour.isActive());
        assertEquals(1500, happyHour.priceFor(1500));

        InvalidActionException error = assertThrows(InvalidActionException.class, happyHour::activate);
        assertTrue(error.getMessage().contains("once per game"));
    }

    @Test
    void happyHourCannotBeUsedWhenTheSettingDoesNotIncludeIt() {
        HappyHour off = new HappyHour(false);
        assertFalse(off.isAvailable());
        assertThrows(InvalidActionException.class, off::activate);
    }

    @Test
    void recoveryOnlyHelpsCustomersStillWaitingAndNeverExceedsOneHundred() {
        Customer waiting = new RegularCustomer(1);
        waiting.waitOneTurn();
        waiting.waitOneTurn(); // 84
        assertTrue(waiting.recoverSatisfaction(HappyHour.RECOVERY_POINTS));
        assertEquals(99, waiting.getSatisfaction());
        assertTrue(waiting.recoverSatisfaction(HappyHour.RECOVERY_POINTS));
        assertEquals(100, waiting.getSatisfaction(), "capped at 100");

        Customer eating = new RegularCustomer(2);
        eating.sitAt(new Table(1));
        eating.placeOrder(SALAD, 900);
        eating.startEating(1);
        assertFalse(eating.recoverSatisfaction(15), "served customers are not 'waiting'");

        Customer gone = new RegularCustomer(3);
        for (int i = 0; i < 13; i++) {
            gone.waitOneTurn();
        }
        gone.leaveUnhappy();
        assertFalse(gone.recoverSatisfaction(15), "customers who have left are not affected");
        assertEquals(0, gone.getSatisfaction());
    }

    // ---- Host and Waiter share the seating rules ----------------------------------------------

    @Test
    void hostAndWaiterApplyTheSameSeatingRules() {
        Table table = new Table(1);
        Customer first = new RegularCustomer(1);
        Customer second = new RegularCustomer(2);
        Host host = new Host();
        Waiter waiter = new Waiter();

        host.startTurn();
        assertEquals(1, host.seat(first, List.of(table)).getNumber());
        assertThrows(InvalidActionException.class, () -> host.seat(second, List.of(table)),
                "the Host also seats at most one customer per turn");

        waiter.startTurn();
        InvalidActionException noTable = assertThrows(InvalidActionException.class,
                () -> waiter.seat(second, List.of(table)));
        assertEquals("There is no free table.", noTable.getMessage());

        InvalidActionException notQueued = assertThrows(InvalidActionException.class,
                () -> waiter.seat(first, List.of(new Table(2))));
        assertTrue(notQueued.getMessage().contains("not in the waiting queue"));
        assertFalse(waiter.hasActed(), "failed seating attempts keep the Waiter's task");
    }
}
