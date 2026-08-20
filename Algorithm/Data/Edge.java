// Author: Othmane

package Algorithm.Data;

/**
 * Undirected edge between two nodes, used as a symmetric key into the distance
 * map of {@link InputData}. The endpoints are stored in canonical order
 * ({@code x <= y}) so that {@code (a, b)} and {@code (b, a)} are equal and hash
 * identically; a record supplies the {@code equals}/{@code hashCode} the map
 * needs.
 *
 * @param x the smaller endpoint
 * @param y the larger endpoint
 *
 * @author Othmane EL YAAKOUBI
 */
public record Edge(int x, int y) {

    /** Normalizes the endpoints so the edge is order-independent. */
    public Edge {
        if (x > y) {
            int swap = x;
            x = y;
            y = swap;
        }
    }
}
