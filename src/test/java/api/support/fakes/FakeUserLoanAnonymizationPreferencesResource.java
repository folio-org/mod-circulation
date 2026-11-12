package api.support.fakes;

import static api.support.fakes.Storage.getStorage;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;

import java.util.HashMap;
import java.util.Map;

import api.support.APITestContext;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Full replace of the preference table in mod-circulation-storage, keeps the rows as
 * userId → preference.
 */
public class FakeUserLoanAnonymizationPreferencesResource implements Handler<RoutingContext> {
  private static final String ROOT_PATH =
    "/loan-anonymization-preference-storage/loan-anonymization-preferences";

  @Override
  public void handle(RoutingContext routingContext) {
    routingContext.request().bodyHandler(body -> {
      JsonObject request = body.toJsonObject();
      Map<String, JsonObject> rows = getStorage().getTenantResources(ROOT_PATH,
        routingContext.request().headers().get(TENANT));

      rows.clear();
      request.getJsonArray("preferences").stream()
        .map(JsonObject.class::cast)
        .forEach(preference -> rows.put(preference.getString("userId"), preference));

      routingContext.response().setStatusCode(204).end();
    });
  }

  public static Map<String, String> storedPreferences() {
    Map<String, String> preferences = new HashMap<>();
    getStorage().getTenantResources(ROOT_PATH, APITestContext.getTenantId())
      .forEach((userId, row) -> preferences.put(userId, row.getString("preference")));
    return preferences;
  }

  public static void storePreference(String userId, String preference) {
    getStorage().getTenantResources(ROOT_PATH, APITestContext.getTenantId())
      .put(userId, new JsonObject().put("userId", userId).put("preference", preference));
  }
}
