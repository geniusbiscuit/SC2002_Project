package restaurantrush.control;

import java.util.OptionalDouble;

/** The final result after the last turn, with each of the three targets reported separately. */
public record GameResult(Outcome outcome, int paidCount, int revenueCents, int satisfactionSum,
                         int servedCount, int unhappyDepartures, int turnsUsed,
                         boolean paidTargetMet, boolean revenueTargetMet, boolean satisfactionTargetMet) {

    public enum Outcome {
        VICTORY, DEFEAT
    }

    public OptionalDouble averageSatisfaction() {
        return paidCount == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) satisfactionSum / paidCount);
    }
}
