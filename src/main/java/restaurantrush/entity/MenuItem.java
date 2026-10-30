package restaurantrush.entity;

/** A dish that can be ordered: name, price in integer cents and preparation units. */
public class MenuItem {

    private final String name;
    private final int priceCents;
    private final int prepUnits;

    public MenuItem(String name, int priceCents, int prepUnits) {
        if (priceCents <= 0 || prepUnits <= 0) {
            throw new IllegalArgumentException("Price and preparation units must be positive");
        }
        this.name = name;
        this.priceCents = priceCents;
        this.prepUnits = prepUnits;
    }

    public String getName() {
        return name;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public int getPrepUnits() {
        return prepUnits;
    }

    @Override
    public String toString() {
        return name;
    }
}
