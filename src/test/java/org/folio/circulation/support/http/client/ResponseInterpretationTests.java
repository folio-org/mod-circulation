package org.folio.circulation.support.http.client;

import static java.util.function.Function.identity;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.Result.of;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
      .flatMapOn(200, mapUsingJson(identity()))
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
  public void canMapToKnownResult() {
    Result<String> result = new ResponseInterpreter<String>()
      .on(200, of(() -> "correct"))
      .apply(new Response(200, "", TEXT_PLAIN.toString()));

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
  public void shouldUseProvidedUnexpectedResponseHandler() {
    Result<String> result = new ResponseInterpreter<String>()
      .flatMapOn(200, response -> of(() -> "ok"))
      .otherwise(response -> of(() -> "unexpected response"))
      .apply(serverError());

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is("unexpected response"));
  }

  @Test
  public void shouldRetainMappingsWhenProvidingUnexpectedResponseHandler() {
    Result<String> result = new ResponseInterpreter<String>()
      .flatMapOn(200, response -> of(response::getBody))
      .otherwise(response -> of(() -> "unexpected response"))
      .apply(new Response(200, "ok", TEXT_PLAIN.toString()));

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is("ok"));
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

  @Test
  public void nullResponseShouldFail() {
    Result<String> result = new ResponseInterpreter<String>()
      .flatMapOn(200, response -> of(() -> "ok"))
      .apply(null);

    assertThat(result.succeeded(), is(false));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));

    final ServerErrorFailure cause = (ServerErrorFailure) result.cause();

    assertThat(cause.getReason(), is("Cannot interpret null response"));
  }

  private Response serverError() {
    return new Response(500, "Something went wrong", TEXT_PLAIN.toString(),
      new CaseInsensitiveHeaders(), "http://failing.com");
  }
}
