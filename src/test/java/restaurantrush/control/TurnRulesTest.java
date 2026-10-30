package restaurantrush.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static restaurantrush.support.TestConfigs.onePastaCustomer;
import static restaurantrush.support.TestConfigs.playPastaExample;
import static restaurantrush.support.TestConfigs.step;

import java.util.List;

import org.junit.jupiter.api.Test;

import restaurantrush.control.GameResult.Outcome;
import restaurantrush.entity.Customer;
import restaurantrush.entity.CustomerState;
import restaurantrush.entity.Order;
import restaurantrush.entity.OrderStatus;
import restaurantrush.support.GreedyManager;

/** Turn sequence, staff limits, payment and result rules of the brief. */
class TurnRulesTest {

    @Test
    void baseSettingMatchesTheBrief() {
        GameConfig config = GameConfig.baseSetting();
        assertEquals(18, config.getMaxTurns());
        assertEquals(2, config.getTableCount());
        assertEquals(List.of(1, 4, 7, 10, 13, 16),
                config.getArrivals().stream().map(GameConfig.ArrivalSlot::turn).toList());
        assertEquals(4, config.getRequiredPaidCustomers());
        assertEquals(4500, config.getRequiredRevenueCents());
        assertEquals(60, config.getRequiredAverageSatisfaction());
        assertEquals(List.of("Salad", "Burger", "Pasta"),
                config.getMenu().getItems().stream().map(i -> i.getName()).toList());
    }

    @Test
    void workedExampleFromTheBriefMatchesTurnByTurn() {
        GameController game = new GameController(onePastaCustomer(1, 1800, 76));
        Customer customer;

        game.beginTurn();
        customer = game.getRestaurant().findCustomer(1).orElseThrow();
        assertEquals(100, customer.getSatisfaction());
        assertTrue(game.waiterSeat(1).success());
        assertTrue(game.chefWait().success());
        game.endTurn();
        assertEquals(92, customer.getSatisfaction());

        step(game, 2, "order", "prepare");
        assertEquals(84, customer.getSatisfaction());
        assertEquals(OrderStatus.IN_PROGRESS, customer.getOrder().getStatus());

        step(game, 3, "wait", "prepare");
        assertEquals(76, customer.getSatisfaction());
        assertEquals(OrderStatus.READY, customer.getOrder().getStatus());

        step(game, 4, "serve", "wait");
        assertEquals(76, customer.getSatisfaction());
        assertEquals(CustomerState.EATING, customer.getState());

        step(game, 5, "wait", "wait");
        assertEquals(CustomerState.READY_TO_PAY, customer.getState());
        assertEquals(0, game.getRestaurant().getRevenueCents(), "ready to pay is not paid yet");

        step(game, 6, "wait", "wait");
        assertEquals(CustomerState.PAID, customer.getState());
        assertEquals(1800, game.getRestaurant().getRevenueCents());
        assertEquals(76, customer.getPaidSatisfaction());
        assertTrue(customer.getTable() == null || customer.getTable().isFree());
    }

    @Test
    void chefMayWorkOnAnOrderTheWaiterTookThisTurn() {
        GameController game = new GameController(onePastaCustomer(1, 1800, 60));
        step(game, 1, "seat", "wait");
        game.beginTurn();
        assertTrue(game.waiterTakeOrder(1).success());
        assertEquals(1, game.getRestaurant().preparableOrders().size());
        assertTrue(game.chefPrepare(1).success());
    }

    @Test
    void aDishFinishedThisTurnIsOnlyServedNextTurn() {
        GameController game = new GameController(onePastaCustomer(1, 1800, 60));
        step(game, 1, "seat", "wait");
        step(game, 2, "order", "prepare");
        game.beginTurn(); // turn 3
        assertTrue(game.waiterWait().success()); // Waiter acts first, nothing is READY yet
        assertTrue(game.chefPrepare(1).success()); // Chef finishes the dish: READY
        game.endTurn();
        assertEquals(OrderStatus.READY, game.getRestaurant().findCustomer(1).orElseThrow().getOrder().getStatus());
        game.beginTurn(); // turn 4
        assertTrue(game.waiterServe(1).success());
    }

    @Test
    void waiterMustChooseBeforeTheChef() {
        GameController game = new GameController(onePastaCustomer(1, 1800, 60));
        game.beginTurn();
        ActionResult early = game.chefWait();
        assertFalse(early.success());
        assertTrue(early.message().contains("Waiter acts before the Chef"));
        assertTrue(game.waiterWait().success());
        assertTrue(game.chefWait().success());
    }

    @Test
    void aSecondWaiterTaskInTheSameTurnIsRejected() {
        GameController game = new GameController(onePastaCustomer(1, 1800, 60));
        game.beginTurn();
        assertTrue(game.waiterSeat(1).success());
        ActionResult second = game.waiterTakeOrder(1);
        assertFalse(second.success(), "one Waiter task per turn");
    }

    @Test
    void invalidChoiceExplainsWhyAndDoesNotUseUpTheStaffTask() {
        GameController game = new GameController(onePastaCustomer(1, 1800, 60));
        game.beginTurn();

        ActionResult unknown = game.waiterSeat(99);
        assertFalse(unknown.success());
        assertEquals("There is no customer with id 99.", unknown.message());

        ActionResult tooEarly = game.waiterTakeOrder(1); // still in the queue, not seated
        assertFalse(tooEarly.success());
        assertTrue(tooEarly.message().contains("not seated"));

        ActionResult nothingToServe = game.waiterServe(1);
        assertFalse(nothingToServe.success());

        assertFalse(game.getRestaurant().getWaiter().hasActed(), "no task was used");
        assertEquals(GameController.Phase.WAITER, game.getPhase());
        assertTrue(game.waiterSeat(1).success(), "the Waiter can still act");
    }

