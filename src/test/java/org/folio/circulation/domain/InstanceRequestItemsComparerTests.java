package org.folio.circulation.domain;

import static org.folio.circulation.domain.InstanceRequestItemsComparer.sortRequestQueues;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class InstanceRequestItemsComparerTests {

  @Test
  void canSortRequestQueuesWhenFirstQueueIsLessThanSecondQueue() {
    Map<Item, Integer> itemQueueSizeMap = new HashMap<>();

    Item item1 = createItem(null);
    Item item2 = createItem(null);

    itemQueueSizeMap.put(item1, 3);
    itemQueueSizeMap.put(item2, 4);

    final Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, null, null);

    assertEquals(2, itemIntegerMap.size());
    assertEquals(3, itemIntegerMap.values().toArray()[0]);
  }

  @Test
  void canSortRequestQueuesWhenSecondQueueIsLessThanFirstQueue() {
    Map<Item, Integer> itemQueueSizeMap = new HashMap<>();

    Item item1 = createItem(null);
    Item item2 = createItem(null);

    itemQueueSizeMap.put(item1, 3);
    itemQueueSizeMap.put(item2, 2);

    final Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, null, null);

    assertEquals(2, itemIntegerMap.size());
    assertEquals(2, itemIntegerMap.values().toArray()[0]);
    assertEquals(item2,  itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  void canSortRequestQueuesWhenQueuesAreEqual() {
    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();

    Item item1 = createItem(null);
    Item item2 = createItem(null);

    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    final Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, null, null);

    assertEquals(2, itemIntegerMap.size());
    //expect order to stay the same when the queue sizes are equal and no further comparison
    assertEquals(2, itemIntegerMap.values().toArray()[0]);
    assertEquals(item1,  itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  void canSortRequestQueuesWhenQueuesAreEqualWithDueDates() {
    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();

    Item item1 = createItem(null);
    Item item2 = createItem(null);

    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    final Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, null, null);

    assertEquals(2, itemIntegerMap.size());
    assertEquals(2, itemIntegerMap.values().toArray()[0]);
    assertEquals(item1,  itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  void canSortRequestQueuesWhenItem1DueDateIsEarlierThanItem2DueDate() {
    Item item1 = createItem(null);
    Item item2 = createItem(null);

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, ZonedDateTime> itemDueDateMap = new LinkedHashMap<>();
    ZonedDateTime item1DueDate = getZonedDateTime();
    ZonedDateTime item2DueDate = item1DueDate.plusDays(3);
    itemDueDateMap.put(item1, item1DueDate);
    itemDueDateMap.put(item2, item2DueDate);

    final Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, null);

    assertEquals(2, itemIntegerMap.size());
    assertEquals(item1,  itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  void canSortRequestQueuesWhenItem2DueDateIsEarlierThanItem1DueDate() {
    Item item1 = createItem(null);
    Item item2 = createItem(null);

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, ZonedDateTime> itemDueDateMap = new LinkedHashMap<>();
    ZonedDateTime item1DueDate = ClockUtil.getZonedDateTime();
    ZonedDateTime item2DueDate = ClockUtil.getZonedDateTime().minusDays(3);
    itemDueDateMap.put(item1, item1DueDate);
    itemDueDateMap.put(item2, item2DueDate);

    final Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, null);

    assertEquals(2, itemIntegerMap.size());
    assertEquals(item2,  itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  void canSortRequestQueuesWhenEitherItemDueDateIsNull() {
    Item item1 = createItem(null);
    Item item2 = createItem(null);

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, ZonedDateTime> itemDueDateMap = new LinkedHashMap<>();
    itemDueDateMap.put(item1, ClockUtil.getZonedDateTime());
    itemDueDateMap.put(item2, null);

    Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, null);

    assertEquals(2, itemIntegerMap.size());
    assertEquals(item1,  itemIntegerMap.keySet().toArray()[0]);

    itemDueDateMap.clear();
    itemDueDateMap.put(item1, null);
    itemDueDateMap.put(item2, ClockUtil.getZonedDateTime());

    itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, null);

    assertEquals(2, itemIntegerMap.size());
    assertEquals(item2,  itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  void canSortRequestQueuesWhenBothItemsDueDateIsNull() {
    Item item1 = createItem(null);
    Item item2 = createItem(null);

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, ZonedDateTime> itemDueDateMap = new LinkedHashMap<>();
    itemDueDateMap.put(item2, null);  //specifically add item2 first and expect that item2 will be first item in the result map.
    itemDueDateMap.put(item1, null);

    Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, null);

    assertEquals(2, itemIntegerMap.size());
    //expect order to stay the same when the queue sizes and all items' due dates are null so no further comparison
    assertEquals(item1,  itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  void canGetItem1WhenSortRequestQueuesUsingServicePointId() {
    UUID destinationServicePointId = UUID.randomUUID();

    Item item1 = createItem(destinationServicePointId);
    Item item2 = createItem(null);

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, ZonedDateTime> itemDueDateMap = new LinkedHashMap<>();
    ZonedDateTime commonDueDate = ClockUtil.getZonedDateTime();
    itemDueDateMap.put(item2, commonDueDate);  //specifically add item2 first and expect that item2 will be first item in the result map.
    itemDueDateMap.put(item1, commonDueDate);

    Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, destinationServicePointId);

    assertEquals(2, itemIntegerMap.size());
    //expect Item1 to be chosen because its home location is served by the destinationServicePointId
    assertEquals(item1, itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  void canGetItem2WhenSortRequestQueuesUsingServicePointId() {
    UUID destinationServicePointId = UUID.randomUUID();

    Item item1 = createItem(null);
    Item item2 = createItem(destinationServicePointId);

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, ZonedDateTime> itemDueDateMap = new LinkedHashMap<>();
    ZonedDateTime commonDueDate = ClockUtil.getZonedDateTime();
    itemDueDateMap.put(item2, commonDueDate);  //specifically add item2 first and expect that item2 will be first item in the result map.
    itemDueDateMap.put(item1, commonDueDate);

    Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, destinationServicePointId);

    assertEquals(2, itemIntegerMap.size());
    //expect Item1 to be chosen because its home location is served by the destinationServicePointId
    assertEquals(item2, itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  void canGetItem1WhenSortRequestQueuesUsingServicePointIdAndBothItemsAreNotServedByDestinationServicePointId() {
    Item item1 = createItem(UUID.randomUUID());
    Item item2 = createItem(UUID.randomUUID());

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, ZonedDateTime> itemDueDateMap = new LinkedHashMap<>();
    ZonedDateTime commonDueDate = ClockUtil.getZonedDateTime();
    itemDueDateMap.put(item2, commonDueDate);  //specifically add item2 first and expect that item2 will be first item in the result map.
    itemDueDateMap.put(item1, commonDueDate);

    Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, UUID.randomUUID());

    assertEquals(2, itemIntegerMap.size());
    //expect Item1 to be chosen because its home location is served by the destinationServicePointId
    assertEquals(item1, itemIntegerMap.keySet().toArray()[0]);
  }

  private static Item createItem(UUID withServicePointId) {
    JsonObject itemRepresentation = new JsonObject();
    itemRepresentation.put("id", UUID.randomUUID().toString());

    Location location = Location.unknown(null);

    if (withServicePointId != null) {
      location = new Location(null, null, null, null,
        List.of(withServicePointId), null, false,
        Institution.unknown(null), Campus.unknown(null),
        Library.unknown(null), ServicePoint.unknown(null));
    }

    return Item.from(itemRepresentation).withLocation(location);
  }
}
