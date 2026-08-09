# heuristicLRP

Location Routing Problem (LRP) solver, ported from a CVRP genetic algorithm that
solves a giant tour with a route-first/cluster-second split.

Each route is served from a `Depot`, the split builds one candidate route per
candidate depot, and a `Solution` groups its routes by depot.

**State:** the reader, the split, the local search and the memetic algorithm all
run on LRP instances. The objective is still the travelled distance alone: depot
opening costs and the route opening cost are parsed and exposed, yet not added to
the solution cost, so results are not comparable to the published best known
values. Depot capacities are not enforced either.

## Build

```bash
./compile.sh            # compiles the .java files changed since the last build
./compile.sh --clean    # full rebuild
```

Classes land in `out/`.

## Run

```bash
java -cp out main                      # solves the instance hardcoded in Algorithm/main.java
java -cp out benchmark                 # solves a whole benchmark directory
java -cp out Algorithm.Data.InputData        # reader self-check
java -cp out Algorithm.Solution.SplitCheck   # split self-check
java -cp out Algorithm.Solution.LSM.GainCheck # local search self-check
```

Change the instance (or the benchmark directory) by editing the path at the top
of `Algorithm/main.java` or `Algorithm/benchmark.java`. `benchmark` writes
`results <dir>.csv` with one row per instance and the gap to the best known cost
read from `bks.csv`; instances missing from that file report `NA`.

## Web app

```bash
./run-server.sh          # compiles, starts the server, opens the browser
./run-server.sh 9090     # on another port
bash kill-server.sh      # stops a server left running
```

The port comes from the CLI argument, else `PORT` in `.env`, else 8081. The page
lists the LRPLib folders and instances, streams the solver log live over
Server-Sent Events, and draws the solution: one colour per route, each drawn
from the depot serving it, with used depots filled and unused ones hollow.
`Stop` ends the run early and still returns the best solution found.

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
