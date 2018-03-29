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
  public void canSortBySinglePropertyImpliedOrder() {
    canSortBySingleParameter("rank", rankedRecords(), "rank", "2", "3", "4", "7", "9");
  }

  @Test
  public void canSortBySinglePropertyAscending() {
    canSortBySingleParameter("rank/sort.ascending", rankedRecords(), "rank", "2", "3", "4", "7", "9");
  }

  @Test
  public void canSortBySinglePropertyDescending() {
    canSortBySingleParameter("rank/sort.descending", rankedRecords(), "rank", "9", "7", "4", "3", "2");
  }

  @Test
  public void canSortBySingleDatePropertyAscending() {
    canSortBySingleParameter("date/sort.ascending", datedRecords(), "date",
      "2017-11-24T12:31:27.000+0000",
      "2018-01-12T12:31:27.000+0000",
      "2018-02-04T15:10:54.000+0000",
      "2018-02-14T08:06:54.000+0000",
      "2018-02-14T15:10:54.000+0000");
  }

  private void canSortBySingleParameter(
    String sortSpecification,
    Collection<JsonObject> records,
    String property,
    String... expectedSort) {

    FakeCQLToJSONInterpreter interpreter = new FakeCQLToJSONInterpreter(true);

    List<JsonObject> sortedRecords =
      interpreter.execute(records, "id==12345 sortBy " + sortSpecification);

    assertThat(sortedRecords.size(), is(5));

    List<Object> sortedValues = sortedRecords.stream()
      .map(record -> record.getString(property))
      .collect(Collectors.toList());

    assertThat(sortedValues, contains(expectedSort));
  }

  private Collection<JsonObject> rankedRecords() {
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
}
