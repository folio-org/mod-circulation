package org.folio.circulation.domain;

import static org.folio.circulation.support.utils.LogUtil.mapAsString;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InstanceRequestItemsComparer {

  private InstanceRequestItemsComparer() {}

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static Map<Item, Integer> sortRequestQueues(Map<Item, Integer> itemsQueueLengthUnsortedMap,
    Map<Item, ZonedDateTime> itemDueDateMap, UUID destinationServicePointId) {

    log.debug("sortRequestQueues:: parameters itemsQueueLengthUnsortedMap: {}, " +
      "itemDueDateMap: {}, destinationServicePointId: {}", () -> mapAsString(
        itemsQueueLengthUnsortedMap), () -> mapAsString(itemDueDateMap),
      () -> destinationServicePointId);

    return itemsQueueLengthUnsortedMap
      .entrySet()
      .stream()
      .sorted(compareQueueLengths(itemDueDateMap, destinationServicePointId))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
        (oldValue, newValue) -> oldValue, (LinkedHashMap::new)));
  }

  private static Comparator<Map.Entry<Item, Integer>> compareQueueLengths(
    Map<Item, ZonedDateTime> itemDueDateMap, UUID destinationServicePointId) {

    log.debug("compareQueueLengths:: parameters itemDueDateMap: {}, destinationServicePointId: {}",
      () -> mapAsString(itemDueDateMap), () -> destinationServicePointId);
    // Sort the map
    return (q1Size, q2Size) -> {
      int result = compareQueueSize(q1Size.getValue(), q2Size.getValue());

      if (result == 0 && itemDueDateMap != null) {
        Item q1Item = q1Size.getKey();
        Item q2Item = q2Size.getKey();

        result = compareDueDate(q1Item, q2Item, itemDueDateMap);
        if (result == 0) {
          result =  compareItemServicePoint(q1Item, q2Item, destinationServicePointId);
        }
      }
      log.info("compareQueueLengths:: result: {}", result);
      return result;
    };
  }

  private static int compareQueueSize(int queueSize1, int queueSize2){
    log.debug("compareQueueSize:: parameters queueSize1: {}, queueSize2: {}",
      queueSize1, queueSize2);
    return queueSize1 - queueSize2;
  }

  private static int compareDueDate(Item item1, Item item2, Map<Item, ZonedDateTime> itemDueDateMap) {
    log.debug("compareDueDate:: parameters item1: {}, item2: {}, itemDueDateMap: {}",
      () -> item1, () -> item2, () -> mapAsString(itemDueDateMap));
    ZonedDateTime q1ItemDueDate = itemDueDateMap.get(item1);
    ZonedDateTime q2ItemDueDate = itemDueDateMap.get(item2);

    if (q1ItemDueDate == null && q2ItemDueDate == null) {
      return 0;
    } else if (q2ItemDueDate == null) {
      return -1;
    } else if (q1ItemDueDate == null) {
      return 1;
    } else {
      return q1ItemDueDate.compareTo(q2ItemDueDate);
    }
  }

  private static int compareItemServicePoint(Item item1, Item item2,
    UUID destinationServicePointId) {

    log.debug("compareItemServicePoint:: parameters item1: {}, item2: {}, " +
        "destinationServicePointId: {}", item1, item2, destinationServicePointId);
    if (destinationServicePointId == null) {
      return 0;
    }

    Location item1Location = item1.getLocation();
    Location item2Location = item2.getLocation();

    if (item1Location != null && item1Location.homeLocationIsServedBy(destinationServicePointId)){
      return -1;
    }
    else if (item2Location != null && item2Location.homeLocationIsServedBy(destinationServicePointId)) {
      return 1;
    }
    return 0;
  }
}
