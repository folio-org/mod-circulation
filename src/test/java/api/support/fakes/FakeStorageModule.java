package api.support.fakes;

import api.APITestSuite;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.http.server.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.util.*;
import java.util.stream.Collectors;

public class FakeStorageModule extends AbstractVerticle {
  private final String rootPath;
  private final String collectionPropertyName;
  private final boolean hasCollectionDelete;
  private final Collection<String> requiredProperties;
  private final Map<String, Map<String, JsonObject>> storedResourcesByTenant;
  private final String recordTypeName;
  private final Collection<String> uniqueProperties;
  private final Boolean includeChangeMetadata;
  private final String changeMetadataPropertyName = "metadata";
  private Proxy proxyAs;

  public FakeStorageModule(
    String rootPath,
    String collectionPropertyName,
    String tenantId,
    Collection<String> requiredProperties,
    boolean hasCollectionDelete,
    String recordTypeName,
    Collection<String> uniqueProperties,
    Boolean includeChangeMetadata) {

    this.rootPath = rootPath;
    this.collectionPropertyName = collectionPropertyName;
    this.requiredProperties = requiredProperties;
    this.hasCollectionDelete = hasCollectionDelete;
    this.recordTypeName = recordTypeName;
    this.uniqueProperties = uniqueProperties;
    this.includeChangeMetadata = includeChangeMetadata;

    storedResourcesByTenant = new HashMap<>();
    storedResourcesByTenant.put(tenantId, new HashMap<>());
  }

  public void register(Router router) {
    String pathTree = rootPath + "/*";

    router.route(pathTree).handler(this::checkTokenHeader);
    router.route(pathTree).handler(this::checkRequestIdHeader);

    router.post(pathTree).handler(BodyHandler.create());
    router.put(pathTree).handler(BodyHandler.create());

    router.post(rootPath).handler(this::checkRequiredProperties);
    router.post(rootPath).handler(this::checkUniqueProperties);
    router.post(rootPath).handler(this::create);

    router.get(rootPath).handler(this::getMany);
    router.delete(rootPath).handler(this::empty);

    router.put(rootPath + "/:id").handler(this::checkRequiredProperties);
    router.put(rootPath + "/:id").handler(this::replace);

    router.get(rootPath + "/:id").handler(this::get);
    router.delete(rootPath + "/:id").handler(this::delete);

    if(proxyAs != null) {
      router.get(proxyAs.path).handler(this::getManyProxy);
      router.get(proxyAs.path + "/:id").handler(this::getProxy);
    }
  }

  private void create(RoutingContext routingContext) {

    WebContext context = new WebContext(routingContext);

    JsonObject body = getJsonFromBody(routingContext);

    String id = body.getString("id", UUID.randomUUID().toString());

    body.put("id", id);

    if(includeChangeMetadata) {
      final String fakeUserId = APITestSuite.USER_ID;
      body.put(changeMetadataPropertyName, new JsonObject()
        .put("createdDate", new DateTime(DateTimeZone.UTC)
          .toString(ISODateTimeFormat.dateTime()))
        .put("createdByUserId", fakeUserId)
        .put("updatedDate", new DateTime(DateTimeZone.UTC)
          .toString(ISODateTimeFormat.dateTime()))
        .put("updatedByUserId", fakeUserId));
    }

    getResourcesForTenant(context).put(id, body);

    System.out.println(
      String.format("Created %s resource: %s", recordTypeName, id));

    JsonResponse.created(routingContext.response(), body);
  }

  private void replace(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    JsonObject body = getJsonFromBody(routingContext);

    Map<String, JsonObject> resourcesForTenant = getResourcesForTenant(context);

    if(resourcesForTenant.containsKey(id)) {
      System.out.println(
        String.format("Replaced %s resource: %s", recordTypeName, id));

      if(includeChangeMetadata) {
        final String fakeUserId = APITestSuite.USER_ID;

        final JsonObject existingChangeMetadata = resourcesForTenant.get(id)
          .getJsonObject(changeMetadataPropertyName);

        final JsonObject updatedChangeMetadata = existingChangeMetadata.copy()
          .put("updatedDate", new DateTime(DateTimeZone.UTC)
            .toString(ISODateTimeFormat.dateTime()))
          .put("updatedByUserId", fakeUserId);

        body.put(changeMetadataPropertyName, updatedChangeMetadata);
      }

      resourcesForTenant.replace(id, body);
      SuccessResponse.noContent(routingContext.response());
    }
    else {
      System.out.println(
        String.format("Created %s resource: %s", recordTypeName, id));

      resourcesForTenant.put(id, body);
      SuccessResponse.noContent(routingContext.response());
    }
  }

