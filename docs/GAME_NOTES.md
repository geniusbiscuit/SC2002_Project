# Game notes: how we read the brief

Where the brief leaves a detail open, this is the rule our code follows. Every rule below is
covered by a unit test or a scripted run (see `TEST_NOTES.md`).

## Turn order

| Step | Where it lives | Notes |
|---|---|---|
| 1 Arrivals | `GameController.beginTurn()` | Customer joins the queue with satisfaction 100. Arrivals happen **before** the start-of-turn display so the manager sees the newcomer. |
| 2 Manager choices | `waiterXxx(...)`, `chefXxx(...)` | The controller enforces **Waiter first, then Chef**. A rejected choice returns an explanation and does not use up the task. |
| 3 Kitchen and serving | `Waiter`, `Chef`, `Order.isServableOn(turn)` | Each action takes effect as soon as it is chosen, so the Chef's list already includes an order the Waiter took this turn. A dish is servable only if it became READY on an earlier turn. |
| 4 Eating and payment | `GameController.endTurn()` | The Cashier pays first (only customers who were READY_TO_PAY before this turn), then diners served on an earlier turn become READY_TO_PAY. |
| 5 Waiting and abandonment | `Restaurant.applyWaitingLoss()` | Every customer who has not been served loses satisfaction. Anyone at 0 leaves: order cancelled, table freed. |
| 6 Result | `GameController.getResult()` | Only after turn 18. The game never ends early. |

## Decisions we made

1. **Customer ids** are arrival order (1 to 6).
2. **Seating:** the manager picks the customer; the customer sits at the lowest-numbered free table.
3. **What customers order (deterministic rule):** customer *n* orders dish *(n - 1) mod 3* of the menu,
   i.e. 1 Salad, 2 Burger, 3 Pasta, 4 Salad, 5 Burger, 6 Pasta. The choice is printed when the order is taken.
4. **"Oldest first" for the Cashier** means the customer who has been READY_TO_PAY longest (ties: lower id).
5. **A READY dish that has not been served yet** still counts as "not served", so that customer keeps losing satisfaction.
6. **"Wait" is a real choice:** it uses the Waiter's or Chef's task for the turn.
7. **Average satisfaction target** is compared with integers (`sum >= 60 * paid customers`), so there is no rounding error.
   The displayed average has one decimal.
8. **Money** is stored as integer cents everywhere and printed as dollars with two decimals.
9. **End-of-turn results** (served count, unhappy departures, revenue, average satisfaction, turns used) are printed after every turn and again in the final summary.
10. **Statistics are derived from customer state** (never kept in separate counters), so revenue cannot include an unpaid or abandoned order.

## Stage 2 decisions

11. **Mixed crowd:** the 2nd arrival (turn 4) is a VIP and the 4th (turn 10) is a Critic. Still six arrivals on turns 1, 4, 7, 10, 13, 16.
12. **VIP bill** = 90% of the list price, rounded half-up to the cent (`Money.percentOf`). The order message shows the list price when a discount applies.
13. **Critic special behaviour (our definition):** a Critic always orders the most expensive dish on the menu (Pasta, $18.00, 2 prep units).
    That puts the longest cooking job on the Chef and, with a loss of 12 per waiting turn, forces the manager to decide whether the Critic is worth the staff time.
14. **Dispatch:** `GameController` never asks what kind of customer it is. Satisfaction loss, bill and dish choice are methods on `Customer` that the subclasses override.
    A test checks that the controller source contains no `instanceof` or type-name checks.

## Stage 3 decisions

15. **Settings:** Host, combos and Happy Hour are switches in `GameConfig`. `fullSetting()` (the default game) turns them on;
    `baseSetting()` (`--classic`) and `regularOnlySetting()` (`--regular-only`) leave them off, so Stage 1 and 2 runs replay exactly.
16. **Host** acts before the Waiter. A customer the Host seats can therefore have the order taken by the Waiter in the same turn.
    The Host and the Waiter share one set of seating rules (`Seating`).
17. **Which customers order a combo (our documented rule):** customers whose id is a multiple of 3 order a combo:
    customer 3 the first (Salad + Pasta, $24.30, 3 units), customer 6 the second (Burger + Pasta, $29.70, 3 units).
    Everyone else keeps the Stage 1 rule. The Critic still orders the most expensive dish on the whole menu, which now is Burger + Pasta.
18. **Combo price** = 90% of the two components' prices added, rounded half-up to the cent. Preparation units are added.
19. **Happy Hour timing:** while it is unused, the manager is asked at the very start of each turn (before any staff acts).
    Switching it on at turn *n* covers turns *n* and *n+1*. Recovery of 15 points happens immediately on turn *n* and again at the start of turn *n+1*.
    The remaining duration is shown as 2, then 1 (turns left including the current one).
20. **"Still waiting for a table or food"** means queued, seated, or ordered but not yet served (a READY dish that has not been served still counts).
    Served, eating and ready-to-pay customers do not recover, and neither do customers who have already left.
21. **Pricing order:** customer discount first (VIP 10%), then Happy Hour 20%, each rounded half-up. The bill is locked when the order is taken,
    so Happy Hour never changes an order already placed, and an order taken after it ends pays the normal price.

## Why the turn-16 customer cannot pay

Seated turn 16, order turn 17 at the earliest, served turn 18 at the earliest (and only for a one-unit dish),
eating finishes turn 19. So at most five customers can pay. This is intended by the brief.
