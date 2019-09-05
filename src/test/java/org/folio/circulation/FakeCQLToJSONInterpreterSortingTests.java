package org.folio.circulation;

import io.vertx.core.json.JsonObject;
import api.support.fakes.FakeCQLToJSONInterpreter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class FakeCQLToJSONInterpreterSortingTests {

  @Test
  public void canSortBySingleStringPropertyImpliedOrder() {
    canSort("rank", stringRankedRecords(),
      "rank", "2", "3", "4", "7", "9");
  }

  @Test
  public void canSortBySingleStringPropertyAscending() {
    canSort("rank/sort.ascending", stringRankedRecords(),
      "rank", "2", "3", "4", "7", "9");
  }

  @Test
  public void canSortBySingleStringPropertyDescending() {
    canSort("rank/sort.descending", stringRankedRecords(),
      "rank", "9", "7", "4", "3", "2");
  }

  @Test
  public void canSortBySingleIntegerPropertyImpliedOrder() {
    canSort("rank", integerRankedRecords(),
      "rank", 2, 3, 4, 7, 9);
  }

  @Test
  public void canSortBySingleIntegerPropertyAscending() {
    canSort("rank/sort.ascending", integerRankedRecords(),
      "rank", 2, 3, 4, 7, 9);
  }

  @Test
  public void canSortBySingleIntegerPropertyDescending() {
    canSort("rank/sort.descending", integerRankedRecords(),
      "rank", 9, 7, 4, 3, 2);
  }

  @Test
  public void canSortBySingleDatePropertyAscending() {
    canSort("date/sort.ascending", datedRecords(), "date",
      "2017-11-24T12:31:27.000+0000",
      "2018-01-12T12:31:27.000+0000",
      "2018-02-04T15:10:54.000+0000",
      "2018-02-14T08:06:54.000+0000",
      "2018-02-14T15:10:54.000+0000");
  }

  @Test
  public void canSortByMultipleProperties() {
    canSort("firstRank/sort.ascending secondRank/sort.descending",
      multiplePropertiesRecords(), "secondRank",
      "k", "a", "t", "c", "d");
  }

  private void canSort(
    String sortSpecification,
    Collection<JsonObject> records,
    String property,
    Object... expectedSort) {

    FakeCQLToJSONInterpreter interpreter = new FakeCQLToJSONInterpreter(true);

    List<JsonObject> sortedRecords =
      interpreter.execute(records, "id==12345 sortBy " + sortSpecification);

    assertThat(sortedRecords.size(), is(5));

    List<Object> sortedValues = sortedRecords.stream()
      .map(record -> record.getValue(property))
      .collect(Collectors.toList());

    assertThat(sortedValues, contains(expectedSort));
  }

  private Collection<JsonObject> integerRankedRecords() {
    Collection<JsonObject> records = new ArrayList<>();

    records.add(new JsonObject()
      .put("id", "12345")
      .put("rank", 9));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("rank", 4));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("rank", 3));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("rank", 7));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("rank", 2));

    records.add(new JsonObject()
      .put("id", "65450")
      .put("rank", 1));

    return records;
  }

  private Collection<JsonObject> stringRankedRecords() {
    Collection<JsonObject> records = new ArrayList<>();

    records.add(new JsonObject()
      .put("id", "12345")
      .put("rank", "9"));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("rank", "4"));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("rank", "3"));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("rank", "7"));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("rank", "2"));

    records.add(new JsonObject()
      .put("id", "65450")
      .put("rank", "1"));

    return records;
  }

  private Collection<JsonObject> datedRecords() {
    Collection<JsonObject> records = new ArrayList<>();

    records.add(new JsonObject()
      .put("id", "12345")
      .put("date", "2018-02-04T15:10:54.000+0000"));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("date", "2018-01-12T12:31:27.000+0000"));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("date", "2017-11-24T12:31:27.000+0000"));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("date", "2018-02-14T15:10:54.000+0000"));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("date", "2018-02-14T08:06:54.000+0000"));

    records.add(new JsonObject()
      .put("id", "65450")
      .put("date", "2018-02-22T08:15:04.000+0000"));

    return records;
  }

  private Collection<JsonObject> multiplePropertiesRecords() {
    Collection<JsonObject> records = new ArrayList<>();

    records.add(new JsonObject()
      .put("id", "12345")
      .put("firstRank", 2)
      .put("secondRank", "a"));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("firstRank", 4)
      .put("secondRank", "c"));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("firstRank", 4)
      .put("secondRank", "t"));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("firstRank", 7)
      .put("secondRank", "d"));

    records.add(new JsonObject()
      .put("id", "12345")
      .put("firstRank", 2)
      .put("secondRank", "k"));

    records.add(new JsonObject()
      .put("id", "65450")
      .put("firstRank", 1)
      .put("secondRank", "m"));

    return records;
  }
}