  private void get(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    Map<String, JsonObject> resourcesForTenant = getResourcesForTenant(context);

    if(resourcesForTenant.containsKey(id)) {
      final JsonObject resourceRepresentation = resourcesForTenant.get(id);

      System.out.println(
        String.format("Found %s resource: %s", recordTypeName,
          resourceRepresentation.encodePrettily()));

      HttpServerResponse response = routingContext.response();

      String json = Json.encodePrettily(resourceRepresentation);
      Buffer buffer = Buffer.buffer(json, "UTF-8");

      response.setStatusCode(200);
      response.putHeader("content-type", "application/json; charset=utf-8");
      response.putHeader("content-length", Integer.toString(buffer.length()));

      //Add fake trace lines to reproduce CIRC-103
      List<String> traceValues = new ArrayList<>();

      traceValues.add("GET mod-authtoken-1.4.1-SNAPSHOT.21");
      traceValues.add("GET mod-circulation-10.1.0-SNAPSHOT.131");

      response.putHeader("X-Okapi-Trace", traceValues);

      response.write(buffer);
      response.end();
    }
    else {
      System.out.println(
        String.format("Failed to find %s resource: %s", recordTypeName, id));

      ClientErrorResponse.notFound(routingContext.response());
    }
  }

  private void getMany(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    Integer limit = context.getIntegerParameter("limit", 10);
    Integer offset = context.getIntegerParameter("offset", 0);
    String query = context.getStringParameter("query", null);

    System.out.println(String.format("Handling %s", routingContext.request().uri()));

    Map<String, JsonObject> resourcesForTenant = getResourcesForTenant(context);

    List<JsonObject> filteredItems = new FakeCQLToJSONInterpreter(false)
      .execute(resourcesForTenant.values(), query);

    List<JsonObject> pagedItems = filteredItems.stream()
      .skip(offset)
      .limit(limit)
      .collect(Collectors.toList());

    JsonObject result = new JsonObject();

    result.put(collectionPropertyName, new JsonArray(pagedItems));
    result.put("totalRecords", filteredItems.size());

    System.out.println(
      String.format("Found %s resources: %s", recordTypeName,
        result.encodePrettily()));

    HttpServerResponse response = routingContext.response();

    String json = Json.encodePrettily(result);
    Buffer buffer = Buffer.buffer(json, "UTF-8");

    response.setStatusCode(200);
    response.putHeader("content-type", "application/json; charset=utf-8");
    response.putHeader("content-length", Integer.toString(buffer.length()));
    response.putHeader("X-Okapi-Trace", "GET mod-authtoken-1.4.1-SNAPSHOT.21");
    response.putHeader("X-Okapi-Trace", "GET mod-circulation-10.1.0-SNAPSHOT.131");

    response.write(buffer);
    response.end();
  }

  private void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    if(!hasCollectionDelete) {
      ClientErrorResponse.notFound(routingContext.response());
      return;
    }

    Map <String, JsonObject> resourcesForTenant = getResourcesForTenant(context);

    resourcesForTenant.clear();

