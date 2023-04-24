package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tolle Abstraktion
 *
 * @author jerome.doring
 *
 * @param <T> The database-type. Should be initiated <b>once<b>.
 */
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 1, time = 90)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1)
public abstract class BenchmarkBaseline<T extends AutoCloseable> extends DatabaseRecordsGenerator {
  private static final Logger LOG = LoggerFactory.getLogger(BenchmarkBaseline.class);
  private static final int BULK_SIZE = 500;

  protected T database;

  @Param({"50", "1000" , "5000", "10000", "50000", "100000", "250000", "500000", "1000000", "5000000", "10000000"})
  public int documentCount;

  /**
   * Sets up, purges and fills data for the test. Will be executed once before the benchmark. Can also be used to ensure indexes.
   *
   * @return The database-client. Whatever this might be
   */
  protected abstract T createDatabaseConnection();

  /**
   * Adds the batch of records to the database.
   *
   * @param records {@link List} of at max {@link BenchmarkBaseline#BULK_SIZE} elements to be inserted
   */
  protected abstract void insertDocuments(List<Lookup> records);

  /**
   * Removes all records from the database, indexes should obviously not be dropped.
   */
  protected abstract void truncate();

  @Setup(Level.Trial)
  public void setup() {
    this.database = createDatabaseConnection();

    truncate();
    fillDatabase();

    cleanupResources();
    LOG.info("Database created");
  }

  private void fillDatabase() {
    List<Lookup> records = new ArrayList<>(BULK_SIZE);
    for (int i = 0; i < documentCount; ++i) {
      Lookup lookup = generateRecord();
      records.add(lookup);

      if (records.size() == BULK_SIZE) {
        insertDocuments(records);
        records.clear();
      }

      if (i % Math.max(50, documentCount / 100) == 0) {
        LOG.info("{}/{} elements inserted in DB.", i, documentCount);
      }
    }
    if (!records.isEmpty()) {
      insertDocuments(records);
      records.clear();
    }
  }

  private void cleanupResources() {
    uniqueCheckIdsList = new ArrayList<>(uniqueCheckIds);
    uniqueCheckIds.clear();
    uniqueCalcReqIds.clear();
  }

  @TearDown(Level.Trial)
  public void teardown() throws Exception {
    database.close();
    // Maybe log stats of the databases (table/index)?
    LOG.info("Database closed");
  }

  public static class RandomCheckIdHolder {
    long randomCheckId;
  }

}
