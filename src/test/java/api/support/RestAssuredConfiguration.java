package api.support;

import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;

import java.util.HashMap;

import org.folio.circulation.support.http.OkapiHeader;

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

public class RestAssuredConfiguration {
  public static RequestSpecification standardHeaders(OkapiHeaders okapiHeaders) {
    final HashMap<String, String> headers = new HashMap<>();

    headers.put(OKAPI_URL, okapiHeaders.getUrl().toString());
    headers.put(TENANT, okapiHeaders.getTenantId());
    headers.put(OkapiHeader.TOKEN, okapiHeaders.getToken());
    headers.put(OkapiHeader.REQUEST_ID, okapiHeaders.getRequestId());

    if (okapiHeaders.hasUserId()) {
      headers.put(OkapiHeader.USER_ID, okapiHeaders.getUserId());
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
      .setConfig(RestAssured.config()
        .httpClient(HttpClientConfig.httpClientConfig()
          .setParam("http.connection.timeout", timeOutInMilliseconds)
          .setParam("http.socket.timeout", timeOutInMilliseconds)))
      .build();
  }

  public static RestAssuredConfig defaultRestAssuredConfig() {
    final ObjectMapperConfig objectMapperConfig = new ObjectMapperConfig()
      .jackson2ObjectMapperFactory((type, s) -> defaultObjectMapper());

    return new RestAssuredConfig().objectMapperConfig(objectMapperConfig);
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
