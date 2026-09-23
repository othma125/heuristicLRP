// Author: Othmane

package Algorithm.Solution.LSM;

import Algorithm.Data.InputData;
import Algorithm.Solution.Route;
import Algorithm.Solution.Solution;

/**
 * Right-shift (or-opt) move: relocates a block of {@code degree + 1} stops
 * starting at position {@code j} of the second route into position {@code i} of
 * the first (or the same) route. The {@code with2Opt} flag reverses the
 * relocated block. It is the mirror image of {@link LeftShift}, so the block
 * may likewise cross over to the first route's depot.
 *
 * @author Othmane EL YAAKOUBI
 */
public class RightShift extends LocalSearchMove {

    private final int degree;
    private final boolean with2Opt;

    /**
     * @param data     the problem instance
     * @param with2opt whether the relocated block is reversed
     * @param degree   number of extra stops moved with the anchor (block size
     *                 is {@code degree + 1})
     * @param i        insertion position in the first route
     * @param j        anchor position in the second route
     * @param routes   one route (intra-route) or two routes (inter-route)
     */
    public RightShift(InputData data, boolean with2opt, int degree, int i, int j, Route... routes) {
        super("RightShift", i, j, routes);
        this.with2Opt = with2opt;
        this.degree = degree;
    }

    /** {@inheritDoc} */
    @Override
    public void setGain(InputData data) {
        if (this.with2Opt) {
            this.gain += data.getTwoStopsDistance(this.secondRoute.getStop(this.j), this.firstRoute.getStop(this.i));
            if (this.i == 0) {
                this.gain += data.getDepotToStopDistance(this.firstRoute.getDepot(), this.secondRoute.getStop(this.j + this.degree));
                this.gain -= data.getDepotToStopDistance(this.firstRoute.getDepot(), this.firstRoute.getStop(this.i));
            }
            else {
                this.gain += data.getTwoStopsDistance(this.firstRoute.getStop(this.i - 1), this.secondRoute.getStop(this.j + this.degree));
                this.gain -= data.getTwoStopsDistance(this.firstRoute.getStop(this.i - 1), this.firstRoute.getStop(this.i));
            }
        }
        else {
            this.gain += data.getTwoStopsDistance(this.secondRoute.getStop(this.j + this.degree), this.firstRoute.getStop(this.i));
            if (this.i == 0) {
                this.gain += data.getDepotToStopDistance(this.firstRoute.getDepot(), this.secondRoute.getStop(this.j));
                this.gain -= data.getDepotToStopDistance(this.firstRoute.getDepot(), this.firstRoute.getStop(this.i));
            }
            else {
                this.gain += data.getTwoStopsDistance(this.firstRoute.getStop(this.i - 1), this.secondRoute.getStop(this.j));
                this.gain -= data.getTwoStopsDistance(this.firstRoute.getStop(this.i - 1), this.firstRoute.getStop(this.i));
            }
        }
        // Closing the hole the block leaves behind happens inside the second route, so those
        // legs are measured from the second route's depot.
        if (this.j > 0 || this.oneSequence)
            this.gain -= data.getTwoStopsDistance(this.secondRoute.getStop(this.j - 1), this.secondRoute.getStop(this.j));
        else
            this.gain -= data.getDepotToStopDistance(this.secondRoute.getDepot(), this.secondRoute.getStop(this.j));
        if (this.j + this.degree + 1 < this.border) {
            if (this.j > 0 || this.oneSequence)
                this.gain += data.getTwoStopsDistance(this.secondRoute.getStop(this.j - 1), this.secondRoute.getStop(this.j + this.degree + 1));
            else
                this.gain += data.getDepotToStopDistance(this.secondRoute.getDepot(), this.secondRoute.getStop(this.j + this.degree + 1));
            this.gain -= data.getTwoStopsDistance(this.secondRoute.getStop(this.j + this.degree), this.secondRoute.getStop(this.j + this.degree + 1));
        }
        else {
            if (this.j > 0 || this.oneSequence)
                this.gain += data.getStopToDepotDistance(this.secondRoute.getStop(this.j - 1), this.secondRoute.getDepot());
            this.gain -= data.getStopToDepotDistance(this.secondRoute.getStop(this.j + this.degree), this.secondRoute.getDepot());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void perform(InputData data) {
        if (this.oneSequence) {
            this.firstRoute.rightShift(this.i, this.j, this.degree, this.with2Opt);
            this.firstRoute.improve(this.gain);
        }
        else {
            int[] seq1 = new int[this.firstRoute.getLength() + this.degree + 1];
            for (int i = 0; i < this.i; i++) 
                seq1[i] = this.firstRoute.getStop(i);
            for (int i = 0; i <= this.degree; i++) 
                seq1[this.i + i] = this.secondRoute.getStop(this.with2Opt ? this.j + this.degree - i : this.j + i);
            for (int i = this.i; i < this.firstRoute.getLength(); i++) 
                seq1[i + this.degree + 1] = this.firstRoute.getStop(i);
            int[] seq2 = new int[this.secondRoute.getLength() - this.degree - 1];
            for (int i = 0; i < this.j; i++) 
                seq2[i] = this.secondRoute.getStop(i);
            for (int i = this.j + this.degree + 1; i < this.secondRoute.getLength(); i++) 
                seq2[i - this.degree - 1] = this.secondRoute.getStop(i);
            this.rebuild(data, seq1, seq2);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isFeasible(InputData data, Solution solution) {
        if (this.oneSequence)
            return true;
        int sumDemand = 0;
        for (int i = this.j; i <= this.j + this.degree; i++) 
            sumDemand += data.getDemand(this.secondRoute.getStop(i));
        int demand1 = this.firstRoute.getSumDemand() + sumDemand;
        int demand2 = this.secondRoute.getSumDemand() - sumDemand;
        if (demand1 > data.getCapacity() || demand2 > data.getCapacity())
            return false;
        // Only the first route's depot takes demand on; the second one frees some, and
        // frees all of it when the block is the whole route and the route goes away.
        return this.hasRoom(solution, this.firstRoute, demand1)
               && (this.border > this.degree + 1 || this.keepsDepotPaid(solution, this.secondRoute));
    }

    @Override
    public String toString() {
        if (this.degree == 0)
            return this.name + " (" + this.i + ";" + this.j + ")";
        else if (this.with2Opt)
            return this.name + " (" + this.i + ";" + this.j + ") " + -this.degree;
        return this.name + " (" + this.i + ";" + this.j + ") " + this.degree;
    }
}
