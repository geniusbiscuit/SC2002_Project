package restaurantrush.control;

import java.util.List;

/** What happened automatically at the end of a turn (eating, payment, abandonment). */
public record TurnSummary(int turn, List<String> events) {
}