    SuccessResponse.noContent(routingContext.response());
  }

  private void delete(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    Map<String, JsonObject> resourcesForTenant = getResourcesForTenant(context);

    if(resourcesForTenant.containsKey(id)) {
      resourcesForTenant.remove(id);

      SuccessResponse.noContent(routingContext.response());
    }
    else {
      ClientErrorResponse.notFound(routingContext.response());
    }
  }

  private void getProxy(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    Map<String, JsonObject> resourcesForTenant = getResourcesForTenant(context);

    if(resourcesForTenant.containsKey(id)) {
      final JsonObject resourceRepresentation = resourcesForTenant.get(id);

      JsonObject mapped = new JsonObject();

      proxyAs.propertiesToMap.forEach(property -> {
        if(resourceRepresentation.containsKey(property)) {
          mapped.put(property, resourceRepresentation.getString(property));
        }
      });

      System.out.println(
        String.format("Proxying %s resource: %s", recordTypeName,
          mapped.encodePrettily()));

      JsonResponse.success(routingContext.response(), mapped);
    }
    else {
      System.out.println(
        String.format("Failed to proxy %s resource: %s", recordTypeName, id));

      ClientErrorResponse.notFound(routingContext.response());
    }
  }

  private void getManyProxy(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    Integer limit = context.getIntegerParameter("limit", 10);
    Integer offset = context.getIntegerParameter("offset", 0);
    String query = context.getStringParameter("query", null);

    System.out.println(String.format("Proxying %s", routingContext.request().uri()));

    Map<String, JsonObject> resourcesForTenant = getResourcesForTenant(context);

    List<JsonObject> filteredItems = new FakeCQLToJSONInterpreter(false)
      .execute(resourcesForTenant.values(), query);

    List<JsonObject> pagedItems = filteredItems.stream()
      .skip(offset)
      .limit(limit)
      .map(record -> {
        JsonObject mapped = new JsonObject();

        proxyAs.propertiesToMap.forEach(property -> {
          if(record.containsKey(property)) {
            mapped.put(property, record.getString(property));
          }
        });

        return mapped;
      })
      .collect(Collectors.toList());

    JsonObject result = new JsonObject();

    result.put(proxyAs.collectionPropertyName, new JsonArray(pagedItems));
    result.put("totalRecords", filteredItems.size());

    System.out.println(
      String.format("Found proxied %s resources: %s", recordTypeName,
        result.encodePrettily()));

    JsonResponse.success(routingContext.response(), result);
  }

  private Map<String, JsonObject> getResourcesForTenant(WebContext context) {
    return storedResourcesByTenant.get(context.getTenantId());
  }

  private static JsonObject getJsonFromBody(RoutingContext routingContext) {
    if (hasBody(routingContext)) {
      return routingContext.getBodyAsJson();
    } else {
      return new JsonObject();
    }
  }

  private static boolean hasBody(RoutingContext routingContext) {
    return StringUtils.isNotBlank(routingContext.getBodyAsString());
  }

  private void checkTokenHeader(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    if(StringUtils.isBlank(context.getOkapiToken())) {
      ClientErrorResponse.forbidden(routingContext.response());
    }
    else {
      routingContext.next();
    }
  }

  private void checkRequestIdHeader(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    if(StringUtils.isBlank(context.getRequestId())) {
      ClientErrorResponse.badRequest(routingContext.response(),
        "Request ID is expected for all requests during tests");
    }
    else {
      routingContext.next();
    }
  }

  private void checkRequiredProperties(RoutingContext routingContext) {
    JsonObject body = getJsonFromBody(routingContext);

    ArrayList<ValidationError> errors = new ArrayList<>();

    requiredProperties.stream().forEach(requiredProperty -> {
      if(!body.getMap().containsKey(requiredProperty)) {
        errors.add(new ValidationError("Required property missing", requiredProperty, ""));
      }
    });

    if(errors.isEmpty()) {
      routingContext.next();
    }
    else {
      JsonResponse.unprocessableEntity(routingContext.response(), errors);
    }
  }

  private void checkUniqueProperties(RoutingContext routingContext) {
    if(uniqueProperties.isEmpty()) {
      routingContext.next();
      return;
    }

    JsonObject body = getJsonFromBody(routingContext);

    ArrayList<ValidationError> errors = new ArrayList<>();

    uniqueProperties.stream().forEach(uniqueProperty -> {
      String proposedValue = body.getString(uniqueProperty);

      Map<String, JsonObject> records = getResourcesForTenant(new WebContext(routingContext));

      if(records.values().stream()
        .map(record -> record.getString(uniqueProperty))
        .anyMatch(usedValue -> usedValue.equals(proposedValue))) {

        errors.add(new ValidationError(
          String.format("%s with this %s already exists", recordTypeName, uniqueProperty),
          uniqueProperty, proposedValue));

        JsonResponse.unprocessableEntity(routingContext.response(),
          errors);
      }
    });

    if(errors.isEmpty()) {
      routingContext.next();
    }
  }

  private class Proxy {
    final String path;
    final String collectionPropertyName;
    final Collection<String> propertiesToMap;

    private Proxy(
      String path,
      String collectionPropertyName,
      Collection<String> propertiesToMap) {

      this.path = path;
      this.collectionPropertyName = collectionPropertyName;
      this.propertiesToMap = propertiesToMap;
    }
  }
}
