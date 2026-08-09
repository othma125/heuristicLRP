# heuristicLRP

Location Routing Problem (LRP) solver, ported from a CVRP genetic algorithm that
solves a giant tour with a route-first/cluster-second split.

**State:** only the instance reader is in place. The metaheuristic
(`Algorithm.Metaheuristics.GeneticAlgorithm`) and the solution classes are not
ported yet, so the calls to them in `Algorithm/main.java` are commented out.

## Build

```bash
./compile.sh            # compiles the .java files changed since the last build
./compile.sh --clean    # full rebuild
```

Classes land in `out/`.

## Run

```bash
java -cp out main                                                   # default instance
java -cp out main Algorithm/LRPLib/Instances_Tuzun_LRP/coordP111112.dat
java -cp out Algorithm.Data.InputData                               # reader self-check
```

`main` prints what the reader parsed: sizes, depot capacities and opening costs,
total demand, and a few distances.

## Instances

`Algorithm/LRPLib/` holds the three standard benchmarks — Prodhon (30), Tuzun
(36) and Barreto (14) — plus `files format.txt` describing the `.dat` layout:
customer count, depot count, depot coordinates, customer coordinates, vehicle
capacity, depot capacities, demands, depot opening costs, route opening cost,
and a flag for real (1) or integer (0) costs. Integer instances scale Euclidean
distances by 100 and truncate them.

`bks.csv` holds the best known solutions (`dataset,instance,bks,optimal,lb`)
extracted from the benchmark's published result page. It is not tracked,
`.gitignore` excludes `*.csv`.

`Instances_Barreto_LRP/coordOr117.dat` is rejected by the reader: its depot block
has four columns instead of two, and it has no best known solution published.
