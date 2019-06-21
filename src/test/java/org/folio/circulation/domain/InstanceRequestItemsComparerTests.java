package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.junit.Test;

import java.util.*;

import static org.folio.circulation.domain.InstanceRequestItemsComparer.sortRequestQueues;
import static org.junit.Assert.assertEquals;

public class InstanceRequestItemsComparerTests {

  @Test
  public void canSortRequestQueuesWhenFirstQueueIsLessThanSecondQueue() {
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
  public void canSortRequestQueuesWhenSecondQueueIsLessThanFirstQueue() {
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
  public void canSortRequestQueuesWhenQueuesAreEqual() {
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
  public void canSortRequestQueuesWhenQueuesAreEqualWithDueDates() {
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
  public void canSortRequestQueuesWhenItem1DueDateIsEarlierThanItem2DueDate() {
    Item item1 = createItem(null);
    Item item2 = createItem(null);

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, DateTime> itemDueDateMap = new LinkedHashMap<>();
    DateTime item1DueDate = DateTime.now();
    DateTime item2DueDate = DateTime.now().plusDays(3);
    itemDueDateMap.put(item1, item1DueDate);
    itemDueDateMap.put(item2, item2DueDate);

    final Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, null);

    assertEquals(2, itemIntegerMap.size());
    assertEquals(item1,  itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  public void canSortRequestQueuesWhenItem2DueDateIsEarlierThanItem1DueDate() {
    Item item1 = createItem(null);
    Item item2 = createItem(null);

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, DateTime> itemDueDateMap = new LinkedHashMap<>();
    DateTime item1DueDate = DateTime.now();
    DateTime item2DueDate = DateTime.now().minusDays(3);
    itemDueDateMap.put(item1, item1DueDate);
    itemDueDateMap.put(item2, item2DueDate);

    final Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, null);

    assertEquals(2, itemIntegerMap.size());
    assertEquals(item2,  itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  public void canSortRequestQueuesWhenEitherItemDueDateIsNull() {
    Item item1 = createItem(null);
    Item item2 = createItem(null);

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, DateTime> itemDueDateMap = new LinkedHashMap<>();
    itemDueDateMap.put(item1, DateTime.now());
    itemDueDateMap.put(item2, null);

    Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, null);

    assertEquals(2, itemIntegerMap.size());
    assertEquals(item1,  itemIntegerMap.keySet().toArray()[0]);

    itemDueDateMap.clear();
    itemDueDateMap.put(item1, null);
    itemDueDateMap.put(item2, DateTime.now());

    itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, null);

    assertEquals(2, itemIntegerMap.size());
    assertEquals(item2,  itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  public void canSortRequestQueuesWhenBothItemsDueDateIsNull() {
    Item item1 = createItem(null);
    Item item2 = createItem(null);

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, DateTime> itemDueDateMap = new LinkedHashMap<>();
    itemDueDateMap.put(item2, null);  //specifically add item2 first and expect that item2 will be first item in the result map.
    itemDueDateMap.put(item1, null);

    Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, null);

    assertEquals(2, itemIntegerMap.size());
    //expect order to stay the same when the queue sizes and all items' due dates are null so no further comparison
    assertEquals(item1,  itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  public void canGetItem1WhenSortRequestQueuesUsingServicePointId() {
    UUID destinationServicePointId = UUID.randomUUID();

    Item item1 = createItem(destinationServicePointId);
    Item item2 = createItem(null);

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, DateTime> itemDueDateMap = new LinkedHashMap<>();
    DateTime commonDueDate = DateTime.now();
    itemDueDateMap.put(item2, commonDueDate);  //specifically add item2 first and expect that item2 will be first item in the result map.
    itemDueDateMap.put(item1, commonDueDate);

    Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, destinationServicePointId);

    assertEquals(2, itemIntegerMap.size());
    //expect Item1 to be chosen because its home location is served by the destinationServicePointId
    assertEquals(item1, itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  public void canGetItem2WhenSortRequestQueuesUsingServicePointId() {
    UUID destinationServicePointId = UUID.randomUUID();

    Item item1 = createItem(null);
    Item item2 = createItem(destinationServicePointId);

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, DateTime> itemDueDateMap = new LinkedHashMap<>();
    DateTime commonDueDate = DateTime.now();
    itemDueDateMap.put(item2, commonDueDate);  //specifically add item2 first and expect that item2 will be first item in the result map.
    itemDueDateMap.put(item1, commonDueDate);

    Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, destinationServicePointId);

    assertEquals(2, itemIntegerMap.size());
    //expect Item1 to be chosen because its home location is served by the destinationServicePointId
    assertEquals(item2, itemIntegerMap.keySet().toArray()[0]);
  }

  @Test
  public void canGetItem1WhenSortRequestQueuesUsingServicePointIdAndBothItemsAreNotServedByDestinationServicePointId() {
    Item item1 = createItem(UUID.randomUUID());
    Item item2 = createItem(UUID.randomUUID());

    Map<Item, Integer> itemQueueSizeMap = new LinkedHashMap<>();
    itemQueueSizeMap.put(item1, 2);
    itemQueueSizeMap.put(item2, 2);

    Map<Item, DateTime> itemDueDateMap = new LinkedHashMap<>();
    DateTime commonDueDate = DateTime.now();
    itemDueDateMap.put(item2, commonDueDate);  //specifically add item2 first and expect that item2 will be first item in the result map.
    itemDueDateMap.put(item1, commonDueDate);

    Map<Item, Integer> itemIntegerMap = sortRequestQueues(itemQueueSizeMap, itemDueDateMap, UUID.randomUUID());

    assertEquals(2, itemIntegerMap.size());
    //expect Item1 to be chosen because its home location is served by the destinationServicePointId
    assertEquals(item1, itemIntegerMap.keySet().toArray()[0]);
  }

  private static Item createItem(UUID withServicePointId) {
    JsonObject itemRepresentation = new JsonObject();
    itemRepresentation.put("itemid", UUID.randomUUID().toString());

    Location location = null;

    if (withServicePointId != null) {
      JsonObject homeLocation = new JsonObject();
      JsonArray servicePointsArray = new JsonArray();

      servicePointsArray.add(withServicePointId.toString());
      homeLocation.put("servicePointIds", servicePointsArray);

      location = new Location(homeLocation, null, null, null);
    }
    
    return new Item(itemRepresentation, null, null, location, null, null, null);
  }
}
