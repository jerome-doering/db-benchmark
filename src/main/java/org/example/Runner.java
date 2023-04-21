package org.example;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;

public class Runner {
  private static final int POLL_INTERVAL = 30;
  private static final int DATASET_SIZE = 10_000_000;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private Connection postgres;
  private Connection mariadb;
  private MongoClient mongo;
  private final ThreadLocalRandom randomizer = ThreadLocalRandom.current();
  private final Map<String, Long> perSecond = new HashMap<>();
  private final TreeSet<Long> uniqueIds = new TreeSet<>();

  public Runner() {
    try {
      this.postgres = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:15432/benchmark", "benchmark", "benchmark");
      System.out.println("Postgres Ready");
    } catch (Throwable e) {
      System.err.println("Unable to connect to postgres " + e.getMessage());
    }

    try {
      this.mariadb = DriverManager.getConnection("jdbc:mariadb://127.0.0.1:3306/test", "root", "root");
      mariadb.createStatement().execute("SELECT 1");
      System.out.println("MariaDB Ready");
    } catch (Throwable e) {
      System.err.println("Unable to connect to mariadb " + e.getMessage());
    }

    try {
      this.mongo = MongoClients.create("mongodb://benchmark:benchmark@localhost:27017/?keepAlive=true&poolSize=30&autoReconnect=true&socketTimeoutMS=360000&connectTimeoutMS=360000");
      mongo.listDatabases();
      System.out.println("Mongo Ready");
    } catch (Throwable e) {
      System.err.println("Unable to connect to mongo " + e.getMessage());
    }
  }

  public void run() throws Exception {
    final var mongoCollection = mongo.getDatabase("benchmark").getCollection("lookup");
    final var schedule = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::reportMetrics, POLL_INTERVAL, POLL_INTERVAL, SECONDS);
    final var mongoThreads = Executors.newFixedThreadPool(10);
    final var mariaThreads = Executors.newFixedThreadPool(10);
    final var pgsqlThreads = Executors.newFixedThreadPool(10);

