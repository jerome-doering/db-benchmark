package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
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
@Warmup(iterations = 1, time = 25)
@Measurement(iterations = 1, time = 60)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1)
public abstract class BenchmarkBaseline<T extends AutoCloseable> extends DatabaseRecordsGenerator {
  private static final Logger LOG = LoggerFactory.getLogger(BenchmarkBaseline.class);
  private static final int BULK_SIZE = 1000;
  private static final int THREADS_TO_FILL_DB = 4;
  private final AtomicInteger numberOfInserts = new AtomicInteger(0);
  BlockingQueue<List<Lookup>> unboundedQueue = new ArrayBlockingQueue<>(THREADS_TO_FILL_DB, true);

  protected T database;

  @Param({"50", "1000", "5000", "10000", "50000", "100000", "250000", "500000", "1000000", "5000000", "10000000"})
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

  /**
   * Rebuilds all indexes because with more execution cycles the and {@link #truncate()} the index can become bloated.
   */
  protected abstract void rebuildIndex();

  @Setup(Level.Trial)
  public void setup() {
    this.database = createDatabaseConnection();

    truncate();
    rebuildIndex(); // for faster inserts
    fillDatabase();
    LOG.info("Rebuilding indexes ...");
    rebuildIndex(); // for faster queries
    LOG.info("{}/{} elements inserted. Database created", numberOfInserts.get(), documentCount);

    cleanupResources();
  }

  @SneakyThrows
  private void fillDatabase() {
    CompletableFuture<Void> producerThread = CompletableFuture.runAsync(this::generateLookups);
    Stream<CompletableFuture<Void>> consumerThreads = IntStream.range(0, THREADS_TO_FILL_DB)
      .boxed()
      .map(cnt -> CompletableFuture.runAsync(this::pullAndInsert));

    @SuppressWarnings("unchecked")
    CompletableFuture<Void>[] threads = Stream.concat(Stream.of(producerThread), consumerThreads)
      .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(threads).join();
  }

  @SneakyThrows
  private void pullAndInsert() {
    LOG.info("Consumer thread for db inserts started.....");
    while (numberOfInserts.get() < documentCount - 1) {
      List<Lookup> batch = unboundedQueue.poll(1, TimeUnit.SECONDS);
      if (batch == null) {
        LOG.info("Waiting {}/{} .....", numberOfInserts.get(), documentCount);
        continue;
      }
      insertDocuments(batch);
      int nrBeforeUpdate = numberOfInserts.getAndAdd(batch.size());
      int progressNumber = Math.max(50, documentCount / 100);

      // Could me made smarter
      for (int i = nrBeforeUpdate; i < nrBeforeUpdate + batch.size(); i++) {
        if (i > 0 && i % progressNumber == 0) {
          LOG.info("{}/{} elements inserted in DB.", i, documentCount);
        }
      }
    }
    LOG.info("Consumer thread for db inserts finished.....");
  }

  @SneakyThrows
  private void generateLookups() {
    LOG.info("Producer thread for lookups started.....");
    List<Lookup> records = new ArrayList<>(BULK_SIZE);
    for (int i = 1; i <= documentCount; ++i) {
      records.add(generateRecord());

      if (i % BULK_SIZE == 0) {
        unboundedQueue.put(records);
        records = new ArrayList<>(BULK_SIZE);
      }
    }
    if (!records.isEmpty()) {
      unboundedQueue.put(records);
    }
    LOG.info("Producer thread for lookups finished.....");
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
    cleanupResources();
    uniqueCheckIdsList.clear();
  }

  public static class RandomCheckIdHolder {
    long randomCheckId;
  }

}
