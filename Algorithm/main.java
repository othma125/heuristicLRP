// Author: Othmane

//import Algorithm.Metaheuristics.GeneticAlgorithm;
//import Algorithm.Solution.GiantTour;
import Algorithm.Data.InputData;
import java.io.IOException;



/**
 * Single-instance entry point. While the LRP solver is being ported, it only
 * loads one LRPLIB instance and prints what the reader parsed; the memetic
 * {@code GeneticAlgorithm} run is commented out below.
 *
 * @author Othmane EL YAAKOUBI
 */
public class main {

    /**
     * @param args optional path to the instance file
     * @throws IOException if the instance file cannot be read
     */
    public static void main(String[] args) throws IOException {

        String file = args.length > 0 ? args[0]
                : "Algorithm/LRPLib/Instances_Prodhon_LRP/coord20-5-1.dat";
        InputData data = new InputData(file);

        System.out.println(data);
        for (int depot = 0; depot < data.getDepotNumber(); depot++)
            System.out.println("depot " + depot
                    + " capacity = " + data.getDepotCapacity(depot)
                    + ", opening cost = " + data.getDepotCost(depot)
                    + ", distance to customer 0 = " + data.getDepotToStopDistance(depot, 0));
        int totalDemand = 0;
        for (int stop = 0; stop < data.getCustomerNumber(); stop++)
            totalDemand += data.getDemand(stop);
        System.out.println("total demand = " + totalDemand
                + ", distance customer 0 -> 1 = " + data.getTwoStopsDistance(0, 1));

//        GeneticAlgorithm algorithm = new GeneticAlgorithm(data);
//        algorithm.Run();
//
//        if (algorithm.isFeasible()) {
//            GiantTour gt = algorithm.getBestGiantTour();
//            System.out.println(gt);
//            System.out.println("\nEnd Time = " + algorithm.getRunningTime() + " ms");
//        }
//        else
//            System.out.println("No feasible solution found\n");
    }
}