    try {
      var amount = DATASET_SIZE;
      var archivalId = 1L;
      while (amount-- > 0) {
        final var lookup = new Lookup(
            String.format("common_rules_executor_-=-_%d_-=-_CALCULATION_REQUEST", uniqueId()),
            archivalId++,
            randomizer.nextInt(0, 5) >= 2 ? Instant.now() : null,
            Instant.now().minusSeconds(randomizer.nextLong(100000L, 999999L)),
            Instant.now().minusSeconds(randomizer.nextLong(100000L, 999999L)),
            randomIdentifiers()
        );

        mongoThreads.execute(() -> saveInMongo(lookup, mongoCollection));
        mariaThreads.execute(() -> saveInMariaDb(lookup));
        pgsqlThreads.execute(() -> saveInPgsql(lookup));
      }

      System.out.println("Threads full");

      while (!mongoThreads.awaitTermination(10, MINUTES) || !mariaThreads.awaitTermination(10, MINUTES) || !pgsqlThreads.awaitTermination(10, MINUTES));
    } finally {
      System.out.println("Finished");
      mongoThreads.close();
      pgsqlThreads.close();
      mariaThreads.close();
      schedule.cancel(true);
    }
  }

  private void reportMetrics() {
    long postgresCount;
    long mariaCount;
    long mongoCount;

    try (var statement = mariadb.createStatement()) {
      var result = statement.executeQuery("SELECT COUNT(id) from lookup");
      result.next();
      mariaCount = result.getLong(1);
    } catch (SQLException e) {
      System.err.println(e.getMessage());
      throw new RuntimeException(e);
    }

    try (var statement = postgres.createStatement()) {
      var result = statement.executeQuery("SELECT COUNT(id) from lookup");
      result.next();
      postgresCount = result.getLong(1);
    }catch (SQLException e) {
      System.err.println(e.getMessage());
      throw new RuntimeException(e);
    }

    mongoCount = mongo.getDatabase("benchmark").getCollection("lookup").countDocuments();

    System.out.printf(
        "[%s] Mongo [%d/s], Postgres [%d/s], Maria [%d/s]%n",
        LocalDateTime.now(),
        mongoCount == DATASET_SIZE ? 0 : Math.round((float) (mongoCount - perSecond.computeIfAbsent("mongo", a -> 0L)) / POLL_INTERVAL),
        postgresCount == DATASET_SIZE ? 0 : Math.round((float) (postgresCount - perSecond.computeIfAbsent("postgres", a -> 0L)) / POLL_INTERVAL),
        Math.round((float) (mariaCount - perSecond.computeIfAbsent("maria", a -> 0L)) / POLL_INTERVAL)
    );

    perSecond.putAll(Map.of("mongo", mongoCount, "postgres", postgresCount, "maria", mariaCount));
  }

  private void saveInMariaDb(Lookup lookup) {
    try {
      try (var statementOne = mariadb.prepareStatement("INSERT INTO lookup (id, archival_id, timestamp, created_at, archived_at) VALUES (?, ?, ?, ?, ?)")) {
        statementOne.setString(1, lookup.id);
        statementOne.setLong(2, lookup.archivalId);
        statementOne.setTimestamp(3, Timestamp.from(lookup.timestamp));
        statementOne.setTimestamp(4, Timestamp.from(lookup.createdAt));
        statementOne.setTimestamp(5, Optional.ofNullable(lookup.archivedAt).map(Timestamp::from).orElse(null));
        statementOne.execute();
      }

      lookup.identifiers.forEach((name, values) -> {
        try {
          StringBuilder query = new StringBuilder();
          query.append("INSERT INTO lookup_identifier (id, name, value) VALUES ");
          final List<String> groups = new ArrayList<>();
          final List<Object> params = new LinkedList<>();

          if (values instanceof Long) {
            groups.add("(?, ?, ?)");
            params.add(lookup.id);
            params.add(name);
            params.add(values.toString());
          } else if (values instanceof Collection<?>) {
            ((Collection<?>) values).forEach(l -> {
              groups.add("(?, ?, ?)");
              params.add(lookup.id);
              params.add(name);
              params.add(l.toString());
            });
          }
          query.append(String.join(",", groups));

          try (var statementTwo = mariadb.prepareStatement(query.toString())) {
            final AtomicInteger index = new AtomicInteger(1);
            params.forEach(param -> {
              try {
                statementTwo.setObject(index.getAndIncrement(), param);
              } catch (SQLException e) {
                throw new RuntimeException(e);
              }
            });

            statementTwo.execute();
          }
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      });

    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void saveInPgsql(Lookup lookup) {
    try (var statement = postgres.prepareStatement("INSERT INTO lookup (id, archival_id, timestamp, created_at, archived_at, identifiers) VALUES (?, ?, ?, ?, ?, ?::json)")) {
      statement.setString(1, lookup.id);
      statement.setLong(2, lookup.archivalId);
      statement.setTimestamp(3, Timestamp.from(lookup.timestamp));
      statement.setTimestamp(4, Timestamp.from(lookup.createdAt));
      statement.setTimestamp(5, Optional.ofNullable(lookup.archivedAt).map(Timestamp::from).orElse(null));
      statement.setString(6, objectMapper.writeValueAsString(lookup.identifiers));

      statement.execute();
    } catch (SQLException | JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private void saveInMongo(Lookup lookup, MongoCollection<Document> mongoCol) {
    mongoCol.insertOne(lookup.toMongoDocument());
  }

  private Long uniqueId() {
    Long uniqueId;
    do {
      uniqueId = randomizer.nextLong(10_000_000, 99_999_999);
    } while (!uniqueIds.add(uniqueId));

    return uniqueId;
  }

  private Map<String, Object> randomIdentifiers() {
    final var map = new HashMap<String, Object>();
    var profileIds = Set.of(
        randomizer.nextInt(1, 100000),
        randomizer.nextInt(100001, 200000),
        randomizer.nextInt(200001, 300000),
        randomizer.nextInt(300001, 400000),
        randomizer.nextInt(400001, 500000)
    );
    var userIds = Set.of(
        randomizer.nextInt(1, 100000),
        randomizer.nextInt(100001, 200000),
        randomizer.nextInt(200001, 300000),
        randomizer.nextInt(300001, 400000),
        randomizer.nextInt(400001, 500000)
    );

    map.put("CHECK_ID", randomizer.nextLong(100000, 999999));
    if (randomizer.nextInt(0, 5) >= 2) {
      map.put("PROFILE_ID", profileIds.stream().limit(randomizer.nextLong(1, profileIds.size())).toList());
    }
    if (randomizer.nextInt(0, 5) >= 2) {
      map.put("USER_ID", userIds.stream().limit(randomizer.nextLong(1, userIds.size())).toList());
    }

    return map;
  }

  static class Lookup {
    public final String id;
    public final Long archivalId;
    public final Instant archivedAt;
    public final Instant createdAt;
    public final Instant timestamp;
    public final Map<String, Object> identifiers;

    public Lookup(String id, Long archivalId, Instant archivedAt, Instant createdAt, Instant timestamp, Map<String, Object> identifiers) {
      this.id = id;
      this.archivalId = archivalId;
      this.createdAt = createdAt;
      this.timestamp = timestamp;
      this.identifiers = identifiers;
      this.archivedAt = archivedAt;
    }

    public Document toMongoDocument() {
      var document = new Document();
      document.put("_id", id);
      document.put("archivalId", archivalId);
      document.put("archivedAt", archivedAt);
      document.put("createdAt", createdAt);
      document.put("timestamp", timestamp);
      document.put("identifiers", identifiers);

      return document;
    }
  }
}
