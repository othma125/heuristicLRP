package Algorithm;

// Author: Othmane

import Algorithm.Metaheuristics.GeneticAlgorithm;
import Algorithm.Solution.GiantTour;
import Algorithm.Data.InputData;
import java.io.IOException;



/**
 * Single-instance entry point: loads one LRPLIB instance, solves it with the
 * memetic {@link GeneticAlgorithm}, and prints the best solution and running
 * time.
 *
 * @author Othmane EL YAAKOUBI
 */
public class main {

    /**
     * @param args the command line arguments (unused)
     * @throws IOException if the instance file cannot be read
     */
    public static void main(String[] args) throws IOException {

        InputData data = new InputData("Algorithm/LRPLib/Instances_Prodhon_LRP/coord20-5-1.dat");
//        InputData data = new InputData("Algorithm/LRPLib/Instances_Tuzun_LRP/coordP111112.dat");
//        InputData data = new InputData("Algorithm/LRPLib/Instances_Barreto_LRP/coordGaspelle.dat");
        GeneticAlgorithm algorithm = new GeneticAlgorithm(data);
        algorithm.Run();

        if (algorithm.isFeasible()) {
            GiantTour gt = algorithm.getBestGiantTour();
            System.out.println(gt);
            //gt.export(data);
            System.out.println("\nEnd Time = " + algorithm.getRunningTime() + " ms");
        }
        else
            System.out.println("No feasible solution found\n");
    }
}
