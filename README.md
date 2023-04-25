# Intro

- Java benchmarks are hard
- This suite uses heavily the Java Microbenchmark Harness (JMH) framework
- Benchmarks are structures the following way:
  1. Random documents are inserted in the database of the benchmark. The number is pre-defined (see further down).
  2. Random reads by `checkId` are continuously performed (maybe fetching by user-id is more interesting). Only the pure query and fetching of data will be benchmarked. Everything else will not be part of the benchmark (it is encapsulated as `State`). The first executions are not part of the benchmark (they are part of the `Warmup`). The duration of the benchmark itself is configured at `BenchmarkBaseline` via the `@Measurement` annotation.
  3. After completion the next number of database-records is taken and started over again

- Goal should be to see a degradation of the query times with increasing DB size since the results will contain average times by database-record-number.
- It currently runs benchmarks for mongo, postgres and mariadb
- **All benchmark executions are single threaded!**

# Accuracy
Especially with smaller databases there is the risk that the same record is being fetched multiple times due to the randomness of the records to be fetched. This could lead to a utilising the DB which then affects the times. However, having 1 minute of testing, I hope that this evens out.

# Usage
- `./gradlew jmh` to run the suite
- in the `build.gradle` there is the `jmh.includes` in which you can toggle to run only certain suites
- the number of iteration and the size of the DB (number of records before the benchmark) can be configured via the `BenchmarkBaseline#documentCount`s annotation. For each of those values the DB will be cleaned and a new set of random values will be inserted.
- Since my PC is just a bit faster than my old Casio calculator I set those values rather low

# The script
I've added a shell script `run-suite.sh`. This first generates an executable jar, then starts the docker-container and benchmarks one-by-one. Meaning for the mongo-benchmarks only the mongo-container should run. The results are being stored in the `./build/` dir. The `.txt` files are easier readable, the `.csv` files can be used for gnuplot later on.

Goal is to set the database-record counts correctly and let the script run overnight.\
Comment out the `docker-compose down` in the script leaves the data-bases running making it possible to get the stats from the biggest DB-configurations.

# Results
As of writing this I got the following results

## Mongo
| Benchmark                  | (documentCount) | Mode | Cnt | Score  | Error | Units |
|----------------------------|----------------:|------|-----|--------|-------|-------|
| MongoRunner.benchmarkRead  |              50 | avgt |     | 0.225  |       | ms/op |
| MongoRunner.benchmarkRead  |            1000 | avgt |     | 0.148  |       | ms/op |
| MongoRunner.benchmarkRead  |            5000 | avgt |     | 0.145  |       | ms/op |
| MongoRunner.benchmarkRead  |           10000 | avgt |     | 0.145  |       | ms/op |
| MongoRunner.benchmarkRead  |           50000 | avgt |     | 0.147  |       | ms/op |
| MongoRunner.benchmarkRead  |          100000 | avgt |     | 0.148  |       | ms/op |
| MongoRunner.benchmarkRead  |          250000 | avgt |     | 0.149  |       | ms/op |
| MongoRunner.benchmarkRead  |          500000 | avgt |     | 0.144  |       | ms/op |
| MongoRunner.benchmarkRead  |         1000000 | avgt |     | 0.149  |       | ms/op |

DB size:
- Lookup
  - Count: 1\_000\_000
  - Size: 82MB
  - Index-size:
    - id: 67MB
    - lookup_values: 76MB
    - archivalId_1: 13MB
  - total-size: 239MB
- Query:
```
// Normal count does not work and other count methods have been added starting mongo 4.2
db.lookup.aggregate( [
   { $count: "myCount" }
]);
db.lookup.stats();
```

## Mariadb
Query times are crap, could someone please check if my query is correct? See `MariaRunner.MariaReadState#setup()`
| Benchmark                   | (documentCount) | Mode | Cnt |    Score |  Error | Units
|-----------------------------|----------------:|------|-----|----------|--------|------
| MariaRunner.benchmarkRead   |              50 | avgt |     |    0.176 |        | ms/op
| MariaRunner.benchmarkRead   |            1000 | avgt |     |    1.319 |        | ms/op
| MariaRunner.benchmarkRead   |            5000 | avgt |     |    5.621 |        | ms/op
| MariaRunner.benchmarkRead   |           10000 | avgt |     |   11.171 |        | ms/op
| MariaRunner.benchmarkRead   |           50000 | avgt |     |   54.530 |        | ms/op
| MariaRunner.benchmarkRead   |          100000 | avgt |     |  109.005 |        | ms/op
| MariaRunner.benchmarkRead   |          250000 | avgt |     |  737.265 |        | ms/op
| MariaRunner.benchmarkRead   |          500000 | avgt |     | 1474.179 |        | ms/op
| MariaRunner.benchmarkRead   |         1000000 | avgt |     | 2909.673 |        | ms/op

DB size:
- Lookup
  - Count: 981\_775 - `count(*)` returns 1e6
  - Size: 188MB
  - Index-size: 74MB
- Lookup_identifiers
  - Count: 434\_028\_544
  - Size: 434MB
  - Index-size: 176MB
- total size: 833MB
- Query:
```
ANALYZE TABLE lookup;
ANALYZE TABLE lookup_identifier ;

show table status from test;
```

## Postgres
| Benchmark                    | (documentCount) | Mode | Cnt |     Score |  Error | Units
|------------------------------|----------------:|------|-----|-----------|--------|------
| PostgresRunner.benchmarkRead |              50 | avgt |     |   0.060   |        | ms/op
| PostgresRunner.benchmarkRead |            1000 | avgt |     |   0.288   |        | ms/op
| PostgresRunner.benchmarkRead |            5000 | avgt |     |   1.206   |        | ms/op
| PostgresRunner.benchmarkRead |           10000 | avgt |     |   2.258   |        | ms/op
| PostgresRunner.benchmarkRead |           50000 | avgt |     |  11.149   |        | ms/op
| PostgresRunner.benchmarkRead |          100000 | avgt |     |  22.303   |        | ms/op
| PostgresRunner.benchmarkRead |          250000 | avgt |     |  34.321   |        | ms/op
| PostgresRunner.benchmarkRead |          500000 | avgt |     |  65.888   |        | ms/op
| PostgresRunner.benchmarkRead |         1000000 | avgt |     | 142.569   |        | ms/op

DB size:
- Lookup
  - Count: 1\_000\_0
  - Size: 214MB
  - Index-size: 215MB
- Query:
```
select pg_size_pretty(pg_relation_size('public.lookup')) as DB, pg_size_pretty(pg_indexes_size('public.lookup')) as idx;
```

# Improvements
- [x] Batch inserts
- [x] Rebuild indexes before execution
- [x] Multi-threaded inserts
- [ ] Update mongo to version 4.2
- [ ] Get the stats (fs-size and index-size) on every tear-down
- [ ] Change read operation to read by user or profile-ID instead of check-ID 
- [ ] Maybe add TiDB?