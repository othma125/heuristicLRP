// Author: Othmane

package Algorithm.Data;

/**
 * A candidate depot of an LRP instance: where it stands, how much it can ship,
 * and what opening it costs. Immutable, so instances can be shared freely across
 * the search threads.
 *
 * <p>Distances are not held here; they live in the instance-wide matrix of
 * {@link InputData}, keyed by the depot's 0-based index.
 *
 * @param location    where the depot stands
 * @param capacity    total demand the depot can serve
 * @param openingCost cost paid once the depot is used by at least one route
 *
 * @author Othmane EL YAAKOUBI
 */
public record Depot(Location location, int capacity, double openingCost) {
}
