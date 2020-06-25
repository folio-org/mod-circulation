package api.support.spring.clients;

import static api.support.http.Limit.noLimit;
import static api.support.http.Offset.noOffset;

import java.net.URL;
import java.util.List;

import api.support.RestAssuredClient;
import api.support.http.CqlQuery;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

@AllArgsConstructor
public final class ResourceClient<T> {
  private final RestAssuredClient restAssuredClient;
  private final URL baseUrl;
  private final Class<T> type;
  private final String collectionName;

  public ResourceClient(RestAssuredClient client, URL baseUrl, Class<T> type) {
    this(client, baseUrl, type, generateCollectionNameFromType(type));
  }

  public T getById(String id) {
    return restAssuredClient.get(baseUrl("/" + id), "get-by-id", type);
  }

  public List<T> getMany(CqlQuery cqlQuery) {
    return restAssuredClient.getMany(baseUrl, type, collectionName,
      cqlQuery, noLimit(), noOffset());
  }

  @SneakyThrows
  private URL baseUrl(String subPath) {
    return new URL(baseUrl + subPath);
  }

  private static <T> String generateCollectionNameFromType(Class<T> type) {
    return type.getTypeName().toLowerCase() + "s";
  }
}
