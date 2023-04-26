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
Especially with smaller databases there is the risk that the same record is being fetched multiple times due to the randomness of the records to be fetched. This could lead to a utilising the DB-cache which then affects the times. However, having 1 minute of testing, I hope that this evens out.\
Note1: Inserting (setup) is done in a multi-threaded way but the actual benchmark is not. \
Note2: The docker images are the default images (no setting/connection tuning)

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

### Times

| Benchmark                  | (documentCount) | Mode | Cnt | Score  | Error | Units |
|----------------------------|----------------:|------|-----|-------:|-------|-------|
| MongoRunner.benchmarkRead  |              50 | avgt |     | 0.225  |       | ms/op |
| MongoRunner.benchmarkRead  |           1,000 | avgt |     | 0.148  |       | ms/op |
| MongoRunner.benchmarkRead  |           5,000 | avgt |     | 0.145  |       | ms/op |
| MongoRunner.benchmarkRead  |          10,000 | avgt |     | 0.145  |       | ms/op |
| MongoRunner.benchmarkRead  |          50,000 | avgt |     | 0.147  |       | ms/op |
| MongoRunner.benchmarkRead  |         100,000 | avgt |     | 0.148  |       | ms/op |
| MongoRunner.benchmarkRead  |         250,000 | avgt |     | 0.149  |       | ms/op |
| MongoRunner.benchmarkRead  |         500,000 | avgt |     | 0.144  |       | ms/op |
| MongoRunner.benchmarkRead  |       1,000,000 | avgt |     | 0.149  |       | ms/op |
| MongoRunner.benchmarkRead  |       5,000,000 | avgt |     | 0.150  |       | ms/op |
| MongoRunner.benchmarkRead  |      10,000,000 | avgt |     | 0.188  |       | ms/op |

### Stats

Lookup collection
- Count: 10,000,000
- Storage: 821MB
- Index:
  - id: 1,118GB
  - lookup_values: 399MB
  - archivalId_1: 130MB
- Total: 2.233GB

Query:
```
// Normal count does not work and other count methods have been added starting mongo 4.2
db.lookup.aggregate( [
   { $count: "myCount" }
]);
db.lookup.stats();
```

## Mariadb
Query times are crap, could someone please check if my query is correct? See `MariaRunner.MariaReadState#setup()`. According to `EXPLAIN` the indexes are being used.

### Times
| Benchmark                   | (documentCount) | Mode | Cnt |     Score |  Error | Units
|-----------------------------|----------------:|------|-----|----------:|--------|------
| MariaRunner.benchmarkRead   |              50 | avgt |     |     0.213 |        | ms/op
| MariaRunner.benchmarkRead   |           1,000 | avgt |     |     1.556 |        | ms/op
| MariaRunner.benchmarkRead   |           5,000 | avgt |     |     6.992 |        | ms/op
| MariaRunner.benchmarkRead   |          10,000 | avgt |     |    13.713 |        | ms/op
| MariaRunner.benchmarkRead   |          50,000 | avgt |     |    64.100 |        | ms/op
| MariaRunner.benchmarkRead   |         100,000 | avgt |     |   130.139 |        | ms/op
| MariaRunner.benchmarkRead   |         250,000 | avgt |     |   747.654 |        | ms/op
| MariaRunner.benchmarkRead   |         500,000 | avgt |     |  1910.946 |        | ms/op
| MariaRunner.benchmarkRead   |       1,000,000 | avgt |     |  4155.642 |        | ms/op
| MariaRunner.benchmarkRead   |       5,000,000 | avgt |     | 26395.945 |        | ms/op
| MariaRunner.benchmarkRead   |      10,000,000 | avgt |     | 46966.719 |        | ms/op



### Stats
- Lookup
  - Count: 9,930,236 - `count(*)` returns 1e7
  - Storage: 1.331GB
  - Index: 841MB
- Lookup_identifiers
  - Count: 39,720,605 - `count(*)` returns 39,990,665
  - Storage: 4.949GB
  - Index: 1.303GB
- Total: 8.034GB

Query:
```
ANALYZE TABLE lookup;
ANALYZE TABLE lookup_identifier ;

show table status from test;
```

## Postgres
### Times

| Benchmark                    | (documentCount) | Mode | Cnt |     Score |  Error | Units
|------------------------------|----------------:|------|-----|----------:|--------|------
| PostgresRunner.benchmarkRead |              50 | avgt |     |   0.112   |        | ms/op
| PostgresRunner.benchmarkRead |           1,000 | avgt |     |   0.080   |        | ms/op
| PostgresRunner.benchmarkRead |           5,000 | avgt |     |   0.199   |        | ms/op
| PostgresRunner.benchmarkRead |          10,000 | avgt |     |   0.238   |        | ms/op
| PostgresRunner.benchmarkRead |          50,000 | avgt |     |   0.270   |        | ms/op
| PostgresRunner.benchmarkRead |         100,000 | avgt |     |   0.258   |        | ms/op
| PostgresRunner.benchmarkRead |         250,000 | avgt |     |   0.272   |        | ms/op
| PostgresRunner.benchmarkRead |         500,000 | avgt |     |   0.286   |        | ms/op
| PostgresRunner.benchmarkRead |       1,000,000 | avgt |     |   0.266   |        | ms/op
| PostgresRunner.benchmarkRead |       5,000,000 | avgt |     |   0.297   |        | ms/op
| PostgresRunner.benchmarkRead |      10,000,000 | avgt |     |   0.256   |        | ms/op

### Stats
Lookup
- Count: 10,000,000
- Storage: 2.136GB
- Index: 1.685GB
- Total: 3.821GB

Query:
```
select pg_size_pretty(pg_relation_size('public.lookup')) as DB, pg_size_pretty(pg_indexes_size('public.lookup')) as idx;
```
# Mongo improvements
The way we store the PK/_id seems inefficient. Since this is done throughout the whole benchmark it affects all the suites in the same way. A small analysis for alternatives:

## Big String

```javascript
_id = "common_rules_executor_-=-_1233433_-=-_CALCULATION_REQUEST"
```

  key             | value
  ----------------| --------:
  storage         | 821MB
  index           | 1.118GB

## Short string

```javascript
_id = "cre=14323=CR"
```

  key             | value
  ----------------| -------:
  storage         | 748MB
  index           | 332MB

## Composite key

```javascript
_id = {
    "i": 1324324,
    "c": "cre",
    "t": "cr"
}
```

  key             | value
  ----------------| -------:
  storage         | 748MB
  index           | 639MB

# Tods
- [x] Batch inserts
- [x] Rebuild indexes before execution
- [x] Multi-threaded inserts
- [ ] Remove indexes, fill the DB, then create index to make the setup faster
- [ ] Update mongo to version 4.2
- [ ] Get the stats (fs-size and index-size) on every tear-down
- [ ] Change read operation to read by user or profile-ID instead of check-ID 
- [ ] Maybe add TiDB?