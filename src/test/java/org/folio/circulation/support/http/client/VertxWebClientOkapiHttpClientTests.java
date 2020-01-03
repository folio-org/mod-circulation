package org.folio.circulation.support.http.client;

import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.circulation.support.http.client.VertxWebClientOkapiHttpClient.createClientUsing;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.VertxAssistant;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class VertxWebClientOkapiHttpClientTests {
  private static VertxAssistant vertxAssistant;

  @Rule
  public WireMockRule fakeWebServer = new WireMockRule(8081);
  private final URL okapiUrl = new URL("http://okapi.com");
  private final String tenantId = "test-tenant";
  private final String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6ImFhMjZjYjg4LTc2YjEtNTQ1OS1hMjM1LWZjYTRmZDI3MGMyMyIsImlhdCI6MTU3NjAxMzY3MiwidGVuYW50IjoiZGlrdSJ9.oGCb0gDIdkXGlCiECvJHgQMXD3QKKW2vTh7PPCrpds8";
  private final String userId = "aa26cb88-76b1-5459-a235-fca4fd270c23";
  private final String requestId = "test-request-id";

  public VertxWebClientOkapiHttpClientTests() throws MalformedURLException { }

  @BeforeClass
  public static void beforeAll() {
    vertxAssistant = new VertxAssistant();

    vertxAssistant.start();
  }

  @AfterClass
  public static void afterAll() {
    if (vertxAssistant != null) {
      vertxAssistant.stop();
    }
  }

  @Test
  public void canGetJson()
    throws InterruptedException, ExecutionException, TimeoutException {

    final String locationResponseHeader = "/a-different-location";

    fakeWebServer.stubFor(matchingFolioHeaders(get(urlEqualTo("/record")))
      .willReturn(okJson(new JsonObject().put("message", "hello").encodePrettily())
        .withHeader("Location", locationResponseHeader)));

    VertxWebClientOkapiHttpClient client =  createClientUsing(
      vertxAssistant.createUsingVertx(Vertx::createHttpClient), okapiUrl,
      tenantId, token, userId, requestId);

    CompletableFuture<Result<Response>> getCompleted = client.get(
      fakeWebServer.url("/record"));

    final Response response = getCompleted.get(2, SECONDS).value();

    assertThat(response, hasStatus(HTTP_OK));
    assertThat(response.getJson().getString("message"), is("hello"));
    assertThat(response.getContentType(), is("application/json"));
    assertThat(response.getHeader("location"), is(locationResponseHeader));
  }

  @Test
  public void canDeleteAResource()
    throws InterruptedException, ExecutionException, TimeoutException {

    fakeWebServer.stubFor(matchingFolioHeaders(delete(urlEqualTo("/record")))
      .willReturn(noContent()));

    VertxWebClientOkapiHttpClient client =  createClientUsing(
      vertxAssistant.createUsingVertx(Vertx::createHttpClient), okapiUrl,
      tenantId, token, userId, requestId);

    CompletableFuture<Result<Response>> getCompleted = client.delete(
      fakeWebServer.url("/record"));

    final Response response = getCompleted.get(2, SECONDS).value();

    assertThat(response, hasStatus(HTTP_NO_CONTENT));
  }

  @Test
  public void failsWhenGetTimesOut()
    throws InterruptedException, ExecutionException, TimeoutException {

    fakeWebServer.stubFor(matchingFolioHeaders(get(urlEqualTo("/record")))
      .willReturn(aResponse().withFixedDelay(1000)));

    VertxWebClientOkapiHttpClient client =  createClientUsing(
      vertxAssistant.createUsingVertx(Vertx::createHttpClient), okapiUrl,
      tenantId, token, userId, requestId);

    CompletableFuture<Result<Response>> getCompleted
      = client.get(fakeWebServer.url("/record"), Duration.of(500, MILLIS));

    final Result<Response> responseResult = getCompleted.get(1, SECONDS);

    assertThat(responseResult.failed(), is(true));
    assertThat(responseResult.cause(), is(instanceOf(ServerErrorFailure.class)));

    final ServerErrorFailure cause = (ServerErrorFailure) responseResult.cause();

    assertThat(cause.getReason(), containsString(
      "The timeout period of 500ms has been exceeded while executing " +
        "GET /record for host localhost"));
  }

  //TODO: Maybe replace this with a filter extension
  private MappingBuilder matchingFolioHeaders(MappingBuilder mappingBuilder) {
    return mappingBuilder
      .withHeader("X-Okapi-Url", equalTo(okapiUrl.toString()))
      .withHeader("X-Okapi-Tenant", equalTo(tenantId))
      .withHeader("X-Okapi-Token", equalTo(token))
      .withHeader("X-Okapi-User-Id", equalTo(userId))
      .withHeader("X-Okapi-Request-Id", equalTo(requestId));
  }
}
