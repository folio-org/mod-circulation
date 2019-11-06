package api.support.fakes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.folio.circulation.infrastructure.serialization.JsonSchemaValidator;
import org.folio.circulation.support.Result;

import api.support.APITestContext;
import io.vertx.core.json.JsonObject;

public class FakeStorageModuleBuilder {
  private final String rootPath;
  private final String collectionPropertyName;
  private final String tenantId;
  private final Collection<String> requiredProperties;
  private final Collection<String> uniqueProperties;
  private final Collection<String> disallowedProperties;
  private final Boolean hasCollectionDelete;
  private final Boolean hasDeleteByQuery;
  private final String recordName;
  private final Boolean includeChangeMetadata;
  private final BiFunction<Collection<JsonObject>, JsonObject, Result<Object>> constraint;
  private final JsonSchemaValidator recordValidator;
  private final String updateBatchPath;
  private final Function<JsonObject, JsonObject> batchUpdatePreProcessor;
  private final List<BiFunction<JsonObject, JsonObject, CompletableFuture<JsonObject>>> recordPreProcessors;

  FakeStorageModuleBuilder() {
    this(
      null,
      null,
      APITestContext.getTenantId(),
      new ArrayList<>(),
      new ArrayList<>(),
      true,
      "",
      new ArrayList<>(),
      false,
      false,
      (c, r) -> Result.succeeded(null),
      null,
      null,
      null,
      null);
  }

  private FakeStorageModuleBuilder(
    String rootPath,
    String collectionPropertyName,
    String tenantId,
    Collection<String> requiredProperties,
    Collection<String> disallowedProperties,
    Boolean hasCollectionDelete,
    String recordName,
    Collection<String> uniqueProperties,
    Boolean hasDeleteByQuery,
    Boolean includeChangeMetadata,
    BiFunction<Collection<JsonObject>, JsonObject, Result<Object>> constraint,
    JsonSchemaValidator recordValidator,
    String updateBatchPath,
    Function<JsonObject, JsonObject> batchUpdatePreProcessor,
    List<BiFunction<JsonObject, JsonObject, CompletableFuture<JsonObject>>> recordPreProcessors) {

    this.rootPath = rootPath;
    this.collectionPropertyName = collectionPropertyName;
    this.tenantId = tenantId;
    this.requiredProperties = requiredProperties;
    this.disallowedProperties = disallowedProperties;
    this.hasCollectionDelete = hasCollectionDelete;
    this.recordName = recordName;
    this.uniqueProperties = uniqueProperties;
    this.hasDeleteByQuery = hasDeleteByQuery;
    this.includeChangeMetadata = includeChangeMetadata;
    this.constraint = constraint;
    this.recordValidator = recordValidator;
    this.updateBatchPath = updateBatchPath;
    this.batchUpdatePreProcessor = batchUpdatePreProcessor;
    this.recordPreProcessors = recordPreProcessors;
  }

  public FakeStorageModule create() {
    return new FakeStorageModule(rootPath, collectionPropertyName, tenantId,
      recordValidator, requiredProperties, hasCollectionDelete, hasDeleteByQuery,
      recordName, uniqueProperties, disallowedProperties, includeChangeMetadata,
      constraint, updateBatchPath, batchUpdatePreProcessor, recordPreProcessors);
  }

  FakeStorageModuleBuilder withRootPath(String rootPath) {
    String newCollectionPropertyName = collectionPropertyName == null
      ? rootPath.substring(rootPath.lastIndexOf("/") + 1)
      : collectionPropertyName;

    return new FakeStorageModuleBuilder(
      rootPath,
      newCollectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.disallowedProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties,
      this.hasDeleteByQuery,
      this.includeChangeMetadata,
      this.constraint,
      this.recordValidator,
      this.updateBatchPath,
      this.batchUpdatePreProcessor,
      this.recordPreProcessors);
  }

  FakeStorageModuleBuilder withCollectionPropertyName(
    String collectionPropertyName) {

    return new FakeStorageModuleBuilder(
      this.rootPath,
      collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.disallowedProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties,
      this.hasDeleteByQuery,
      this.includeChangeMetadata,
      this.constraint,
      this.recordValidator,
      this.updateBatchPath,
      this.batchUpdatePreProcessor,
      this.recordPreProcessors);

  }

