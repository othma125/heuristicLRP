// Author: Othmane

package Algorithm.Metaheuristics;


import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

import Algorithm.Data.InputData;
import Algorithm.Solution.GiantTour;

/**
 * Base class for metaheuristic solvers. Holds the problem instance, tracks the
 * best giant tour found and the time it was reached, and derives a
 * stagnation-based minimum running time from the instance size. Concrete
 * solvers implement {@link #run()}.
 *
 * @author Othmane EL YAAKOUBI
 */
public abstract class MetaHeuristic {
    InputData data;
    long startTime;// Start Time in milliseconds
    long endTime;
    long bestSolutionReachingTime;
    private GiantTour bestGiantTour = null;
    private final ReentrantLock bestLock = new ReentrantLock();
    public final long stagnationMinTime;

    /**
     * Where the search reports progress. Defaults to the process standard output;
     * the web server points it at the requesting client's event stream so that
     * concurrent runs never share one log.
     */
    public PrintStream log = System.out;

    /** Incumbent trace: one {time_ms_since_StartTime, cost} pair per improvement. */
    public final List<long[]> trace = Collections.synchronizedList(new ArrayList<>());


    /**
     * @param data the problem instance to solve
     */
    public MetaHeuristic(InputData data) {
        this.data = data;
        this.stagnationMinTime = (long) Math.max(100, 100 * Math.sqrt(data.getSize()));
    }

    /**
     * Records {@code newGt} as the incumbent if it improves on the current
     * best, updating the best-reaching timestamp and logging the improvement.
     * Guarded by {@link #bestLock} so the concurrent crossovers cannot interleave
     * the comparison with the update.
     *
     * @param newGt a candidate giant tour
     * @return {@code true} if the incumbent was replaced
     */
    public boolean setBestSolution(GiantTour newGt) {
        if (newGt == null)
            return false;
        this.bestLock.lock();
        try {
            if (this.bestGiantTour == null || newGt == this.bestGiantTour || newGt.compareTo(this.bestGiantTour) < 0) {
                this.bestSolutionReachingTime = System.currentTimeMillis();
                this.bestGiantTour = newGt;
                this.log.println(String.format(Locale.US, "%.2f", this.bestGiantTour.getFitness())
                        + " after " + (this.bestSolutionReachingTime - this.startTime) + " ms");
                this.trace.add(new long[]{this.bestSolutionReachingTime - this.startTime, (long) this.bestGiantTour.getFitness()});
                return true;
            }
            return false;
        } finally {
            this.bestLock.unlock();
        }
    }

    /**
     * @return the best giant tour found so far, or {@code null} if none
     */
    public GiantTour getBestGiantTour() {
        return this.bestGiantTour;
    }

    /**
     * @return {@code true} if a feasible solution has been found
     */
    public boolean isFeasible() {
        return this.bestGiantTour != null;
    }

    /**
     * @return the total running time in milliseconds
     */
    public long getRunningTime() {
        return this.endTime;
    }

    /**
     * Requests the running solver to stop early; it will return the best tour found so far.
     * The flag lives on the instance so the split and its local search can abort mid-run
     * instead of finishing the current giant tour first.
     */
    public void requestStop() {
        this.data.requestStop();
    }

    /**
     * @return {@code true} once {@link #requestStop()} has been called
     */
    protected boolean isStopRequested() {
        return this.data.isStopRequested();
    }

    /**
     * Stagnation-based stopping rule: always continues while the last
     * improvement is within {@code stagnationMinTime}, then continues with a
     * probability that decays as the stagnation stretch grows relative to the
     * total elapsed time.
     *
     * @return {@code true} if the search should keep running
     */
    protected boolean nonStopCondition() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.bestSolutionReachingTime <= this.stagnationMinTime)
            return true;
        double probability = currentTime - this.bestSolutionReachingTime - this.stagnationMinTime;
        probability /= (double) (currentTime - this.startTime);
        return ThreadLocalRandom.current().nextDouble() > probability;
    }

    /**
     * Runs the metaheuristic to completion.
     */
    public abstract void run();
}