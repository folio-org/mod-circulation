package org.folio.circulation.domain;

import org.joda.time.DateTime;

import java.util.*;
import java.util.stream.Collectors;

public class InstanceRequestItemsComparer {

  private InstanceRequestItemsComparer() {}

  public static Map<Item, Integer> sortRequestQueues(Map<Item, Integer> itemsQueueLengthUnsortedMap,
                                                     Map<Item, DateTime> itemDueDateMap,
                                                     UUID destinationServicePointId) {
    return itemsQueueLengthUnsortedMap
      .entrySet()
      .stream()
      .sorted(compareQueueLengths(itemDueDateMap, destinationServicePointId))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
        (oldValue, newValue) -> oldValue, (LinkedHashMap::new)));
  }

  private static Comparator<Map.Entry<Item, Integer>> compareQueueLengths(Map<Item, DateTime> itemDueDateMap,
                                                                          UUID destinationServicePointId) {
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
      return result;
    };
  }

  private static int compareQueueSize(int queueSize1, int queueSize2){
    return queueSize1 - queueSize2;
  }

  private static int compareDueDate(Item item1, Item item2, Map<Item, DateTime> itemDueDateMap) {
    DateTime q1ItemDueDate = itemDueDateMap.get(item1);
    DateTime q2ItemDueDate = itemDueDateMap.get(item2);

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
    if (destinationServicePointId == null)
      return 0;

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
