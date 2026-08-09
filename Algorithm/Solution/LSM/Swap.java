// Author: Othmane

package Algorithm.Solution.LSM;

import Algorithm.Data.Depot;
import Algorithm.Data.InputData;
import Algorithm.Solution.Route;

/**
 * Swap move: exchanges the stop at position {@code I} of the first route with
 * the stop at position {@code J} of the second (or the same) route.
 *
 * @author Othmane EL YAAKOUBI
 */
public class Swap extends LocalSearchMove {

    private final int FirstBorder;

    /**
     * @param data   the problem instance
     * @param i      position in the first route
     * @param j      position in the second route
     * @param routes one route (intra-route) or two routes (inter-route)
     */
    public Swap(InputData data, int i, int j, Route... routes) {
        super("Swap", i, j, routes);
        this.FirstBorder = this.FirstRoute.getLength();
    }

    /** {@inheritDoc} */
    @Override
    public void setGain(InputData data) {

        // --- First route: predecessor of I ---
        if (this.I == 0) {
            this.Gain += data.getDepotToStopDistance(this.FirstRoute.getDepot(), this.SecondRoute.getStop(this.J));
            this.Gain -= data.getDepotToStopDistance(this.FirstRoute.getDepot(), this.FirstRoute.getStop(this.I));
        }
        else {
            this.Gain += data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - 1), this.SecondRoute.getStop(this.J));
            this.Gain -= data.getTwoStopsDistance(this.FirstRoute.getStop(this.I - 1), this.FirstRoute.getStop(this.I));
        }
        // --- Middle part ---
        if (this.I + 1 < this.J && this.OneSequence) {
            this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J - 1), this.FirstRoute.getStop(this.I));
            this.Gain -= data.getTwoStopsDistance(this.SecondRoute.getStop(this.J - 1), this.SecondRoute.getStop(this.J));
            this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J), this.FirstRoute.getStop(this.I + 1));
            this.Gain -= data.getTwoStopsDistance(this.FirstRoute.getStop(this.I), this.FirstRoute.getStop(this.I + 1));
        }
        else if (!this.OneSequence) {
            if (this.J > 0) {
                this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J - 1), this.FirstRoute.getStop(this.I));
                this.Gain -= data.getTwoStopsDistance(this.SecondRoute.getStop(this.J - 1), this.SecondRoute.getStop(this.J));
            }
            else {
                // Opening leg of the second route, so measured from the depot serving it.
                this.Gain += data.getDepotToStopDistance(this.SecondRoute.getDepot(), this.FirstRoute.getStop(this.I));
                this.Gain -= data.getDepotToStopDistance(this.SecondRoute.getDepot(), this.SecondRoute.getStop(this.J));
            }
            if (this.I + 1 < this.FirstBorder) {
                this.Gain += data.getTwoStopsDistance(this.SecondRoute.getStop(this.J), this.FirstRoute.getStop(this.I + 1));
                this.Gain -= data.getTwoStopsDistance(this.FirstRoute.getStop(this.I), this.FirstRoute.getStop(this.I + 1));
            }
            else {
                this.Gain += data.getStopToDepotDistance(this.SecondRoute.getStop(this.J), this.FirstRoute.getDepot());
                this.Gain -= data.getStopToDepotDistance(this.FirstRoute.getStop(this.I), this.FirstRoute.getDepot());
            }
        }
        // --- Successor of J ---
        if (this.J + 1 < this.Border) {
            this.Gain += data.getTwoStopsDistance(this.FirstRoute.getStop(this.I), this.SecondRoute.getStop(this.J + 1));
            this.Gain -= data.getTwoStopsDistance(this.SecondRoute.getStop(this.J), this.SecondRoute.getStop(this.J + 1));
        }
        else {
            // Closing leg of the second route, so measured from the depot serving it.
            this.Gain += data.getStopToDepotDistance(this.FirstRoute.getStop(this.I), this.SecondRoute.getDepot());
            this.Gain -= data.getStopToDepotDistance(this.SecondRoute.getStop(this.J), this.SecondRoute.getDepot());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void Perform(InputData data) {
        if (this.OneSequence) {
            this.FirstRoute.Swap(this.I, this.J);
            this.FirstRoute.Improve(this.Gain);
        }
        else {
            Depot first_depot = this.FirstRoute.getDepot();
            Depot second_depot = this.SecondRoute.getDepot();
            int[] seq1 = this.FirstRoute.getSequence().clone();
            int[] seq2 = this.SecondRoute.getSequence().clone();
            int aux = seq1[this.I];
            seq1[this.I] = seq2[this.J];
            seq2[this.J] = aux;
            this.FirstRoute = new Route(data, first_depot, seq1);
            this.SecondRoute = new Route(data, second_depot, seq2);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isFeasible(InputData data) {
        if (this.OneSequence)
            return true;
        int availableCapacity1 = data.getCapacity();
        for (int i = 0; i < this.FirstBorder; i++) 
            if (i != this.I) {
                availableCapacity1 -= data.getDemand(this.FirstRoute.getStop(i));
            }
        if (availableCapacity1 < 0 || data.getDemand(this.SecondRoute.getStop(this.J)) > availableCapacity1)
            return false;
        int availableCapacity2 = data.getCapacity();
        for (int j = 0; j < this.Border; j++) 
            if (j != this.J) {
                availableCapacity2 -= data.getDemand(this.SecondRoute.getStop(j));
            }
        return data.getDemand(this.FirstRoute.getStop(this.I)) <= availableCapacity2;
    }

    @Override
    public String toString() {
        return this.Name + " (" + this.I + ";" + this.J + ")";
    }
}