    @Test
    void chefRejectsAnOrderThatDoesNotExist() {
        GameController game = new GameController(onePastaCustomer(1, 1800, 60));
        game.beginTurn();
        assertTrue(game.waiterSeat(1).success());
        ActionResult noOrder = game.chefPrepare(1);
        assertFalse(noOrder.success());
        assertTrue(noOrder.message().contains("has not ordered"));
        assertTrue(game.chefWait().success());
    }

    @Test
    void customerLeavesAtZeroSatisfactionWithoutPayingAndTheTableIsFreed() {
        GameController game = new GameController(onePastaCustomer(1, 1800, 60));
        step(game, 1, "seat", "wait");
        step(game, 2, "order", "wait");
        Customer customer = game.getRestaurant().findCustomer(1).orElseThrow();
        Order order = customer.getOrder();
        int turn = 3;
        while (customer.getState() != CustomerState.ABANDONED) {
            step(game, turn++, "wait", "wait");
            assertTrue(turn < 20, "customer must eventually leave");
        }
        assertEquals(0, customer.getSatisfaction());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(game.getRestaurant().getTables().stream().allMatch(t -> t.isFree()));
        assertEquals(0, game.getRestaurant().getRevenueCents(), "abandoned orders never count as revenue");
        assertEquals(1, game.getRestaurant().getUnhappyDepartures());
    }

    @Test
    void aRegularCustomerLeavesInTheTurnTheirSatisfactionReachesZero() {
        GameController game = new GameController(onePastaCustomer(1, 1800, 60));
        Customer customer = null;
        for (int turn = 1; turn <= 13; turn++) {
            step(game, turn, "wait", "wait");
            customer = game.getRestaurant().findCustomer(1).orElseThrow();
            if (turn < 13) {
                assertEquals(CustomerState.WAITING_FOR_TABLE, customer.getState(), "turn " + turn);
            }
        }
        assertEquals(CustomerState.ABANDONED, customer.getState()); // 100 - 13 x 8 = -4 -> 0
    }

    @Test
    void resultIsOnlyAvailableAfterTheLastTurnAndTheGameAlwaysRunsAllTurns() {
        GameController game = new GameController(onePastaCustomer(1, 1800, 60));
        assertThrows(IllegalStateException.class, game::getResult);
        playPastaExample(game); // customer pays on turn 6, but the game keeps going
        assertTrue(game.isGameOver());
        assertEquals(18, game.getResult().turnsUsed());
    }

    @Test
    void victoryRequiresAllThreeTargetsAndEachBoundaryIsInclusive() {
        // The example run: 1 paid customer, $18.00, average satisfaction 76.
        assertEquals(Outcome.VICTORY, resultFor(1, 1800, 76).outcome());

        GameResult tooFewCustomers = resultFor(2, 1800, 76);
        assertEquals(Outcome.DEFEAT, tooFewCustomers.outcome());
        assertFalse(tooFewCustomers.paidTargetMet());

        GameResult tooLittleRevenue = resultFor(1, 1801, 76);
        assertEquals(Outcome.DEFEAT, tooLittleRevenue.outcome());
        assertFalse(tooLittleRevenue.revenueTargetMet());

        GameResult tooUnhappy = resultFor(1, 1800, 77);
        assertEquals(Outcome.DEFEAT, tooUnhappy.outcome());
        assertFalse(tooUnhappy.satisfactionTargetMet());
    }

    @Test
    void defeatWhenNobodyPaysAndAverageIsNotApplicable() {
        GameController game = new GameController(GameConfig.baseSetting());
        while (!game.isGameOver()) {
            game.beginTurn();
            game.waiterWait();
            game.chefWait();
            game.endTurn();
        }
        GameResult result = game.getResult();
        assertEquals(Outcome.DEFEAT, result.outcome());
        assertEquals(0, result.paidCount());
        assertTrue(result.averageSatisfaction().isEmpty());
        assertFalse(result.satisfactionTargetMet());
    }

    @Test
    void atMostFiveCustomersCanPayBecauseTheTurnSixteenArrivalCannotFinish() {
        GameController game = new GameController(GameConfig.baseSetting());
        GreedyManager.playToEnd(game);
        assertEquals(6, game.getRestaurant().getCustomers().size());
        assertTrue(game.getResult().paidCount() <= 5);
        assertTrue(game.getRestaurant().findCustomer(6).orElseThrow().getState() != CustomerState.PAID);
    }

    @Test
    void greedyManagerWinsBothTheStage1AndTheMixedBaseSetting() {
        for (GameConfig config : List.of(GameConfig.regularOnlySetting(), GameConfig.baseSetting())) {
            GameController game = new GameController(config);
            GreedyManager.playToEnd(game);
            GameResult result = game.getResult();
            assertEquals(Outcome.VICTORY, result.outcome());
            assertEquals(18, result.turnsUsed());
        }
    }

    private static GameResult resultFor(int paid, int revenueCents, int averageSatisfaction) {
        GameController game = new GameController(onePastaCustomer(paid, revenueCents, averageSatisfaction));
        playPastaExample(game);
        return game.getResult();
    }
}
