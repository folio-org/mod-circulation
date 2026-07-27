package api.support;

import static org.folio.circulation.support.http.OkapiHeader.OKAPI_PERMISSIONS;
import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.REQUEST_ID;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.folio.circulation.support.http.OkapiHeader.TOKEN;
import static org.folio.circulation.support.http.OkapiHeader.USER_ID;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import api.support.http.OkapiHeaders;
import api.support.jackson.serializer.JsonObjectJacksonSerializer;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;

public class RestAssuredConfiguration {
  private static final String CONNECTION_TIMEOUT = "http.connection.timeout";
  private static final String SOCKET_TIMEOUT = "http.socket.timeout";
  private static final int MAX_CONNECTIONS = 200;

  private static final PoolingClientConnectionManager CONNECTION_MANAGER =
    new PoolingClientConnectionManager();

  static {
    CONNECTION_MANAGER.setMaxTotal(MAX_CONNECTIONS);
    CONNECTION_MANAGER.setDefaultMaxPerRoute(MAX_CONNECTIONS);
  }

  private static final HttpClientConfig REUSABLE_HTTP_CLIENT_CONFIG =
    HttpClientConfig.httpClientConfig()
      .httpClientFactory(RestAssuredConfiguration::pooledHttpClient)
      .reuseHttpClientInstance();

  private static final ConcurrentMap<Integer, RestAssuredConfig> TIMEOUT_CONFIGS =
    new ConcurrentHashMap<>();

  public static RequestSpecification standardHeaders(OkapiHeaders okapiHeaders) {
    final HashMap<String, String> headers = new HashMap<>();

    headers.put(OKAPI_URL, okapiHeaders.getUrl().toString());
    headers.put(TENANT, APITestContext.getTenantId());
    headers.put(TOKEN, okapiHeaders.getToken());
    headers.put(REQUEST_ID, okapiHeaders.getRequestId());
    headers.put(OKAPI_PERMISSIONS, okapiHeaders.getOkapiPermissions());

    if (okapiHeaders.hasUserId()) {
      headers.put(USER_ID, okapiHeaders.getUserId());
    }

    return new RequestSpecBuilder()
      .addHeaders(headers)
      .setAccept("application/json, text/plain")
      .setContentType("application/json")
      .build();
  }

  public static RequestSpecification timeoutConfig() {
    final int defaultTimeOutInMilliseconds = 10000;

    return timeoutConfig(defaultTimeOutInMilliseconds);
  }

  public static RequestSpecification timeoutConfig(int timeOutInMilliseconds) {
    return new RequestSpecBuilder()
      .setConfig(TIMEOUT_CONFIGS.computeIfAbsent(timeOutInMilliseconds,
        RestAssuredConfiguration::timeoutConfigFor))
      .build();
  }

  public static RestAssuredConfig defaultRestAssuredConfig() {
    final ObjectMapperConfig objectMapperConfig = new ObjectMapperConfig()
      .jackson2ObjectMapperFactory((type, s) -> defaultObjectMapper());

    return configWithReusableHttpClient(objectMapperConfig);
  }

  public static RestAssuredConfig configWithReusableHttpClient(
    ObjectMapperConfig objectMapperConfig) {

    return new RestAssuredConfig()
      .objectMapperConfig(objectMapperConfig)
      .httpClient(REUSABLE_HTTP_CLIENT_CONFIG);
  }

  private static RestAssuredConfig timeoutConfigFor(int timeOutInMilliseconds) {
    return RestAssured.config()
      .httpClient(REUSABLE_HTTP_CLIENT_CONFIG
        .setParam(CONNECTION_TIMEOUT, timeOutInMilliseconds)
        .setParam(SOCKET_TIMEOUT, timeOutInMilliseconds));
  }

  @SuppressWarnings("deprecation")
  private static HttpClient pooledHttpClient() {
    return new DefaultHttpClient(CONNECTION_MANAGER);
  }

  /**
   * Object mapper that allows JsonObject to be correctly serialized.
   */
  private static ObjectMapper defaultObjectMapper() {
    final SimpleModule jsonObjectMapperModule = new SimpleModule();
    jsonObjectMapperModule.addSerializer(JsonObject.class, new JsonObjectJacksonSerializer());

    return new ObjectMapper().findAndRegisterModules()
      .registerModule(jsonObjectMapperModule);
  }
}
