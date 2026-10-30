package restaurantrush.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;

import restaurantrush.control.GameConfig.ArrivalSlot;
import restaurantrush.control.GameResult.Outcome;
import restaurantrush.entity.Customer;
import restaurantrush.entity.CustomerState;
import restaurantrush.entity.Menu;
import restaurantrush.entity.MenuItem;
import restaurantrush.entity.Order;
import restaurantrush.entity.RegularCustomer;
import restaurantrush.entity.VIPCustomer;
import restaurantrush.support.GreedyManager;

/** Stage 3 through the controller: Host, Happy Hour, and the full setting. */
class StageThreeFeaturesTest {

    private static final MenuItem SALAD = new MenuItem("Salad", 900, 1);
    private static final MenuItem BURGER = new MenuItem("Burger", 1500, 1);

    /** Customers all arrive on turn 1; ids follow the order given. */
    @SafeVarargs
    private static GameConfig config(Menu menu, boolean host, boolean happyHour, IntFunction<Customer>... kinds) {
        List<ArrivalSlot> arrivals = new ArrayList<>();
        for (IntFunction<Customer> kind : kinds) {
            arrivals.add(new ArrivalSlot(1, kind));
        }
        return new GameConfig(18, 2, 1, 1, 0, menu, arrivals, host, happyHour);
    }

    private static Menu burgerOnly() {
        return new Menu(List.of(BURGER));
    }

    // ---- fullSetting -------------------------------------------------------------------------

    @Test
    void fullSettingKeepsTheBaseNumbersAndAddsHostCombosAndHappyHour() {
        GameConfig full = GameConfig.fullSetting();
        GameConfig base = GameConfig.baseSetting();
        assertEquals(base.getMaxTurns(), full.getMaxTurns());
        assertEquals(base.getTableCount(), full.getTableCount());
        assertEquals(base.getArrivals().stream().map(ArrivalSlot::turn).toList(),
                full.getArrivals().stream().map(ArrivalSlot::turn).toList());
        assertTrue(full.isHostEnabled());
        assertTrue(full.isHappyHourEnabled());
        assertEquals(2, full.getMenu().getCombos().size());
        assertFalse(base.isHostEnabled());
        assertFalse(base.isHappyHourEnabled());
        assertTrue(base.getMenu().getCombos().isEmpty());
    }

    @Test
    void greedyManagerWinsTheFullSettingWithAndWithoutHappyHour() {
        for (var policy : List.of(GreedyManager.NO_HAPPY_HOUR, GreedyManager.WHEN_A_VIP_IS_WAITING)) {
            GameController game = new GameController(GameConfig.fullSetting());
            GreedyManager.playToEnd(game, policy);
            assertEquals(Outcome.VICTORY, game.getResult().outcome());
            assertEquals(18, game.getResult().turnsUsed());
        }
    }

    // ---- Host ------------------------------------------------------------------------------------

    @Test
    void theHostSeatsOneCustomerInAdditionToTheWaitersTask() {
        GameController game = new GameController(
                config(Menu.classic(), true, false, RegularCustomer::new, RegularCustomer::new));
        game.beginTurn();
        assertEquals(GameController.Phase.HOST, game.getPhase());
        assertTrue(game.hostSeat(1).success());
        assertTrue(game.waiterSeat(2).success(), "the Waiter can still seat a customer in the same turn");
        assertEquals(CustomerState.SEATED, game.getRestaurant().findCustomer(1).orElseThrow().getState());
        assertEquals(CustomerState.SEATED, game.getRestaurant().findCustomer(2).orElseThrow().getState());
        assertFalse(game.hostSeat(2).success(), "at most one Host task per turn");
    }

    @Test
    void theHostActsBeforeTheWaiter() {
        GameController game = new GameController(config(Menu.classic(), true, false, RegularCustomer::new));
        game.beginTurn();
        ActionResult early = game.waiterSeat(1);
        assertFalse(early.success());
        assertTrue(early.message().contains("Host acts before the Waiter"));
        assertTrue(game.hostWait().success());
        assertTrue(game.waiterSeat(1).success());
    }

