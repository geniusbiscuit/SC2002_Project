package restaurantrush.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;

import restaurantrush.control.GameConfig;
import restaurantrush.control.GameController;
import restaurantrush.control.GameResult;
import restaurantrush.support.TestConfigs;

/** Stage 2: VIP and Critic differ from Regular only through the methods the customer overrides. */
class CustomerBehaviourTest {

    private static final Menu MENU = Menu.classic();

    @Test
    void satisfactionLossPerWaitingTurnDependsOnTheCustomer() {
        assertEquals(92, afterOneWait(new RegularCustomer(1)));
        assertEquals(95, afterOneWait(new VIPCustomer(1)));
        assertEquals(88, afterOneWait(new CriticCustomer(1)));
    }

    @Test
    void vipGetsTenPercentOffEveryDishOthersPayFullPrice() {
        MenuItem salad = MENU.getItems().get(0);
        MenuItem burger = MENU.getItems().get(1);
        MenuItem pasta = MENU.getItems().get(2);
        VIPCustomer vip = new VIPCustomer(2);
        assertEquals(810, vip.billFor(salad));
        assertEquals(1350, vip.billFor(burger));
        assertEquals(1620, vip.billFor(pasta));
        assertEquals(1500, new RegularCustomer(2).billFor(burger));
        assertEquals(1500, new CriticCustomer(2).billFor(burger));
    }

    @Test
    void criticAlwaysOrdersTheMostExpensiveDishOthersFollowTheRotation() {
        for (int id = 1; id <= 6; id++) {
            assertEquals("Pasta", new CriticCustomer(id).chooseItem(MENU).getName(), "critic #" + id);
        }
        assertEquals("Burger", new VIPCustomer(2).chooseItem(MENU).getName());
        assertEquals("Salad", new RegularCustomer(1).chooseItem(MENU).getName());
    }

    @Test
    void everyCustomerKindFollowsTheSameOrderAndServiceFlow() {
        record Case(IntFunction<Customer> kind, int expectedBill, int expectedSatisfactionAtPayment) {
        }
        // Pasta, played exactly like the brief's example (3 unserved turns before serving).
        List<Case> cases = List.of(
                new Case(RegularCustomer::new, 1800, 76),
                new Case(VIPCustomer::new, 1620, 85),
                new Case(CriticCustomer::new, 1800, 64));
        for (Case c : cases) {
            GameController game = new GameController(TestConfigs.onePastaCustomer(c.kind(), 1, 1, 0));
            TestConfigs.playPastaExample(game);
            Customer customer = game.getRestaurant().findCustomer(1).orElseThrow();
            assertEquals(CustomerState.PAID, customer.getState(), customer.getTypeName());
            assertEquals(c.expectedBill(), game.getRestaurant().getRevenueCents(), customer.getTypeName());
            assertEquals(c.expectedSatisfactionAtPayment(), customer.getPaidSatisfaction(), customer.getTypeName());
        }
    }

    @Test
    void anImpatientCriticLeavesBeforeARegularAndAVipLeavesLast() {
        assertEquals(9, turnsUntilTheyLeave(CriticCustomer::new));  // 100 - 9 x 12 < 0
        assertEquals(13, turnsUntilTheyLeave(RegularCustomer::new)); // 100 - 13 x 8 < 0
        assertEquals(20, turnsUntilTheyLeave(VIPCustomer::new));     // 100 - 20 x 5 = 0
    }

    @Test
    void baseSettingStillHasSixArrivalsOnTheRequiredTurnsWithAVipAndACritic() {
        GameConfig config = GameConfig.baseSetting();
        assertEquals(List.of(1, 4, 7, 10, 13, 16),
                config.getArrivals().stream().map(GameConfig.ArrivalSlot::turn).toList());
        List<String> kinds = config.getArrivals().stream()
                .map(slot -> slot.factory().apply(1).getTypeName()).toList();
        assertEquals(List.of("Regular", "VIP", "Regular", "Critic", "Regular", "Regular"), kinds);
    }

    @Test
    void theTurnControllerNeverChecksCustomerTypes() throws IOException {
        String controller = Files.readString(Path.of("src/main/java/restaurantrush/control/GameController.java"));
        assertFalse(controller.contains("instanceof"), "behaviour must be dispatched through the customer");
        assertFalse(controller.contains("getTypeName"), "behaviour must be dispatched through the customer");
        assertFalse(controller.contains("VIPCustomer") || controller.contains("CriticCustomer"));
    }

    @Test
    void mixedBaseSettingResultIsStillJudgedByTheSameThreeTargets() {
        GameController game = new GameController(GameConfig.baseSetting());
        while (!game.isGameOver()) {
            game.beginTurn();
            game.waiterWait();
            game.chefWait();
            game.endTurn();
        }
        GameResult result = game.getResult();
        assertEquals(GameResult.Outcome.DEFEAT, result.outcome());
        // Only customer 1 (Regular, turn 13) and customer 4 (Critic, turn 18) run out of patience in time;
        // the patient VIP (customer 2) would leave on turn 23.
        assertEquals(2, result.unhappyDepartures());
        assertEquals(CustomerState.ABANDONED, game.getRestaurant().findCustomer(4).orElseThrow().getState());
        assertEquals(CustomerState.WAITING_FOR_TABLE, game.getRestaurant().findCustomer(2).orElseThrow().getState());
    }

    private static int afterOneWait(Customer customer) {
        customer.waitOneTurn();
        return customer.getSatisfaction();
    }

    private static int turnsUntilTheyLeave(IntFunction<Customer> kind) {
        Customer customer = kind.apply(1);
        int turns = 0;
        while (customer.getSatisfaction() > 0) {
            customer.waitOneTurn();
            turns++;
        }
        return turns;
    }
}
