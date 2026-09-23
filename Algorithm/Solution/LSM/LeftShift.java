// Author: Othmane

package Algorithm.Solution.LSM;

import Algorithm.Data.InputData;
import Algorithm.Solution.Route;
import Algorithm.Solution.Solution;

/**
 * Left-shift (or-opt) move: relocates a block of {@code degree + 1} stops
 * ending at position {@code i} of the first route to position {@code j} of the
 * second (or the same) route. The {@code with2Opt} flag reverses the relocated
 * block. The two routes may serve different depots, in which case the block is
 * handed over to the second route's depot.
 *
 * @author Othmane EL YAAKOUBI
 */
public class LeftShift extends LocalSearchMove {

    private final int firstBorder;
    private final int degree;
    private final boolean with2Opt;

    /**
     * @param data     the problem instance
     * @param with2opt whether the relocated block is reversed
     * @param degree   number of extra stops moved with the anchor (block size
     *                 is {@code degree + 1})
     * @param i        anchor position in the first route
     * @param j        insertion position in the second route
     * @param routes   one route (intra-route) or two routes (inter-route)
     */
    public LeftShift(InputData data, boolean with2opt, int degree, int i, int j, Route... routes) {
        super("LeftShift", i, j, routes);
        this.with2Opt = with2opt;
        this.degree = degree;
        this.firstBorder = this.firstRoute.getLength();
    }

    /** {@inheritDoc} */
    @Override
    public void setGain(InputData data) {
        // The relocated block joins the second route, so every leg it opens or closes on that
        // side is measured from the second route's depot.
        if (this.border == 0) {
            if (this.with2Opt) {
                this.gain += data.getDepotToStopDistance(this.secondRoute.getDepot(), this.firstRoute.getStop(this.i));
                this.gain += data.getStopToDepotDistance(this.firstRoute.getStop(this.i - this.degree), this.secondRoute.getDepot());
            }
            else {
                this.gain += data.getStopToDepotDistance(this.firstRoute.getStop(this.i), this.secondRoute.getDepot());
                this.gain += data.getDepotToStopDistance(this.secondRoute.getDepot(), this.firstRoute.getStop(this.i - this.degree));
            }
        }
        else if (this.with2Opt) {
            this.gain += data.getTwoStopsDistance(this.secondRoute.getStop(this.j), this.firstRoute.getStop(this.i));
            if (this.j + 1 < this.border) {
                this.gain += data.getTwoStopsDistance(this.firstRoute.getStop(this.i - this.degree), this.secondRoute.getStop(this.j + 1));
                this.gain -= data.getTwoStopsDistance(this.secondRoute.getStop(this.j), this.secondRoute.getStop(this.j + 1));
            }
            else {
                this.gain += data.getStopToDepotDistance(this.firstRoute.getStop(this.i - this.degree), this.secondRoute.getDepot());
                this.gain -= data.getStopToDepotDistance(this.secondRoute.getStop(this.j), this.secondRoute.getDepot());
            }
        }
        else {
            this.gain += data.getTwoStopsDistance(this.secondRoute.getStop(this.j), this.firstRoute.getStop(this.i - this.degree));
            if (this.j + 1 < this.border) {
                this.gain += data.getTwoStopsDistance(this.firstRoute.getStop(this.i), this.secondRoute.getStop(this.j + 1));
                this.gain -= data.getTwoStopsDistance(this.secondRoute.getStop(this.j), this.secondRoute.getStop(this.j + 1));
            }
            else {
                this.gain += data.getStopToDepotDistance(this.firstRoute.getStop(this.i), this.secondRoute.getDepot());
                this.gain -= data.getStopToDepotDistance(this.secondRoute.getStop(this.j), this.secondRoute.getDepot());
            }
        }
        if (this.i + 1 < this.firstBorder || this.oneSequence)
            this.gain -= data.getTwoStopsDistance(this.firstRoute.getStop(this.i), this.firstRoute.getStop(this.i + 1));
        else
            this.gain -= data.getStopToDepotDistance(this.firstRoute.getStop(this.i), this.firstRoute.getDepot());
        if (this.i - this.degree == 0) {
            if (this.i + 1 < this.firstBorder || this.oneSequence)
                this.gain += data.getDepotToStopDistance(this.firstRoute.getDepot(), this.firstRoute.getStop(this.i + 1));
            this.gain -= data.getDepotToStopDistance(this.firstRoute.getDepot(), this.firstRoute.getStop(this.i - this.degree));
        } 
        else {
            if (this.i + 1 < this.firstBorder || this.oneSequence)
                this.gain += data.getTwoStopsDistance(this.firstRoute.getStop(this.i - this.degree - 1), this.firstRoute.getStop(this.i + 1));
            else
                this.gain += data.getStopToDepotDistance(this.firstRoute.getStop(this.i - this.degree - 1), this.firstRoute.getDepot());
            this.gain -= data.getTwoStopsDistance(this.firstRoute.getStop(this.i - this.degree - 1), this.firstRoute.getStop(this.i - this.degree));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void perform(InputData data) {
        if (this.oneSequence) {
            this.firstRoute.leftShift(this.i, this.j, this.degree, this.with2Opt);
            this.firstRoute.improve(this.gain);
        }
        else {
            int[] seq1 = new int[this.firstRoute.getLength() - this.degree - 1];
            for (int i = 0; i < this.i - this.degree; i++) 
                seq1[i] = this.firstRoute.getStop(i);
            for (int i = this.i + 1; i < this.firstRoute.getLength(); i++) 
                seq1[i - this.degree - 1] = this.firstRoute.getStop(i);
            int[] seq2 = new int[this.secondRoute.getLength() + this.degree + 1];
            if (this.secondRoute.getLength() > 0) {
                for (int i = 0; i <= this.j; i++) 
                    seq2[i] = this.secondRoute.getStop(i);
                for (int i = this.j + 1; i < this.secondRoute.getLength(); i++) 
                    seq2[i + this.degree + 1] = this.secondRoute.getStop(i);
            }
            for (int i = 0; i <= this.degree; i++) 
                seq2[this.secondRoute.getLength() > 0 ? this.j + 1 + i : i] = this.firstRoute.getStop(this.with2Opt ? this.i - i : this.i - this.degree + i);
            this.rebuild(data, seq1, seq2);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isFeasible(InputData data, Solution solution) {
        if (this.oneSequence)
            return true;
        int sumDemand = 0;
        for (int i = this.i - this.degree; i <= this.i; i++) 
            sumDemand += data.getDemand(this.firstRoute.getStop(i));
        int demand1 = this.firstRoute.getSumDemand() - sumDemand;
        int demand2 = this.secondRoute.getSumDemand() + sumDemand;
        if (demand1 > data.getCapacity() || demand2 > data.getCapacity())
            return false;
        // Only the second route's depot takes demand on; the first one frees some, and
        // frees all of it when the block is the whole route and the route goes away.
        return this.hasRoom(solution, this.secondRoute, demand2)
               && (this.firstBorder > this.degree + 1 || this.keepsDepotPaid(solution, this.firstRoute));
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
