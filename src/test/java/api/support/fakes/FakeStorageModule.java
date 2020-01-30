package api.support.fakes;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.infrastructure.serialization.JsonSchemaValidator;
import org.folio.circulation.support.CreatedJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ClientErrorResponse;
import org.folio.circulation.support.http.server.SuccessResponse;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import api.support.APITestContext;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeStorageModule extends AbstractVerticle {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final Set<String> queries = Collections.synchronizedSet(new HashSet<>());

  private final String rootPath;
  private final String collectionPropertyName;
  private final boolean hasCollectionDelete;
  private final boolean hasDeleteByQuery;
  private final Collection<String> requiredProperties;
  private final Map<String, Map<String, JsonObject>> storedResourcesByTenant;
  private final String recordTypeName;
  private final Collection<String> uniqueProperties;
  private final Collection<String> disallowedProperties;
  private final Boolean includeChangeMetadata;
  private final String changeMetadataPropertyName = "metadata";
  private final BiFunction<Collection<JsonObject>, JsonObject, Result<Object>> constraint;
  private final JsonSchemaValidator recordValidator;
  private final String batchUpdatePath;
  private final Function<JsonObject, JsonObject> batchUpdatePreProcessor;
  private final List<BiFunction<JsonObject, JsonObject, CompletableFuture<JsonObject>>> recordPreProcessors;
  private final Collection<String> additionalQueryParameters;

  public static Stream<String> getQueries() {
    return queries.stream();
  }

  FakeStorageModule(
    String rootPath,
    String collectionPropertyName,
    String tenantId,
    JsonSchemaValidator recordValidator,
    @Deprecated Collection<String> requiredProperties,
    boolean hasCollectionDelete,
    boolean hasDeleteByQuery,
    String recordTypeName,
    Collection<String> uniqueProperties,
    @Deprecated Collection<String> disallowedProperties,
    Boolean includeChangeMetadata,
    BiFunction<Collection<JsonObject>, JsonObject, Result<Object>> constraint,
    String batchUpdatePath,
    Function<JsonObject, JsonObject> batchUpdatePreProcessor,
    List<BiFunction<JsonObject, JsonObject, CompletableFuture<JsonObject>>> recordPreProcessors,
    Collection<String> queryParameters) {

    this.rootPath = rootPath;
    this.collectionPropertyName = collectionPropertyName;
    this.requiredProperties = requiredProperties;
    this.hasCollectionDelete = hasCollectionDelete;
    this.hasDeleteByQuery = hasDeleteByQuery;
    this.recordTypeName = recordTypeName;
    this.uniqueProperties = uniqueProperties;
    this.disallowedProperties = disallowedProperties;
    this.constraint = constraint;
    this.includeChangeMetadata = includeChangeMetadata;
    this.recordValidator = recordValidator;
    this.additionalQueryParameters = Objects.isNull(queryParameters)
      ? new ArrayList<>()
      : queryParameters;
    this.batchUpdatePath = batchUpdatePath;
    this.batchUpdatePreProcessor = batchUpdatePreProcessor;
    this.recordPreProcessors = recordPreProcessors;

    storedResourcesByTenant = new HashMap<>();
    storedResourcesByTenant.put(tenantId, new HashMap<>());
  }

  void register(Router router) {
    String pathTree = rootPath + "/*";

    router.route(rootPath).handler(this::checkTokenHeader);
    router.route(pathTree).handler(this::checkTokenHeader);
    router.route(rootPath).handler(this::checkRequestIdHeader);
    router.route(pathTree).handler(this::checkRequestIdHeader);

    router.post(rootPath).handler(BodyHandler.create());
    router.post(pathTree).handler(BodyHandler.create());
    router.put(rootPath).handler(BodyHandler.create());
    router.put(pathTree).handler(BodyHandler.create());

    router.post(rootPath).handler(this::checkRepresentationAgainstRecordSchema);
    router.post(rootPath).handler(this::checkRequiredProperties);
    router.post(rootPath).handler(this::checkUniqueProperties);
    router.post(rootPath).handler(this::checkDisallowedProperties);
    router.post(rootPath).handler(this::create);

    router.get(rootPath).handler(this::checkForUnexpectedQueryParameters);
    router.get(rootPath).handler(this::getMany);

    if (hasDeleteByQuery) {
      router.delete(rootPath).handler(this::deleteMany);
    } else {
      router.delete(rootPath).handler(this::empty);
    }

    router.put(rootPath + "/:id").handler(this::checkRepresentationAgainstRecordSchema);
    router.put(rootPath + "/:id").handler(this::checkRequiredProperties);
    router.put(rootPath + "/:id").handler(this::checkDisallowedProperties);
    router.put(rootPath + "/:id").handler(this::replace);

    router.get(rootPath + "/:id").handler(this::getById);
    router.delete(rootPath + "/:id").handler(this::delete);

    if (StringUtils.isNotBlank(batchUpdatePath)) {
      router.route(batchUpdatePath).handler(this::checkTokenHeader);
      router.route(batchUpdatePath).handler(this::checkRequestIdHeader);

      router.post(batchUpdatePath).handler(BodyHandler.create());
      router.post(batchUpdatePath).handler(this::batchUpdate);
    }
  }

  private void batchUpdate(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);
    JsonObject body = routingContext.getBodyAsJson();

    if (batchUpdatePreProcessor != null) {
      body = batchUpdatePreProcessor.apply(body);
    }

    JsonArray entities = body.getJsonArray(collectionPropertyName);
    CompletableFuture<Result<Void>> replaceFuture =
      CompletableFuture.completedFuture(Result.succeeded(null));

    for (int entityIndex = 0; entityIndex < entities.size(); entityIndex++) {
      JsonObject entity = entities.getJsonObject(entityIndex);
      String id = entity.getString("id");

      replaceFuture = replaceFuture
        .thenCompose(r -> r.after(v -> replaceSingleItem(context, id, entity)));
    }

    replaceFuture.thenAccept(result -> {
      if (result.failed()) {
        result.cause().writeTo(routingContext.response());
      }
      routingContext.response().setStatusCode(201).end();
    });
  }

  private void create(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    JsonObject originalBody = getJsonFromBody(routingContext);

    String id = originalBody.getString("id", UUID.randomUUID().toString());

    originalBody.put("id", id);

    preProcessBody(null, originalBody).thenAccept(body -> {
      if (includeChangeMetadata) {
        final String fakeUserId = APITestContext.getUserId();
        body.put(changeMetadataPropertyName, new JsonObject()
          .put("createdDate", new DateTime(DateTimeZone.UTC)
            .toString(ISODateTimeFormat.dateTime()))
          .put("createdByUserId", fakeUserId)
          .put("updatedDate", new DateTime(DateTimeZone.UTC)
            .toString(ISODateTimeFormat.dateTime()))
          .put("updatedByUserId", fakeUserId));
      }

      final Map<String, JsonObject> existingRecords = getResourcesForTenant(context);

      if (constraint == null) {
        existingRecords.put(id, body);

        log.debug("Created {} resource: {}", recordTypeName, id);

        new CreatedJsonResponseResult(body, null)
          .writeTo(routingContext.response());
      } else {
        final Result<Object> checkConstraint = constraint.apply(existingRecords.values(), body);

        if (checkConstraint.succeeded()) {
          existingRecords.put(id, body);

          log.debug("Created {} resource: {}", recordTypeName, id);

          new CreatedJsonResponseResult(body, null)
            .writeTo(routingContext.response());
        } else {
          checkConstraint.cause().writeTo(routingContext.response());
        }
      }
    });
  }

  private void replace(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    JsonObject body = getJsonFromBody(routingContext);

    replaceSingleItem(context, id, body).thenAccept(replaceResult -> {
      if (replaceResult.succeeded()) {
        SuccessResponse.noContent(routingContext.response());
      } else {
        replaceResult.cause().writeTo(routingContext.response());
      }
    });
  }

  private CompletableFuture<Result<Void>> replaceSingleItem(
    WebContext context, String id, JsonObject rawBody) {
    Map<String, JsonObject> resourceForTenant = getResourcesForTenant(context);
    JsonObject oldBody = resourceForTenant.get(id);

    return preProcessBody(oldBody, rawBody).thenApply(body -> {
      Map<String, JsonObject> resourcesForTenant = getResourcesForTenant(context);

      if (resourcesForTenant.containsKey(id)) {
        log.debug("Replaced {} resource: {}", recordTypeName, id);

        if (includeChangeMetadata) {
          final String fakeUserId = APITestContext.getUserId();
          final JsonObject existingChangeMetadata = resourcesForTenant.get(id)
            .getJsonObject(changeMetadataPropertyName);

          final JsonObject updatedChangeMetadata = existingChangeMetadata.copy()
            .put("updatedDate", new DateTime(DateTimeZone.UTC)
              .toString(ISODateTimeFormat.dateTime()))
            .put("updatedByUserId", fakeUserId);

          body.put(changeMetadataPropertyName, updatedChangeMetadata);
        }

        if (constraint == null) {
          resourcesForTenant.replace(id, body);
        } else {
          final Result<Object> checkConstraint = constraint.apply(resourcesForTenant.values(), body);

          if (checkConstraint.succeeded()) {
            resourcesForTenant.replace(id, body);
          } else {
            return Result.failed(checkConstraint.cause());
          }
        }
      } else {
        log.debug("Created {} resource: {}", recordTypeName, id);

        if (includeChangeMetadata) {
          final String fakeUserId = APITestContext.getUserId();
          body.put(changeMetadataPropertyName, new JsonObject()
            .put("createdDate", new DateTime(DateTimeZone.UTC)
              .toString(ISODateTimeFormat.dateTime()))
            .put("createdByUserId", fakeUserId)
            .put("updatedDate", new DateTime(DateTimeZone.UTC)
              .toString(ISODateTimeFormat.dateTime()))
            .put("updatedByUserId", fakeUserId));
        }

        resourcesForTenant.put(id, body);
      }
      return Result.succeeded(null);
    });
  }

  private void getById(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    Result<UUID> idParsingResult = getIdParameter(routingContext);

    if(idParsingResult.failed()) {
      idParsingResult.cause().writeTo(routingContext.response());
      return;
    }

    Map<String, JsonObject> resourcesForTenant = getResourcesForTenant(context);

    final String id = idParsingResult.value().toString();

    if(resourcesForTenant.containsKey(id)) {
      final JsonObject resourceRepresentation = resourcesForTenant.get(id);

      log.debug("Found {} resource: {}", recordTypeName,
        resourceRepresentation.encodePrettily());

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
      log.debug("Failed to find {} resource: {}", recordTypeName,
        idParsingResult);

      ClientErrorResponse.notFound(routingContext.response());
    }
  }

  private void getMany(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    Integer limit = context.getIntegerParameter("limit", 10);
    Integer offset = context.getIntegerParameter("offset", 0);
    String query = context.getStringParameter("query", null);

    log.debug("Handling {}", routingContext.request().uri());

    if(query != null) {
      queries.add(format("%s?%s", routingContext.request().path(), query));
    }

    Map<String, JsonObject> resourcesForTenant = getResourcesForTenant(context);

    List<JsonObject> filteredItems = new FakeCQLToJSONInterpreter()
      .execute(resourcesForTenant.values(), query);

    List<JsonObject> pagedItems = filteredItems.stream()
      .skip(offset)
      .limit(limit)
      .collect(Collectors.toList());

    JsonObject result = new JsonObject();

    result.put(collectionPropertyName, new JsonArray(pagedItems));
    result.put("totalRecords", filteredItems.size());

    log.debug("Found {} resources: {}", recordTypeName, result.encodePrettily());

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

  private void deleteMany(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    String query = context.getStringParameter("query", null);

    Map<String, JsonObject> resourcesForTenant = getResourcesForTenant(context);

    new FakeCQLToJSONInterpreter()
      .execute(resourcesForTenant.values(), query)
      .forEach(item -> resourcesForTenant.remove(item.getString("id")));

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

    if(isBlank(context.getOkapiToken())) {
      forbidden(routingContext);
    }
    else {
      routingContext.next();
    }
  }

  private void forbidden(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    response.setStatusCode(403);
    response.end();
  }

  private void checkRequestIdHeader(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    if(isBlank(context.getRequestId())) {
      ClientErrorResponse.badRequest(routingContext.response(),
        "Request ID is expected for all requests during tests");
    }
    else {
      routingContext.next();
    }
  }

  private void checkRepresentationAgainstRecordSchema(RoutingContext routingContext) {
    if (recordValidator == null) {
      routingContext.next();
      return;
    }

    final Result<String> validationResult = recordValidator.validate(
      routingContext.getBodyAsString());

    if (validationResult.failed()) {
      validationResult.cause().writeTo(routingContext.response());
    }
    else {
      routingContext.next();
    }
  }

  private void checkRequiredProperties(RoutingContext routingContext) {
    JsonObject body = getJsonFromBody(routingContext);

    ArrayList<ValidationError> errors = new ArrayList<>();

    requiredProperties.forEach(requiredProperty -> {
      if(!body.getMap().containsKey(requiredProperty)) {
        errors.add(new ValidationError("Required property missing", requiredProperty, ""));
      }
    });

    if(errors.isEmpty()) {
      routingContext.next();
    }
    else {
      failedValidation(errors).writeTo(routingContext.response());
    }
  }

  //PgUtil from RAML Module Builder 24.0.0 fails with a 500 error like
  //ErrorMessage(fields=Map(Line -> 137, File -> uuid.c, SQLSTATE -> 22P02,
  // Routine -> string_to_uuid, V -> ERROR,
  // Message -> invalid input syntax for type uuid: "null", Severity -> ERROR))
  // when an ID parameter is not a UUID
  private Result<UUID> getIdParameter(RoutingContext routingContext) {
    final String id = routingContext.request().getParam("id");

    return Result.of(() -> UUID.fromString(id))
      .mapFailure(r -> failedDueToServerError(format(
        "ID parameter \"%s\" is not a valid UUID", id)));
  }

  private void checkUniqueProperties(RoutingContext routingContext) {
    if(uniqueProperties.isEmpty()) {
      routingContext.next();
      return;
    }

    JsonObject body = getJsonFromBody(routingContext);

    ArrayList<ValidationError> errors = new ArrayList<>();

    uniqueProperties.forEach(uniqueProperty -> {
      String proposedValue = body.getString(uniqueProperty);

      Map<String, JsonObject> records = getResourcesForTenant(new WebContext(routingContext));

      if(records.values().stream()
        .map(record -> record.getString(uniqueProperty))
        .anyMatch(usedValue -> usedValue.equals(proposedValue))) {

        errors.add(new ValidationError(
          format("%s with this %s already exists", recordTypeName, uniqueProperty),
          uniqueProperty, proposedValue));

        failedValidation(errors).writeTo(routingContext.response());
      }
    });

    if(errors.isEmpty()) {
      routingContext.next();
    }
  }

  private void checkDisallowedProperties(RoutingContext routingContext) {
    if(disallowedProperties.isEmpty()) {
      routingContext.next();
      return;
    }

    JsonObject body = getJsonFromBody(routingContext);

    ArrayList<ValidationError> errors = new ArrayList<>();

    disallowedProperties.forEach(disallowedProperty -> {
      if(body.containsKey(disallowedProperty)) {
        errors.add(new ValidationError(
          format("Unrecognised field \"%s\"", disallowedProperty),
          disallowedProperty, null));

        failedValidation(errors).writeTo(routingContext.response());
      }
    });

    if(errors.isEmpty()) {
      routingContext.next();
    }
  }

  private void checkForUnexpectedQueryParameters(RoutingContext routingContext) {
    //Check for only expected query string parameters
    // as incorrectly formed query parameters will respond with all records
    // e.g. ?id=foo will be ignored
    final String rawQuery = routingContext.request().query();

    log.debug("Query: {}", rawQuery);

    if (rawQuery == null) {
      routingContext.next();
      return;
    }

    log.debug("Split query parameters");

    final List<String> unexpectedParameters = Arrays.stream(rawQuery.split("&"))
      .filter(queryParameter -> {
        boolean isValidParameter = queryParameter.contains("query") ||
          queryParameter.contains("offset") ||
          isContainsQueryParameter(queryParameter) ||
          queryParameter.contains("limit");

        return !isValidParameter;
      })
      .collect(Collectors.toList());

    if(unexpectedParameters.isEmpty()) {
      routingContext.next();
      return;
    }

    log.debug("Unexpected query parameters");

    unexpectedParameters
      .forEach(queryParameter -> log.debug("\"{}\"", queryParameter));

    ClientErrorResponse.badRequest(routingContext.response(),
      format("Unexpected query string parameters: %s",
        String.join(",", unexpectedParameters)));
  }

  private CompletableFuture<JsonObject> preProcessBody(JsonObject oldBody, JsonObject newBody) {
    CompletableFuture<JsonObject> resultJsonFuture = CompletableFuture.completedFuture(newBody);

    if(recordPreProcessors != null && recordPreProcessors.stream().noneMatch(Objects::isNull)){
      for (BiFunction<JsonObject, JsonObject, CompletableFuture<JsonObject>> preProcessor:
        recordPreProcessors) {
        resultJsonFuture = resultJsonFuture.thenCompose(previous -> preProcessor.apply(oldBody, newBody));
      }
      return resultJsonFuture;
    }
    return CompletableFuture.completedFuture(newBody);
  }

  private boolean isContainsQueryParameter(String queryParameter) {
    String query = StringUtils.substringBefore(queryParameter, "=");
    return this.additionalQueryParameters.contains(query);
  }
}
