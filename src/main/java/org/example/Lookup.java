package org.example;

import java.time.Instant;
import java.util.Map;
import org.bson.Document;

public class Lookup {

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
