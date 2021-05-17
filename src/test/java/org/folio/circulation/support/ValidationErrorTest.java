package org.folio.circulation.support;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

class ValidationErrorTest {

  @Test
  void singleValidationErrorOneParam() {
    var f = ValidationErrorFailure.singleValidationError("my.code", "foo {a} bar", "a", "b");
    assertThat(f.getErrors().size(), is(1));
    var e = f.getErrors().iterator().next();
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
  void singleValidationErrorThreeParams() {
    var f = ValidationErrorFailure.singleValidationError("my.code", "foo {a}{b}{c}{a}{b}{c} bar",
        "a", "b", "b", "c", "c", "a");
    assertThat(f.getErrors().size(), is(1));
    var e = f.getErrors().iterator().next();
    assertThat(e.getCode(), is("my.code"));
    assertThat(e.getMessage(), is("foo bcabca bar"));
    assertThat(e.getParameter("a"), is("b"));
    assertThat(e.getParameter("b"), is("c"));
    assertThat(e.getParameter("c"), is("a"));
    assertThat(e.toJson().getString("code"), is("my.code"));
    assertThat(e.toJson().getString("message"), is("foo bcabca bar"));
    assertThat(e.toJson().getJsonArray("parameters").size(), is(3));
  }

}
