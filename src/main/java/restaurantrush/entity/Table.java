package restaurantrush.entity;

/** A table. It stays occupied until its customer pays or leaves. */
public class Table {

    private final int number;
    private Customer occupant;

    public Table(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    public boolean isFree() {
        return occupant == null;
    }

    public Customer getOccupant() {
        return occupant;
    }

    void occupy(Customer customer) {
        if (occupant != null) {
            throw new InvalidActionException("Table " + number + " is already occupied.");
        }
        occupant = customer;
    }

    void release() {
        occupant = null;
    }
}
