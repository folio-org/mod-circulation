package api.support.spring;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.itemsStorageUrl;
import static api.support.http.InterfaceUrls.scheduledAgeToLostUrl;
import static api.support.http.InterfaceUrls.usersUrl;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import api.support.RestAssuredClient;
import api.support.dto.Item;
import api.support.dto.User;
import api.support.fixtures.OverrideRenewalFixture;
import api.support.jackson.serializer.JsonObjectJacksonSerializer;
import api.support.spring.clients.ResourceClient;
import api.support.spring.clients.ScheduledJobClient;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.vertx.core.json.JsonObject;

@Configuration
@SuppressWarnings("unused")
public class TestSpringConfiguration {
  @Bean
  public RestAssuredClient restAssuredClient(RestAssuredConfig config) {
    return new RestAssuredClient(getOkapiHeadersFromContext(), config);
  }

  @Bean
  public ResourceClient<Item> itemClient(RestAssuredClient client) {
    return new ResourceClient<>(client, itemsStorageUrl(""), Item.class);
  }

  @Bean
  public ResourceClient<User> userClient(RestAssuredClient client) {
    return new ResourceClient<>(client, usersUrl(""), User.class);
  }

  @Bean
  public ScheduledJobClient scheduledAgeToLostClient(RestAssuredClient client) {
    return new ScheduledJobClient(scheduledAgeToLostUrl(), client);
  }

  @Bean
  public OverrideRenewalFixture overrideRenewalFixture(RestAssuredClient client) {
    return new OverrideRenewalFixture(client, itemClient(client), userClient(client));
  }

  @Bean
  public RestAssuredConfig restAssuredConfig() {
    final ObjectMapperConfig objectMapperConfig = new ObjectMapperConfig()
      .jackson2ObjectMapperFactory((type, s) -> objectMapper());

    return new RestAssuredConfig().objectMapperConfig(objectMapperConfig);
  }

  @Bean
  public ObjectMapper objectMapper() {
    final SimpleModule jsonObjectMapper = new SimpleModule();
    jsonObjectMapper.addSerializer(JsonObject.class, new JsonObjectJacksonSerializer());

    return new ObjectMapper().findAndRegisterModules()
      .setSerializationInclusion(NON_NULL)
      .disable(FAIL_ON_UNKNOWN_PROPERTIES)
      .disable(WRITE_DATES_AS_TIMESTAMPS);
  }
}
