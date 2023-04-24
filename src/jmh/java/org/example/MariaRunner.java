package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

public class MariaRunner extends BenchmarkBaseline<Connection> {

  @State(Scope.Thread)
  public static class MariaReadState extends RandomCheckIdHolder {
    PreparedStatement statement;

    @Setup(Level.Invocation)
    @SneakyThrows
    public void setup(MariaRunner runner) {
      String query = """
        SELECT *
        FROM lookup l1
        JOIN lookup_identifier l2 on l1.id = l2.id
        WHERE l2.name = 'CHECK_ID' AND value = ?""";
      this.statement = runner.database.prepareStatement(query);
      this.randomCheckId = runner.getRandomCheckId();
      this.statement.setLong(1, randomCheckId);
    }

    @TearDown(Level.Invocation)
    @SneakyThrows
    public void release() {
      statement.close();
    }
  }

  @Benchmark
  @SneakyThrows
  public void benchmarkRead(MariaReadState state, Blackhole bl) {
    ResultSet rs = state.statement.executeQuery();
    rs.next();
    rs.getLong("value");
    if (rs.getLong("value") != state.randomCheckId) {
      throw new IllegalStateException("Record not found!");
    }
    bl.consume(rs);
    rs.close(); // Should that be part of the benchmark?
  }

  @Override
  @SneakyThrows
  protected Connection createDatabaseConnection() {
    Class.forName("org.mariadb.jdbc.Driver"); // for running via CLI
    Connection connection = DriverManager.getConnection("jdbc:mariadb://127.0.0.1:3306/test", "root", "root");
    try (Statement s = connection.createStatement();) {
      s.execute("SELECT 1");
    }
    return connection;
  }

  @Override
  protected void insertDocuments(List<Lookup> records) {
    saveLookupBatch(records);
    saveLookupIdentifiersBatch(records);
  }

  @SneakyThrows
  private void saveLookupBatch(List<Lookup> records) {
    try (PreparedStatement statement = database
      .prepareStatement("INSERT INTO lookup (id, archival_id, timestamp, created_at, archived_at) VALUES (?, ?, ?, ?, ?)")) {

      records.forEach(r -> insertLookups(r, statement));

      statement.executeBatch();
    }
  }

  @SneakyThrows
  private void saveLookupIdentifiersBatch(List<Lookup> records) {
    try (PreparedStatement statement = database
      .prepareStatement("INSERT INTO lookup_identifier (id, name, value) VALUES (?, ?, ?)")) {

      for (Lookup lookup : records) {
        lookup.identifiers
          .forEach((name, values) -> insertIdentifiers2(lookup, name, values, statement));
      }
      statement.executeBatch();
    }
  }

  @Override
  @SneakyThrows
  protected void truncate() {
    try (var statementOne =
      database.prepareStatement("TRUNCATE TABLE lookup")) {
      statementOne.execute();
    }
    try (var statementOne =
      database.prepareStatement("TRUNCATE TABLE lookup_identifier")) {
      statementOne.execute();
    }
  }

  @SneakyThrows
  private void insertLookups(Lookup lookup, PreparedStatement statement) {
    statement.setString(1, lookup.id);
    statement.setLong(2, lookup.archivalId);
    statement.setTimestamp(3, Timestamp.from(lookup.timestamp));
    statement.setTimestamp(4, Timestamp.from(lookup.createdAt));
    statement.setTimestamp(5, Optional.ofNullable(lookup.archivedAt).map(Timestamp::from).orElse(null));
    statement.addBatch();
  }

  @SneakyThrows
  private void insertIdentifiers2(Lookup lookup, String name, Object values, PreparedStatement statement) {
    if (values instanceof Long) {
      statement.setString(1, lookup.id);
      statement.setString(2, name);
      statement.setString(3, values.toString());
      statement.addBatch();
    } else if (values instanceof Collection<?> col) {
      for (Object o : col) {
        statement.setString(1, lookup.id);
        statement.setString(2, name);
        statement.setString(3, o.toString());
        statement.addBatch();
      }
    }
  }
}
