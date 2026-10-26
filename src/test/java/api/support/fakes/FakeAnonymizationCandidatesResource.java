package api.support.fakes;

import static api.support.APITestContext.createWebClient;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLEncoder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import api.support.http.InterfaceUrls;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Fake of GET /loan-storage/anonymization-candidates. The users' preferences are set by the
 * tests, every request is recorded.
 */
public class FakeAnonymizationCandidatesResource implements Handler<RoutingContext> {
  private static final Map<String, String> preferences = new ConcurrentHashMap<>();
  private static final List<MultiMap> requests = new CopyOnWriteArrayList<>();

  public static void setPreference(String userId, String preference) {
    preferences.put(userId, preference);
  }

  public static List<MultiMap> getRequests() {
    return requests;
  }

  public static void reset() {
    preferences.clear();
    requests.clear();
  }

  @Override
  public void handle(RoutingContext routingContext) {
    final MultiMap parameters = MultiMap.caseInsensitiveMultiMap()
      .addAll(routingContext.queryParams());
    requests.add(parameters);

    final String preference = parameters.get("preference");
    final String returnedBefore = parameters.get("returnedBefore");
    final int limit = Integer.parseInt(parameters.get("limit"));

    final String query = URLEncoder.encode("status.name==\"Closed\" and userId=\"\"", UTF_8);

    createWebClient().get(InterfaceUrls.loansStorageUrl("?limit=10000&query=" + query).toString())
      .thenAccept(result -> {
        final JsonArray loans = new JsonArray();
        result.value().getJson().getJsonArray("loans").stream()
          .map(JsonObject.class::cast)
          .filter(inGroup(preference))
          .filter(returnedBefore(returnedBefore))
          .limit(limit)
          .forEach(loans::add);

        routingContext.response()
          .putHeader("Content-type", "application/json")
          .setStatusCode(200)
          .end(new JsonObject()
            .put("loans", loans)
            .put("totalRecords", loans.size())
            .encode());
      });
  }

  private static Predicate<JsonObject> inGroup(String preference) {
    return loan -> {
      String userPreference = preferences.get(loan.getString("userId"));

      return "asEarlyAsPossible".equals(preference)
        ? "asEarlyAsPossible".equals(userPreference)
        : !"asEarlyAsPossible".equals(userPreference) && !"never".equals(userPreference);
    };
  }

  private static Predicate<JsonObject> returnedBefore(String returnedBefore) {
    if (returnedBefore == null) {
      return loan -> true;
    }

    OffsetDateTime cutoff = OffsetDateTime.parse(returnedBefore);

    return loan -> loan.getString("systemReturnDate") != null
      && OffsetDateTime.parse(loan.getString("systemReturnDate")).isBefore(cutoff);
  }
}
