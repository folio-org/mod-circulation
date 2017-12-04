package org.folio.circulation;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.fakes.FakeCQLToJSONInterpreter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class FakeCQLToJSONInterpreterTests {
  @Test
  public void canFilterBySingleProperty() {

    FakeCQLToJSONInterpreter interpreter = new FakeCQLToJSONInterpreter();

    Collection<JsonObject> records = new ArrayList<>();

    records.add(new JsonObject().put("myProperty", "foo"));
    records.add(new JsonObject().put("myProperty", "bar"));

    List<JsonObject> matchedRecords =
      interpreter.filterByQuery(records, "myProperty=foo");

    assertThat(matchedRecords.size(), is(1));
  }

  @Test
  public void canFilterByMultipleProperties() {
    FakeCQLToJSONInterpreter interpreter = new FakeCQLToJSONInterpreter();

    Collection<JsonObject> records = new ArrayList<>();

    records.add(new JsonObject()
      .put("myProperty", "foo")
      .put("otherProperty", "nope"));

    records.add(new JsonObject()
      .put("myProperty", "bar")
      .put("otherProperty", "match"));

    JsonObject shouldMatch = new JsonObject()
      .put("myProperty", "foo")
      .put("otherProperty", "match");

    records.add(shouldMatch);

    List<JsonObject> matchedRecords =
      interpreter.filterByQuery(records, "myProperty=foo and otherProperty=match");

    assertThat(matchedRecords.size(), is(1));
  }

  @Test
  public void canFilterByMultipleValues() {
    FakeCQLToJSONInterpreter interpreter = new FakeCQLToJSONInterpreter();

    Collection<JsonObject> records = new ArrayList<>();

    records.add(new JsonObject()
      .put("myProperty", "baz"));

    JsonObject shouldMatch = new JsonObject()
      .put("myProperty", "bar");

    records.add(shouldMatch);

    JsonObject shouldAlsoMatch = new JsonObject()
      .put("myProperty", "foo");

    records.add(shouldAlsoMatch);

    List<JsonObject> matchedRecords =
      interpreter.filterByQuery(records, "myProperty=(foo or bar)");

    assertThat(matchedRecords.size(), is(2));
  }
}
