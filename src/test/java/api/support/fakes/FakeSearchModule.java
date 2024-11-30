package api.support.fakes;

import static java.lang.String.format;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.http.server.ClientErrorResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.SneakyThrows;

public class FakeSearchModule {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public static final String RECORD_TYPE_NAME = "search-instance";
  private static final String ID_REGEXP = "id\\s*(==|!=|>|>=|<|<=|\\|=|\\|=)\\s*" +
    "([a-f0-9\\-]+)";
  private final Storage storage;
  private final Pattern idPattern;
  private static final String ROOT_PATH = "/search/instances";

  public FakeSearchModule() {
    this.idPattern = Pattern.compile(ID_REGEXP);
    this.storage = Storage.getStorage();
  }

  @SneakyThrows
  public void register(Router router) {
    router.get("/search/instances").handler(this::getById);
  }

  private void getById(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    Result<UUID> idParsingResult = getIdParameter(routingContext);

    if (idParsingResult.failed()) {
      idParsingResult.cause().writeTo(routingContext.response());
      return;
    }

    Map<String, JsonObject> resourcesForTenant = getResourcesForTenant(context);

    final String id = idParsingResult.value().toString();

    if (resourcesForTenant.containsKey(id)) {
      final JsonObject resourceRepresentation = resourcesForTenant.get(id);

      final JsonObject searchResult = new JsonObject();

      searchResult.put("totalRecords", 1);
      searchResult.put("instances", List.of(resourceRepresentation));

      log.debug("Found {} resource: {}", RECORD_TYPE_NAME,
        searchResult.encodePrettily());

      HttpServerResponse response = routingContext.response();
      Buffer buffer = Buffer.buffer(Json.encodePrettily(searchResult), "UTF-8");

      response.setStatusCode(200);
      response.putHeader("content-type", "application/json; charset=utf-8");
      response.putHeader("content-length", Integer.toString(buffer.length()));

      response.write(buffer);
      response.end();
    }
    else {
      log.debug("Failed to find {} resource: {}", RECORD_TYPE_NAME,
        idParsingResult);

      ClientErrorResponse.notFound(routingContext.response());
    }
  }

  private Result<UUID> getIdParameter(RoutingContext routingContext) {
    final String query = routingContext.request().getParam("query");
    Matcher matcher = idPattern.matcher(query);
    String id = matcher.find() ? matcher.group(2) : null;

    return Result.of(() -> UUID.fromString(id))
      .mapFailure(r -> failedDueToServerError(format(
        "ID parameter \"%s\" is not a valid UUID", id)));
  }

  private Map<String, JsonObject> getResourcesForTenant(WebContext context) {
    return storage.getTenantResources(ROOT_PATH, context.getTenantId());
  }
}