  FakeStorageModuleBuilder withRecordName(String recordName) {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.disallowedProperties,
      this.hasCollectionDelete,
      recordName,
      this.uniqueProperties,
      this.hasDeleteByQuery,
      this.includeChangeMetadata,
      this.constraint,
      this.recordValidator,
      this.updateBatchPath,
      this.batchUpdatePreProcessor,
      this.recordPreProcessors);
  }

  FakeStorageModuleBuilder validateRecordsWith(JsonSchemaValidator validator) {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      requiredProperties,
      this.disallowedProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties,
      this.hasDeleteByQuery,
      this.includeChangeMetadata,
      this.constraint,
      validator,
      this.updateBatchPath,
      this.batchUpdatePreProcessor,
      this.recordPreProcessors);
  }

  @Deprecated()
  /**
   * Deprecated in favour of schema validation
   * {@link #validateRecordsWith(JsonSchemaValidator) }
   */
  private FakeStorageModuleBuilder withRequiredProperties(
    Collection<String> requiredProperties) {

    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      requiredProperties,
      this.disallowedProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties,
      this.hasDeleteByQuery,
      this.includeChangeMetadata,
      this.constraint,
      this.recordValidator,
      this.updateBatchPath,
      this.batchUpdatePreProcessor,
      this.recordPreProcessors);
  }

  @Deprecated()
  /**
   * Deprecated in favour of schema validation
   * {@link #validateRecordsWith(JsonSchemaValidator) }
   */
  FakeStorageModuleBuilder withRequiredProperties(String... requiredProperties) {
    return withRequiredProperties(Arrays.asList(requiredProperties));
  }

    private FakeStorageModuleBuilder withUniqueProperties(
      Collection<String> uniqueProperties) {

    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.disallowedProperties,
      this.hasCollectionDelete,
      this.recordName,
      uniqueProperties,
      this.hasDeleteByQuery,
      this.includeChangeMetadata,
      this.constraint,
      this.recordValidator,
      this.updateBatchPath,
      this.batchUpdatePreProcessor,
      this.recordPreProcessors);
  }

  FakeStorageModuleBuilder withUniqueProperties(String... uniqueProperties) {
    return withUniqueProperties(Arrays.asList(uniqueProperties));
  }

  @Deprecated()
  /**
   * Deprecated in favour of schema validation
   * {@link #validateRecordsWith(JsonSchemaValidator) }
   */
  private FakeStorageModuleBuilder withDisallowedProperties(
    Collection<String> disallowedProperties) {

    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      disallowedProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties,
      this.hasDeleteByQuery,
      this.includeChangeMetadata,
      this.constraint,
      this.recordValidator,
      this.updateBatchPath,
      this.batchUpdatePreProcessor,
      this.recordPreProcessors);
  }

  @Deprecated()
  /**
   * Deprecated in favour of schema validation
   * {@link #validateRecordsWith(JsonSchemaValidator) }
   */
  FakeStorageModuleBuilder withDisallowedProperties(String... disallowedProperties) {
    return withDisallowedProperties(Arrays.asList(disallowedProperties));
  }

  FakeStorageModuleBuilder disallowCollectionDelete() {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.disallowedProperties,
      false,
      this.recordName,
      this.uniqueProperties,
      this.hasDeleteByQuery,
      this.includeChangeMetadata,
      this.constraint,
      this.recordValidator,
      this.updateBatchPath,
      this.batchUpdatePreProcessor,
      this.recordPreProcessors);
  }

  FakeStorageModuleBuilder allowDeleteByQuery() {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.disallowedProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties,
      true,
      this.includeChangeMetadata,
      this.constraint,
      this.recordValidator,
      this.updateBatchPath,
      this.batchUpdatePreProcessor,
      this.recordPreProcessors);
  }

  FakeStorageModuleBuilder withChangeMetadata() {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.disallowedProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties,
      this.hasDeleteByQuery,
      true,
      this.constraint,
      this.recordValidator,
      this.updateBatchPath,
      this.batchUpdatePreProcessor,
      this.recordPreProcessors);
  }

  FakeStorageModuleBuilder withRecordConstraint(
    BiFunction<Collection<JsonObject>, JsonObject, Result<Object>> constraint) {

    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.disallowedProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties,
      this.hasDeleteByQuery,
      this.includeChangeMetadata,
      constraint,
      this.recordValidator,
      this.updateBatchPath,
      this.batchUpdatePreProcessor,
      this.recordPreProcessors);
  }

  FakeStorageModuleBuilder withBatchUpdate(String path) {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.disallowedProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties,
      this.hasDeleteByQuery,
      this.includeChangeMetadata,
      constraint,
      this.recordValidator,
      path,
      this.batchUpdatePreProcessor,
      this.recordPreProcessors);
  }

  FakeStorageModuleBuilder withBatchUpdatePreProcessor(Function<JsonObject, JsonObject> preProcessor) {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.disallowedProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties,
      this.hasDeleteByQuery,
      this.includeChangeMetadata,
      constraint,
      this.recordValidator,
      this.updateBatchPath,
      preProcessor,
      this.recordPreProcessors);
  }

  FakeStorageModuleBuilder withRecordPreProcessor(
    List<BiFunction<JsonObject, JsonObject, CompletableFuture<JsonObject>>> recordPreProcessor) {

    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.disallowedProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties,
      this.hasDeleteByQuery,
      this.includeChangeMetadata,
      this.constraint,
      this.recordValidator,
      this.updateBatchPath,
      this.batchUpdatePreProcessor,
      recordPreProcessor);
  }
}
