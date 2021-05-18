package org.folio.circulation.support.http.server;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValidationErrorTest {

  @Test
  void oneParam() {
    var e = new ValidationError("my.code", "foo {a} bar", "a", "b");
    assertThat(e.getCode(), is("my.code"));
    assertThat(e.getMessage(), is("foo b bar"));
    assertThat(e.getParameter("a"), is("b"));
    assertThat(e.toJson(), is(
        new JsonObject()
        .put("code", "my.code")
        .put("message", "foo b bar")
        .put("parameters", new JsonArray().add(
            new JsonObject().put("key", "a").put("value", "b")))));
  }

  @Test
  void oneParamWithNullMessage() {
    var e = new ValidationError("my.code", null, "a", "b");
    assertThat(e.getCode(), is("my.code"));
    assertThat(e.getMessage(), is(nullValue()));
    assertThat(e.getParameter("a"), is("b"));
    assertThat(e.toJson().getString("code"), is("my.code"));
    assertThat(e.toJson().getMap(), not(hasKey("message")));
    assertThat(e.toJson(), is(
        new JsonObject()
        .put("code", "my.code")
        .put("parameters", new JsonArray().add(
            new JsonObject().put("key", "a").put("value", "b")))));
  }

  @Test
  void threeParams() {
    var e = new ValidationError("my.code", "foo {a}{b}{c}{a}{b}{c} bar",
        "a", "b", "b", "c", "c", "a");
    assertThat(e.getCode(), is("my.code"));
    assertThat(e.getMessage(), is("foo bcabca bar"));
    assertThat(e.getParameter("a"), is("b"));
    assertThat(e.getParameter("b"), is("c"));
    assertThat(e.getParameter("c"), is("a"));
    assertThat(e.toJson().getString("code"), is("my.code"));
    assertThat(e.toJson().getString("message"), is("foo bcabca bar"));
    var parameters = e.toJson().getJsonArray("parameters");
    assertThat(parameters.size(), is(3));
    var map = Map.of(
        parameters.getJsonObject(0).getString("key"),
        parameters.getJsonObject(0).getString("value"),
        parameters.getJsonObject(1).getString("key"),
        parameters.getJsonObject(1).getString("value"),
        parameters.getJsonObject(2).getString("key"),
        parameters.getJsonObject(2).getString("value"));
    assertThat(map, is(Map.of("a", "b", "b", "c", "c", "a")));
  }

  @Test
  void threeParametersWithNullMessage() {
    var e = new ValidationError("my.code", null, "a", "b", "b", "c", "c", "a");
    assertThat(e.getCode(), is("my.code"));
    assertThat(e.getMessage(), is(nullValue()));
    assertThat(e.getParameter("a"), is("b"));
    assertThat(e.getParameter("b"), is("c"));
    assertThat(e.getParameter("c"), is("a"));
    assertThat(e.toJson().getString("code"), is("my.code"));
    assertThat(e.toJson().getMap(), not(hasKey("message")));
    assertThat(e.toJson().getJsonArray("parameters").size(), is(3));
  }

}
