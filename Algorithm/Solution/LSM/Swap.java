// Author: Othmane

package Algorithm.Solution.LSM;

import Algorithm.Data.InputData;
import Algorithm.Solution.Route;
import Algorithm.Solution.Solution;

/**
 * Swap move: exchanges the stop at position {@code i} of the first route with
 * the stop at position {@code j} of the second (or the same) route. The two
 * routes may serve different depots, in which case the two stops change depot
 * with the routes that take them.
 *
 * @author Othmane EL YAAKOUBI
 */
public class Swap extends LocalSearchMove {

    private final int firstBorder;

    /**
     * @param data   the problem instance
     * @param i      position in the first route
     * @param j      position in the second route
     * @param routes one route (intra-route) or two routes (inter-route)
     */
    public Swap(InputData data, int i, int j, Route... routes) {
        super("Swap", i, j, routes);
        this.firstBorder = this.firstRoute.getLength();
    }

    /** {@inheritDoc} */
    @Override
    public void setGain(InputData data) {

        // --- First route: predecessor of I ---
        if (this.i == 0) {
            this.gain += data.getDepotToStopDistance(this.firstRoute.getDepot(), this.secondRoute.getStop(this.j));
            this.gain -= data.getDepotToStopDistance(this.firstRoute.getDepot(), this.firstRoute.getStop(this.i));
        }
        else {
            this.gain += data.getTwoStopsDistance(this.firstRoute.getStop(this.i - 1), this.secondRoute.getStop(this.j));
            this.gain -= data.getTwoStopsDistance(this.firstRoute.getStop(this.i - 1), this.firstRoute.getStop(this.i));
        }
        // --- Middle part ---
        if (this.i + 1 < this.j && this.oneSequence) {
            this.gain += data.getTwoStopsDistance(this.secondRoute.getStop(this.j - 1), this.firstRoute.getStop(this.i));
            this.gain -= data.getTwoStopsDistance(this.secondRoute.getStop(this.j - 1), this.secondRoute.getStop(this.j));
            this.gain += data.getTwoStopsDistance(this.secondRoute.getStop(this.j), this.firstRoute.getStop(this.i + 1));
            this.gain -= data.getTwoStopsDistance(this.firstRoute.getStop(this.i), this.firstRoute.getStop(this.i + 1));
        }
        else if (!this.oneSequence) {
            if (this.j > 0) {
                this.gain += data.getTwoStopsDistance(this.secondRoute.getStop(this.j - 1), this.firstRoute.getStop(this.i));
                this.gain -= data.getTwoStopsDistance(this.secondRoute.getStop(this.j - 1), this.secondRoute.getStop(this.j));
            }
            else {
                // Opening leg of the second route, so measured from the depot serving it.
                this.gain += data.getDepotToStopDistance(this.secondRoute.getDepot(), this.firstRoute.getStop(this.i));
                this.gain -= data.getDepotToStopDistance(this.secondRoute.getDepot(), this.secondRoute.getStop(this.j));
            }
            if (this.i + 1 < this.firstBorder) {
                this.gain += data.getTwoStopsDistance(this.secondRoute.getStop(this.j), this.firstRoute.getStop(this.i + 1));
                this.gain -= data.getTwoStopsDistance(this.firstRoute.getStop(this.i), this.firstRoute.getStop(this.i + 1));
            }
            else {
                this.gain += data.getStopToDepotDistance(this.secondRoute.getStop(this.j), this.firstRoute.getDepot());
                this.gain -= data.getStopToDepotDistance(this.firstRoute.getStop(this.i), this.firstRoute.getDepot());
            }
        }
        // --- Successor of J ---
        if (this.j + 1 < this.border) {
            this.gain += data.getTwoStopsDistance(this.firstRoute.getStop(this.i), this.secondRoute.getStop(this.j + 1));
            this.gain -= data.getTwoStopsDistance(this.secondRoute.getStop(this.j), this.secondRoute.getStop(this.j + 1));
        }
        else {
            // Closing leg of the second route, so measured from the depot serving it.
            this.gain += data.getStopToDepotDistance(this.firstRoute.getStop(this.i), this.secondRoute.getDepot());
            this.gain -= data.getStopToDepotDistance(this.secondRoute.getStop(this.j), this.secondRoute.getDepot());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void perform(InputData data) {
        if (this.oneSequence) {
            this.firstRoute.swap(this.i, this.j);
            this.firstRoute.improve(this.gain);
        }
        else {
            int[] seq1 = this.firstRoute.getSequence().clone();
            int[] seq2 = this.secondRoute.getSequence().clone();
            int aux = seq1[this.i];
            seq1[this.i] = seq2[this.j];
            seq2[this.j] = aux;
            this.rebuild(data, seq1, seq2);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isFeasible(InputData data, Solution solution) {
        if (this.oneSequence)
            return true;
        int transferred = data.getDemand(this.secondRoute.getStop(this.j)) - data.getDemand(this.firstRoute.getStop(this.i));
        int demand1 = this.firstRoute.getSumDemand() + transferred;
        int demand2 = this.secondRoute.getSumDemand() - transferred;
        if (demand1 > data.getCapacity() || demand2 > data.getCapacity())
            return false;
        // Neither route can empty, so the depots only have to have room for what they end
        // up shipping: the heavier stop moving one way is what can overload a depot.
        return this.hasRoom(solution, this.firstRoute, demand1) && this.hasRoom(solution, this.secondRoute, demand2);
    }

    @Override
    public String toString() {
        return this.name + " (" + this.i + ";" + this.j + ")";
    }
}
