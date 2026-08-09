// Author: Othmane

package Algorithm.Data;

/**
 * A point of the instance plane, shared by depots and customers.
 *
 * @param abscissa x coordinate
 * @param ordinate y coordinate
 *
 * @author Othmane EL YAAKOUBI
 */
public record Location(double abscissa, double ordinate) {

    /**
     * @param other the point to measure to
     * @return the Euclidean distance between the two points
     */
    public double distanceTo(Location other) {
        return Math.hypot(this.abscissa - other.abscissa, this.ordinate - other.ordinate);
    }
}
