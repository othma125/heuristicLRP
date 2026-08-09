// Author: Othmane

package Algorithm.Data;

/**
 * A candidate depot of an LRP instance: where it stands, how much it can ship,
 * and what opening it costs. Immutable, so instances can be shared freely across
 * the search threads.
 *
 * <p>Distances are not held here; they live in the instance-wide matrix of
 * {@link InputData}, which the depot's {@code index} keys into.
 *
 * @author Othmane EL YAAKOUBI
 */
public final class Depot {

    private final int index;
    private final Location location;
    private final int capacity;
    private final double openingCost;

    /**
     * @param index       0-based position of the depot in the instance
     * @param location    where the depot stands
     * @param capacity    total demand the depot can serve
     * @param openingCost cost paid once the depot is used by at least one route
     */
    public Depot(int index, Location location, int capacity, double openingCost) {
        this.index = index;
        this.location = location;
        this.capacity = capacity;
        this.openingCost = openingCost;
    }

    public int index() {
        return this.index;
    }

    public Location location() {
        return this.location;
    }

    public int capacity() {
        return this.capacity;
    }

    public double openingCost() {
        return this.openingCost;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Depot other && other.index == this.index;
    }

    @Override
    public int hashCode() {
        return this.index;
    }

    @Override
    public String toString() {
        return "Depot[index=" + this.index + ", location=" + this.location
                + ", capacity=" + this.capacity + ", openingCost=" + this.openingCost + "]";
    }
}
