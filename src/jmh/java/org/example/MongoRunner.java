package org.example;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import java.util.List;
import org.bson.Document;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.LoggerFactory;

public class MongoRunner extends BenchmarkBaseline<MongoClient> {

  @State(Scope.Thread)
  public static class MongoLookupReadState {
    Document query;
    MongoCollection<Document> mongoCollection;

    @Setup(Level.Invocation)
    public void setup(MongoRunner runner) {
      this.mongoCollection = runner.getCollection();
      this.query = new Document();
      long randomId = runner.getRandomCheckId();
      this.query.put("identifiers.CHECK_ID", randomId);
    }
  }

  @Benchmark
  public void benchmarkRead(MongoLookupReadState state, Blackhole bl) {
    bl.consume(state.mongoCollection.find(state.query).cursor().next());
  }

  @Override
  protected MongoClient createDatabaseConnection() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger rootLogger = loggerContext.getLogger("org.mongodb.driver");
    rootLogger.setLevel(ch.qos.logback.classic.Level.WARN);
    MongoClient mongo = MongoClients.create(
      "mongodb://benchmark:benchmark@localhost:27017/?keepAlive=true&poolSize=30&autoReconnect=true&socketTimeoutMS=360000&connectTimeoutMS=360000");
    mongo.listDatabases();

    return mongo;
  }

  @Override
  protected void truncate() {
    getCollection().deleteMany(new Document());
  }

  @Override
  protected void insertDocuments(List<Lookup> records) {
    getCollection().insertMany(records.stream().map(Lookup::toMongoDocument).toList());
  }

  private MongoCollection<Document> getCollection() {
    return database.getDatabase("benchmark").getCollection("lookup");
  }

}