    @Test
    void aCustomerSeatedByTheHostCanOrderInTheSameTurnSoTheDishIsServedOnTurnTwo() {
        GameController game = new GameController(config(new Menu(List.of(SALAD)), true, false, RegularCustomer::new));
        game.beginTurn();
        assertTrue(game.hostSeat(1).success());
        assertTrue(game.waiterTakeOrder(1).success());
        assertTrue(game.chefPrepare(1).success());
        game.endTurn();
        game.beginTurn();
        game.hostWait();
        assertTrue(game.waiterServe(1).success(), "READY on turn 1, served on turn 2");
    }

    @Test
    void withoutAHostInTheSettingHostChoicesAreRejected() {
        GameController game = new GameController(config(Menu.classic(), false, false, RegularCustomer::new));
        game.beginTurn();
        assertEquals(GameController.Phase.WAITER, game.getPhase());
        ActionResult result = game.hostSeat(1);
        assertFalse(result.success());
        assertEquals("There is no Host in this setting.", result.message());
        assertTrue(game.waiterSeat(1).success());
    }

    @Test
    void aRejectedHostChoiceDoesNotUseUpTheHostsTask() {
        GameController game = new GameController(config(Menu.classic(), true, false,
                RegularCustomer::new, RegularCustomer::new, RegularCustomer::new));
        game.beginTurn();
        assertTrue(game.hostSeat(1).success());
        assertTrue(game.waiterSeat(2).success());
        assertTrue(game.chefWait().success());
        game.endTurn();
        game.beginTurn(); // both tables are taken
        ActionResult noTable = game.hostSeat(3);
        assertFalse(noTable.success());
        assertEquals("There is no free table.", noTable.message());
        assertEquals(GameController.Phase.HOST, game.getPhase(), "still the Host's choice");
        assertTrue(game.hostWait().success());
    }

    // ---- Happy Hour ------------------------------------------------------------------------------

    @Test
    void aVipBurgerOrderedDuringHappyHourCostsTenEighty() {
        GameController game = new GameController(config(burgerOnly(), false, true, VIPCustomer::new));
        game.beginTurn();
        assertTrue(game.activateHappyHour().success());
        assertTrue(game.waiterSeat(1).success());
        game.chefWait();
        game.endTurn();

        game.beginTurn(); // turn 2: Happy Hour still active
        ActionResult order = game.waiterTakeOrder(1);
        assertTrue(order.success());
        assertTrue(order.message().contains("$10.80"), order.message());
        assertTrue(order.message().contains("list price $15.00"), order.message());
        assertEquals(1080, game.getRestaurant().findCustomer(1).orElseThrow().getOrder().getBillCents());
    }

    @Test
    void happyHourLastsThisTurnAndTheNextAndShowsItsRemainingDuration() {
        GameController game = new GameController(config(burgerOnly(), false, true, RegularCustomer::new));
        game.beginTurn();
        assertTrue(game.getHappyHour().isAvailable());
        game.activateHappyHour();
        assertEquals(2, game.getHappyHour().getRemainingTurns());
        game.waiterWait();
        game.chefWait();
        game.endTurn();

        game.beginTurn();
        assertTrue(game.getHappyHour().isActive());
        assertEquals(1, game.getHappyHour().getRemainingTurns());
        game.waiterWait();
        game.chefWait();
        game.endTurn();

        game.beginTurn();
        assertFalse(game.getHappyHour().isActive());
        assertEquals(0, game.getHappyHour().getRemainingTurns());
    }

    @Test
    void happyHourOnlyOncePerGameAndOnlyAtTheStartOfATurn() {
        GameController game = new GameController(config(burgerOnly(), false, true, RegularCustomer::new));
        game.beginTurn();
        game.waiterWait();
        ActionResult late = game.activateHappyHour();
        assertFalse(late.success(), "a staff member has already acted");
        assertTrue(late.message().contains("start of a turn"));
        game.chefWait();
        game.endTurn();

        game.beginTurn();
        assertTrue(game.activateHappyHour().success());
        game.waiterWait();
        game.chefWait();
        game.endTurn();
        game.beginTurn();
        assertFalse(game.activateHappyHour().success(), "already active");
        game.waiterWait();
        game.chefWait();
        game.endTurn();
        game.beginTurn(); // expired, but still used once
        ActionResult again = game.activateHappyHour();
        assertFalse(again.success());
        assertTrue(again.message().contains("once per game"));
    }

