package org.folio.circulation.support.http.client;

import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.circulation.support.http.client.NamedQueryParameter.namedParameter;
import static org.folio.circulation.support.http.client.VertxWebClientOkapiHttpClient.createClientUsing;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.VertxAssistant;
import org.folio.circulation.support.results.Result;
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
  public WireMockRule fakeWebServer = new WireMockRule(options().port(8081));

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
  public void canPostWithJson()
    throws InterruptedException, ExecutionException, TimeoutException {

    final String locationResponseHeader = "/a-different-location";

    fakeWebServer.stubFor(matchingFolioHeaders(post(urlPathEqualTo("/record")))
      .withHeader("Content-Type", equalTo("application/json"))
      .withRequestBody(equalToJson(dummyJsonRequestBody().encodePrettily()))
      .willReturn(created().withBody(dummyJsonResponseBody())
        .withHeader("Content-Type", "application/json")
        .withHeader("Location", locationResponseHeader)));

    OkapiHttpClient client = createClient();

    CompletableFuture<Result<Response>> postCompleted = client.post(
      fakeWebServer.url("/record"), dummyJsonRequestBody());

    final Response response = postCompleted.get(2, SECONDS).value();

    assertThat(response, hasStatus(HTTP_CREATED));
    assertThat(response.getJson().getString("message"), is("hello"));
    assertThat(response.getContentType(), is("application/json"));
    assertThat(response.getHeader("location"), is(locationResponseHeader));
  }

  @Test
  public void canGetJson()
    throws InterruptedException, ExecutionException, TimeoutException {

    final String locationResponseHeader = "/a-different-location";

    fakeWebServer.stubFor(matchingFolioHeaders(get(urlPathEqualTo("/record")))
      .willReturn(okJson(dummyJsonResponseBody())
        .withHeader("Location", locationResponseHeader)));

    OkapiHttpClient client = createClient();

    CompletableFuture<Result<Response>> getCompleted = client.get(
      fakeWebServer.url("/record"));

    final Response response = getCompleted.get(2, SECONDS).value();

    assertThat(response, hasStatus(HTTP_OK));
    assertThat(response.getJson().getString("message"), is("hello"));
    assertThat(response.getContentType(), is("application/json"));
    assertThat(response.getHeader("location"), is(locationResponseHeader));
  }

  @Test
  public void canGetJsonUsingQueryParameters()
    throws InterruptedException, ExecutionException, TimeoutException {

    fakeWebServer.stubFor(matchingFolioHeaders(get(urlPathEqualTo("/record")))
      .withQueryParam("first-parameter", equalTo("foo"))
      .withQueryParam("second-parameter", equalTo("bar"))
      .willReturn(okJson(dummyJsonResponseBody())));

    OkapiHttpClient client = createClient();

    CompletableFuture<Result<Response>> getCompleted = client.get(
      fakeWebServer.url("/record"), namedParameter("first-parameter", "foo"),
        namedParameter("second-parameter", "bar"));

    final Response response = getCompleted.get(2, SECONDS).value();

    assertThat(response, hasStatus(HTTP_OK));
    assertThat(response.getJson().getString("message"), is("hello"));
    assertThat(response.getContentType(), is("application/json"));
  }

  @Test
  public void canPutWithJson()
    throws InterruptedException, ExecutionException, TimeoutException {

    fakeWebServer.stubFor(
      matchingFolioHeaders(put(urlPathEqualTo("/record/12345")))
      .withHeader("Content-Type", equalTo("application/json"))
      .withRequestBody(equalToJson(dummyJsonRequestBody().encodePrettily()))
      .willReturn(noContent()));

    OkapiHttpClient client = createClient();

    CompletableFuture<Result<Response>> postCompleted = client.put(
      fakeWebServer.url("/record/12345"), dummyJsonRequestBody());

    final Response response = postCompleted.get(2, SECONDS).value();

    assertThat(response, hasStatus(HTTP_NO_CONTENT));
    assertThat(response.getBody(), is(emptyOrNullString()));
  }

  @Test
  public void canDeleteAResource()
    throws InterruptedException, ExecutionException, TimeoutException {

    fakeWebServer.stubFor(matchingFolioHeaders(delete(urlPathEqualTo("/record")))
      .willReturn(noContent()));

    OkapiHttpClient client = createClient();

    CompletableFuture<Result<Response>> getCompleted = client.delete(
      fakeWebServer.url("/record"));

    final Response response = getCompleted.get(2, SECONDS).value();

    assertThat(response, hasStatus(HTTP_NO_CONTENT));
  }

  @Test
  public void canDeleteAResourceUsingQueryParameters()
    throws InterruptedException, ExecutionException, TimeoutException {

    fakeWebServer.stubFor(matchingFolioHeaders(delete(urlPathEqualTo("/record")))
      .withQueryParam("first-parameter", equalTo("foo"))
      .withQueryParam("second-parameter", equalTo("bar"))
      .willReturn(noContent()));

    OkapiHttpClient client = createClient();

    CompletableFuture<Result<Response>> deleteCompleted = client.delete(
      fakeWebServer.url("/record"), namedParameter("first-parameter", "foo"),
      namedParameter("second-parameter", "bar"));

    final Response response = deleteCompleted.get(2, SECONDS).value();

    assertThat(response, hasStatus(HTTP_NO_CONTENT));
  }

  @Test
  public void failsWhenGetTimesOut()
    throws InterruptedException, ExecutionException, TimeoutException {

    fakeWebServer.stubFor(matchingFolioHeaders(get(urlPathEqualTo("/record")))
      .willReturn(aResponse().withFixedDelay(1000)));

    OkapiHttpClient client = createClient();

    CompletableFuture<Result<Response>> getCompleted
      = client.get(fakeWebServer.url("/record"), Duration.of(500, MILLIS));

    final Result<Response> responseResult = getCompleted.get(1, SECONDS);

    assertThat(responseResult.failed(), is(true));
    assertThat(responseResult.cause(), is(instanceOf(ServerErrorFailure.class)));

    final ServerErrorFailure cause = (ServerErrorFailure) responseResult.cause();

    assertThat(cause.getReason(), containsString(
      "The timeout period of 500ms has been exceeded while executing " +
        "GET /record for server"));
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

  private OkapiHttpClient createClient() {
    return createClientUsing(
      vertxAssistant.createUsingVertx(Vertx::createHttpClient), okapiUrl,
      tenantId, token, userId, requestId);
  }

  private JsonObject dummyJsonRequestBody() {
    return new JsonObject().put("from", "James");
  }

  private String dummyJsonResponseBody() {
    return new JsonObject().put("message", "hello")
      .encodePrettily();
  }
}
