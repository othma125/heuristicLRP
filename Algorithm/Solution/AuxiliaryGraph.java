package Algorithm.Solution;

// Author: Othmane

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Phaser;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Stream;

import Algorithm.Data.InputData;

/**
 * The route-first/cluster-second split structure. Given one or more giant
 * tours, it builds a directed graph whose nodes are tour positions and whose
 * arcs are feasible routes (respecting capacity); the shortest source-to-sink
 * path is the optimal partition of the tour into vehicle routes.
 *
 * <p>Arcs are relaxed concurrently: each {@link ArcSetter} is a
 * {@link RecursiveAction} submitted to the common {@link ForkJoinPool}, and a
 * {@link Phaser} keeps the constructor blocked until the whole graph has been
 * explored. The {@code bound} prunes partial solutions that cannot improve on
 * the incumbent cost.
 *
 * @author Othmane EL YAAKOUBI
 */
public class AuxiliaryGraph implements AutoCloseable {

    private final int length;
    private final double bound;
    private final int[][] tours;
    private AuxiliaryGraphNode[] nodes;
    private final InputData data;
    private final Set<ArcSetter> arcsSetters;
    private final Phaser phaser = new Phaser(1);

    /**
     * Builds and fully explores the split graph for the given giant tours,
     * blocking until all arcs have been relaxed.
     *
     * @param data        the problem instance
     * @param bound       cost upper bound used to prune partial solutions
     * @param giantTours one or more tours to split (more than one enables the graph-based crossover)
     */
    AuxiliaryGraph(InputData data, double bound, GiantTour ... giantTours) {
        this.data = data;
        this.bound = bound;
        // Snapshot the parents once: the individuals being recombined are re-split by other
        // threads, which swaps their sequence, and every walk must see one stable permutation.
        this.tours = new int[giantTours.length][];
        for (int i = 0; i < giantTours.length; i++)
            this.tours[i] = giantTours[i].getSequenceSnapshot();
        this.length = this.tours[0].length;
        this.nodes = new AuxiliaryGraphNode[this.length + 1];
        for (int i = 0; i <= this.length; i++) 
            this.nodes[i] = new AuxiliaryGraphNode(i);
        this.arcsSetters = ConcurrentHashMap.newKeySet();
        for (int[] tour : this.tours) {
            if (data.isStopRequested())
                break;
            ArcSetter setter = new ArcSetter(this, this.nodes[0], null, tour);
            this.arcsSetters.add(setter);
            this.phaser.register();
            ForkJoinPool.commonPool().execute(setter);
        }
        this.phaser.arriveAndAwaitAdvance();
        if (this.isFeasible())
            this.getLastNode().getSolutions()
                                .stream()
                                .forEach(s -> s.interRoutesLocalSearch(data));
    }

    /**
     * Spawns successor arc setters from {@code node} once every setter still
     * running has advanced past it, so the node's labels are final before they
     * are extended. Only the node's Pareto set is extended, and solutions above
     * the pruning bound are skipped.
     *
     * @param node the node whose outgoing arcs should be scheduled
     */
    void setNewSetters(AuxiliaryGraphNode node) {
        // A stopped run spawns no further arcs: the setters still in flight drain, the
        // phaser advances, and the constructor returns instead of exploring the graph.
        if (node.nodeIndex == this.length || this.data.isStopRequested())
            return;
        node.lock.lock();
        try {
            boolean allMatch = true;
            for (ArcSetter setter : this.arcsSetters) 
                if (setter.startingNode == node || setter.nodeProcessingWith < node.nodeIndex) {
                    allMatch = false;
                    break;
                }
            if (allMatch) 
                node.getParetoSet().stream()
                                    .filter(solution -> solution.getTotalDistance() < this.bound)
                                    .forEach(solution -> {
                                        for (int[] tour : this.tours) {
                                            ArcSetter setter = new ArcSetter(this, node, solution, tour);
                                            this.arcsSetters.add(setter);
                                            this.phaser.register();
                                            ForkJoinPool.commonPool().execute(setter);
                                        }
                                    });
        } finally {
            node.lock.unlock();
        }
    }
    

    /**
     * @return the sink node (end of the tour)
     */
    AuxiliaryGraphNode getLastNode() {
        return this.getNode(this.length);
    }

    /**
     * @param i node index
     * @return the node at the given index
     */
    AuxiliaryGraphNode getNode(int i) {
        return this.nodes[i];
    }

    /**
     * @return {@code true} if the sink node was reached, i.e. a full split
     *         exists
     */
    boolean isFeasible() {
        return this.getLastNode().isFeasible();
    }

    /**
     * @return the cost of the optimal split (sink node label)
     */
    double getLabel() {
        return this.getLastNode().getLabel();
    }

    /**
     * @return the number of distinct optimal splits stored on the sink node
     */
    int getSolutionsCount() {
        return this.getLastNode().getSolutions().size();
    }

    /**
     * @return the number of routes in the optimal split
     */
    int getRoutesCount() {
        return this.getLastNode().getRoutesCount();
    }

    /**
     * @return the CVRPLIB route listing of the optimal split
     */
    String export() {
        return this.getLastNode().export();
    }

    /**
     * Applies inter-route local search to the optimal split and returns its
     * flattened giant-tour sequence.
     *
     * @param data the problem instance
     * @return the improved sequence
     */
    int[] getNewSequence(InputData data) {
        return this.getLastNode().getNewSequence(data);
    }

    @Override
    public String toString() {
        return this.getLastNode().toString();
    }

    /**
     * Releases the graph by closing all of its nodes and dropping the node
     * array. Runs on a background thread so the caller does not block on the
     * teardown of a large graph.
     */
    @Override
    public void close() {
        new Thread(() -> {
            for (AuxiliaryGraphNode node : this.nodes)
                node.close();
            this.nodes = null;
        }).start();
    }

    // Getter methods for ArcSetter access
    int getLength() {
        return this.length;
    }

    InputData getData() {
        return this.data;
    }

    int[][] getTours() {
        return this.tours;
    }

    Phaser getPhaser() {
        return this.phaser;
    }

    Set<ArcSetter> getArcsSetters() {
        return this.arcsSetters;
    }
}