    @Test
    void happyHourIsRejectedWhenTheSettingDoesNotIncludeIt() {
        GameController game = new GameController(config(burgerOnly(), false, false, RegularCustomer::new));
        game.beginTurn();
        ActionResult result = game.activateHappyHour();
        assertFalse(result.success());
        assertTrue(result.message().contains("not part of this game setting"));
    }

    @Test
    void waitingCustomersRecoverFifteenAtTurnStartForTheTwoHappyHourTurnsOnly() {
        GameController game = new GameController(config(burgerOnly(), false, true, RegularCustomer::new));
        Customer customer;
        game.beginTurn();
        customer = game.getRestaurant().findCustomer(1).orElseThrow();
        game.waiterWait();
        game.chefWait();
        game.endTurn();
        assertEquals(92, customer.getSatisfaction());

        game.beginTurn(); // turn 2: switch Happy Hour on: 92 + 15 -> capped at 100
        assertTrue(game.activateHappyHour().success());
        assertEquals(100, customer.getSatisfaction());
        game.waiterWait();
        game.chefWait();
        game.endTurn();
        assertEquals(92, customer.getSatisfaction());

        game.beginTurn(); // turn 3: second Happy Hour turn recovers again at turn start
        assertEquals(100, customer.getSatisfaction());
        game.waiterWait();
        game.chefWait();
        game.endTurn();
        assertEquals(92, customer.getSatisfaction());

        game.beginTurn(); // turn 4: Happy Hour is over, no recovery
        assertEquals(92, customer.getSatisfaction());
        game.waiterWait();
        game.chefWait();
        game.endTurn();
        assertEquals(84, customer.getSatisfaction());
    }

    @Test
    void anOrderPlacedBeforeHappyHourKeepsItsFullPriceAndOneTakenAfterItEndedPaysFullPrice() {
        // Customer 1 orders on turn 1 (Host seats, Waiter takes the order) BEFORE Happy Hour starts on turn 2.
        GameController game = new GameController(config(burgerOnly(), true, true, VIPCustomer::new));
        game.beginTurn();
        game.hostSeat(1);
        game.waiterTakeOrder(1);
        game.chefPrepare(1);
        game.endTurn();
        game.beginTurn();
        assertTrue(game.activateHappyHour().success());
        Order order = game.getRestaurant().findCustomer(1).orElseThrow().getOrder();
        assertEquals(1350, order.getBillCents(), "already placed: Happy Hour does not change it");
        game.hostWait();
        game.waiterServe(1);
        game.chefWait();
        game.endTurn();

        // A second game: the order is taken on turn 3, after Happy Hour (turns 1-2) has ended.
        GameController late = new GameController(config(burgerOnly(), false, true, VIPCustomer::new));
        late.beginTurn();
        late.activateHappyHour();
        late.waiterSeat(1);
        late.chefWait();
        late.endTurn();
        late.beginTurn();
        late.waiterWait();
        late.chefWait();
        late.endTurn();
        late.beginTurn(); // turn 3: Happy Hour over
        late.waiterTakeOrder(1);
        assertEquals(1350, late.getRestaurant().findCustomer(1).orElseThrow().getOrder().getBillCents());
    }

    @Test
    void aHappyHourPriceStaysLockedUntilPayment() {
        GameController game = new GameController(config(burgerOnly(), false, true, VIPCustomer::new));
        game.beginTurn();
        game.activateHappyHour();
        game.waiterSeat(1);
        game.chefWait();
        game.endTurn();
        game.beginTurn();
        game.waiterTakeOrder(1);
        game.chefPrepare(1);
        game.endTurn();
        while (!game.isGameOver()) { // Happy Hour is long over by the time the customer pays
            game.beginTurn();
            int id = 1;
            Customer customer = game.getRestaurant().findCustomer(id).orElseThrow();
            if (customer.getState() == CustomerState.AWAITING_FOOD) {
                game.waiterServe(id);
            } else {
                game.waiterWait();
            }
            game.chefWait();
            game.endTurn();
        }
        assertEquals(1080, game.getRestaurant().getRevenueCents());
    }
}
