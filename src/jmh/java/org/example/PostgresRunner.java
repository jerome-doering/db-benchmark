package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
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

public class PostgresRunner extends BenchmarkBaseline<Connection> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @State(Scope.Thread)
  public static class MariaReadState {
    PreparedStatement statement;

    @Setup(Level.Invocation)
    @SneakyThrows
    public void setup(PostgresRunner runner) {
      String query = """
        SELECT *
        FROM lookup
        WHERE identifiers->>'CHECK_ID' = ?""";
      this.statement = runner.database.prepareStatement(query);
      long randomId = runner.getRandomCheckId();
      this.statement.setString(1, String.valueOf(randomId));
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
    if (!rs.next() || rs.getString("id") == null) {
      throw new IllegalStateException("Record not found!");
    }
    bl.consume(rs);
    rs.close(); // Should that be part of the benchmark?
  }

  @Override
  @SneakyThrows
  protected Connection createDatabaseConnection() {
    Connection connection = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:15432/benchmark", "benchmark", "benchmark");
    try (Statement s = connection.createStatement();) {
      s.execute("SELECT 1");
    }
    return connection;
  }

  @Override
  @SneakyThrows
  protected void insertDocuments(List<Lookup> records) {
    try (PreparedStatement statement = database
      .prepareStatement("INSERT INTO lookup (id, archival_id, timestamp, created_at, archived_at, identifiers) VALUES (?, ?, ?, ?, ?, ?::json)")) {

      records.forEach(r -> saveInPostgres(r, statement));

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
  }

  @SneakyThrows
  private void saveInPostgres(Lookup lookup, PreparedStatement statement) {
    statement.setString(1, lookup.id);
    statement.setLong(2, lookup.archivalId);
    statement.setTimestamp(3, Timestamp.from(lookup.timestamp));
    statement.setTimestamp(4, Timestamp.from(lookup.createdAt));
    statement.setTimestamp(5, Optional.ofNullable(lookup.archivedAt).map(Timestamp::from).orElse(null));
    statement.setString(6, objectMapper.writeValueAsString(lookup.identifiers));
    statement.addBatch();
  }
}
