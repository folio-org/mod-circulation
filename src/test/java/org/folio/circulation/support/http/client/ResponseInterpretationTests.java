package org.folio.circulation.support.http.client;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;
import static org.folio.circulation.support.Result.of;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.junit.Test;

import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.json.JsonObject;

public class ResponseInterpretationTests {
  @Test
  public void shouldApplyOnlyMapperWhenMatchesStatusCode() {
    final JsonObject body = new JsonObject()
      .put("foo", "hello")
      .put("bar", "world");

    Result<JsonObject> result = new ResponseInterpreter<JsonObject>()
      .flatMapOn(200, response -> of(response::getJson))
      .apply(new Response(200, body.toString(), APPLICATION_JSON.toString()));

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(body));
  }

  @Test
  public void shouldApplyCorrectMapperWhenMultipleDefined() {
    Result<String> result = new ResponseInterpreter<String>()
      .flatMapOn(200, response -> of(() -> "incorrect"))
      .flatMapOn(400, response -> of(() -> "correct"))
      .apply(new Response(400, "", TEXT_PLAIN.toString()));

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is("correct"));
  }

  @Test
  public void shouldProvideDiagnosticFailureOnUnexpectedResponse() {
    Result<JsonObject> result = new ResponseInterpreter<JsonObject>()
      .flatMapOn(200, response -> of(response::getJson))
      .apply(serverError());

    assertThat(result.succeeded(), is(false));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));

    final ServerErrorFailure cause = (ServerErrorFailure) result.cause();

    assertThat(cause.getReason(), is(
      "HTTP request to \"http://failing.com\" failed, status code: 500, response: \"Something went wrong\""));
  }

  @Test
  public void shouldCaptureErrorWhenMappingFailsAtRuntime() {
    final JsonObject body = new JsonObject()
      .put("foo", "hello")
      .put("bar", "world");

    Result<JsonObject> result = new ResponseInterpreter<JsonObject>()
      .flatMapOn(200, response -> { throw new RuntimeException("Not good"); })
      .apply(new Response(200, body.toString(), ""));

    assertThat(result.succeeded(), is(false));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));

    final ServerErrorFailure cause = (ServerErrorFailure) result.cause();

    assertThat(cause.getReason(), containsString("Not good"));
    assertThat(cause.getReason(), containsString("RuntimeException"));
  }

  private Response serverError() {
    return new Response(500, "Something went wrong", TEXT_PLAIN.toString(),
      new CaseInsensitiveHeaders(), "http://failing.com");
  }
}
