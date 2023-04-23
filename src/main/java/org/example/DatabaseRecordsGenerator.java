package org.example;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class DatabaseRecordsGenerator {

  protected static final ThreadLocalRandom randomizer = ThreadLocalRandom.current();

  protected final TreeSet<Long> uniqueCalcReqIds = new TreeSet<>();
  protected final TreeSet<Long> uniqueCheckIds = new TreeSet<>();
  // Faster access for benchmark but might let the heap explode
  // Get a random element from that should be O(1)
  protected ArrayList<Long> uniqueCheckIdsList;

  protected final AtomicLong archivalId = new AtomicLong(1L);

  protected Lookup generateRecord() {
    return new Lookup(
      String.format("common_rules_executor_-=-_%d_-=-_CALCULATION_REQUEST", uniqueCrId()),
      archivalId.getAndIncrement(),
      randomizer.nextInt(0, 5) >= 2 ? Instant.now() : null,
      Instant.now().minusSeconds(randomizer.nextLong(100000L, 999999L)),
      Instant.now().minusSeconds(randomizer.nextLong(100000L, 999999L)),
      randomIdentifiers());
  }

  protected Long getRandomCheckId() {
    return uniqueCheckIdsList.get(randomizer.nextInt(uniqueCheckIdsList.size()));
  }

  private Map<String, Object> randomIdentifiers() {
    final var map = new HashMap<String, Object>();
    var profileIds = Set.of(
      randomizer.nextInt(1, 100000),
      randomizer.nextInt(100001, 200000),
      randomizer.nextInt(200001, 300000),
      randomizer.nextInt(300001, 400000),
      randomizer.nextInt(400001, 500000));
    var userIds = Set.of(
      randomizer.nextInt(1, 100000),
      randomizer.nextInt(100001, 200000),
      randomizer.nextInt(200001, 300000),
      randomizer.nextInt(300001, 400000),
      randomizer.nextInt(400001, 500000));

    map.put("CHECK_ID", uniqueCheckId());
    if (randomizer.nextInt(0, 5) >= 2) {
      map.put("PROFILE_ID", profileIds.stream().limit(randomizer.nextLong(1, profileIds.size())).toList());
    }
    if (randomizer.nextInt(0, 5) >= 2) {
      map.put("USER_ID", userIds.stream().limit(randomizer.nextLong(1, userIds.size())).toList());
    }

    return map;
  }

  private Long uniqueCrId() {
    return computeUniqueId(uniqueCalcReqIds);
  }

  private Long uniqueCheckId() {
    return computeUniqueId(uniqueCheckIds);
  }

  private Long computeUniqueId(TreeSet<Long> set) {
    Long uniqueId;
    do {
      uniqueId = randomizer.nextLong(10_000_000, 99_999_999);
    } while (!set.add(uniqueId));

    return uniqueId;
  }
}
