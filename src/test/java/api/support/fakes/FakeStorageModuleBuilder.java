package api.support.fakes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import api.APITestSuite;

public class FakeStorageModuleBuilder {
  private final String rootPath;
  private final String collectionPropertyName;
  private final String tenantId;
  private final Collection<String> requiredProperties;
  private final Collection<String> uniqueProperties;
  private final Collection<String> disallowedProperties;
  private final Boolean hasCollectionDelete;
  private final String recordName;
  private final Boolean includeChangeMetadata;

  FakeStorageModuleBuilder() {
    this(
      null,
      null,
      APITestSuite.TENANT_ID,
      new ArrayList<>(),
      new ArrayList<>(),
      true,
      "",
      new ArrayList<>(),
      false);
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
    Boolean includeChangeMetadata) {

    this.rootPath = rootPath;
    this.collectionPropertyName = collectionPropertyName;
    this.tenantId = tenantId;
    this.requiredProperties = requiredProperties;
    this.disallowedProperties = disallowedProperties;
    this.hasCollectionDelete = hasCollectionDelete;
    this.recordName = recordName;
    this.uniqueProperties = uniqueProperties;
    this.includeChangeMetadata = includeChangeMetadata;
  }

  public FakeStorageModule create() {
    return new FakeStorageModule(rootPath, collectionPropertyName, tenantId,
      requiredProperties, hasCollectionDelete, recordName, uniqueProperties,
      disallowedProperties, includeChangeMetadata);
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
      this.includeChangeMetadata);
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
      this.includeChangeMetadata);
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
      this.includeChangeMetadata);
  }

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
      this.includeChangeMetadata);
    }

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
      this.includeChangeMetadata);
  }

  FakeStorageModuleBuilder withUniqueProperties(String... uniqueProperties) {
    return withUniqueProperties(Arrays.asList(uniqueProperties));
  }

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
      this.includeChangeMetadata);
  }

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
      this.includeChangeMetadata);
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
      true);
  }
}
