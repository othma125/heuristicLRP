// Author: Othmane

package Algorithm.Solution.LSM;

import Algorithm.Data.InputData;
import Algorithm.Solution.Route;
import Algorithm.Solution.Solution;

/**
 * 2-opt move. Intra-route it reverses the segment between the two positions;
 * inter-route it reconnects the two routes by swapping their tails, reversing
 * one prefix. Removes edge crossings. The inter-route form is restricted to
 * routes sharing a depot.
 *
 * @author Othmane EL YAAKOUBI
 */
public class _2Opt extends LocalSearchMove {

    private final int firstBorder;

    /**
     * @param data   the problem instance
     * @param i      position in the first route
     * @param j      position in the second route
     * @param routes one route (intra-route) or two routes (inter-route)
     */
    public _2Opt(InputData data, int i, int j, Route... routes) {
        super("2Opt", i, j, routes);
        this.firstBorder = this.firstRoute.getLength();
    }

    /** {@inheritDoc} */
    @Override
    public void setGain(InputData data) {
        if (this.i == 0) {
            this.gain += data.getDepotToStopDistance(this.firstRoute.getDepot(), this.secondRoute.getStop(this.j));
            this.gain -= data.getDepotToStopDistance(this.firstRoute.getDepot(), this.firstRoute.getStop(this.i));
        }
        else {
            this.gain += data.getTwoStopsDistance(this.firstRoute.getStop(this.i - 1), this.secondRoute.getStop(this.j));
            this.gain -= data.getTwoStopsDistance(this.firstRoute.getStop(this.i - 1), this.firstRoute.getStop(this.i));
        }
        if (this.j + 1 < this.border) {
            this.gain += data.getTwoStopsDistance(this.firstRoute.getStop(this.i), this.secondRoute.getStop(this.j + 1));
            this.gain -= data.getTwoStopsDistance(this.secondRoute.getStop(this.j), this.secondRoute.getStop(this.j + 1));
        }
        else {
            // The tail of the first route closes the second one, so it returns to its depot.
            this.gain += data.getStopToDepotDistance(this.firstRoute.getStop(this.i), this.secondRoute.getDepot());
            this.gain -= data.getStopToDepotDistance(this.secondRoute.getStop(this.j), this.secondRoute.getDepot());
        }
        if (!this.oneSequence && !this.firstRoute.getDepot().equals(this.secondRoute.getDepot())) {
            // The two routes swap tails, so the end of the first one becomes the start of the
            // second and the start of the second becomes the end of the first: both legs
            // change depot, which only costs anything when the depots differ.
            int firstLast = this.firstRoute.getLast();
            int secondFirst = this.secondRoute.getStop(0);
            this.gain += data.getStopToDepotDistance(firstLast, this.secondRoute.getDepot());
            this.gain -= data.getStopToDepotDistance(firstLast, this.firstRoute.getDepot());
            this.gain += data.getDepotToStopDistance(this.firstRoute.getDepot(), secondFirst);
            this.gain -= data.getDepotToStopDistance(this.secondRoute.getDepot(), secondFirst);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void perform(InputData data) {
        if (this.oneSequence) {
            this.firstRoute._2Opt(this.i, this.j);
            this.firstRoute.improve(this.gain);
        }
        else {
            int[] seq1 = new int[this.i + this.j + 1];
            for (int i = 0; i < this.i; i++) 
                seq1[i] = this.firstRoute.getStop(i);
            for (int i = 0; i <= this.j; i++) 
                seq1[i + this.i] = this.secondRoute.getStop(this.j - i);
            int[] seq2 = new int[this.secondRoute.getLength() + this.firstRoute.getLength() - seq1.length];
            int k = 0;
            for (int i = this.firstRoute.getLength() - 1; i >= this.i; i--) {
                seq2[k] = this.firstRoute.getStop(i);
                k++;
            }
            for (int i = this.j + 1; i < this.secondRoute.getLength(); i++) {
                seq2[k] = this.secondRoute.getStop(i);
                k++;
            }
            this.rebuild(data, seq1, seq2);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isFeasible(InputData data, Solution solution) {
        if (this.oneSequence)
            return true;
        // Reconnecting sends a whole segment of demand each way at once, so across two
        // depots it rewrites what both of them ship. The neighbourhood is kept inside a
        // single depot, where only the two vehicle loads change.
        if (!this.firstRoute.getDepot().equals(this.secondRoute.getDepot()))
            return false;
        int availableCapacity1 = data.getCapacity();
        for (int i = 0; i < this.i; i++) 
            availableCapacity1 -= data.getDemand(this.firstRoute.getStop(i));
        int sumDemand2 = 0;
        for (int j = 0; j <= this.j; j++) 
            sumDemand2 += data.getDemand(this.secondRoute.getStop(j));
        if (sumDemand2 > availableCapacity1 || availableCapacity1 < 0)
            return false;
        int availableCapacity2 = data.getCapacity();
        for (int j = this.j + 1; j < this.border; j++) 
            availableCapacity2 -= data.getDemand(this.secondRoute.getStop(j));
        int sumDemand1 = 0;
        for (int i = this.i; i < this.firstBorder; i++) 
            sumDemand1 += data.getDemand(this.firstRoute.getStop(i));
        return sumDemand1 <= availableCapacity2;
    }

    @Override
    public String toString() {
        return this.name + " (" + this.i + ";" + this.j + ")";
    }
}
