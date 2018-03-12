package org.folio.circulation.api.fakes;

import org.folio.circulation.api.APITestSuite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class FakeStorageModuleBuilder {
  private final String rootPath;
  private final String collectionPropertyName;
  private final String tenantId;
  private final Collection<String> requiredProperties;
  private final Collection<String> uniqueProperties;
  private final Boolean hasCollectionDelete;
  private final String recordName;

  public FakeStorageModuleBuilder() {
    this(null, null, APITestSuite.TENANT_ID, new ArrayList<>(), true, "",
      new ArrayList<>());
  }

  private FakeStorageModuleBuilder(
    String rootPath,
    String collectionPropertyName,
    String tenantId,
    Collection<String> requiredProperties,
    boolean hasCollectionDelete,
    String recordName,
    Collection<String> uniqueProperties) {

    this.rootPath = rootPath;
    this.collectionPropertyName = collectionPropertyName;
    this.tenantId = tenantId;
    this.requiredProperties = requiredProperties;
    this.hasCollectionDelete = hasCollectionDelete;
    this.recordName = recordName;
    this.uniqueProperties = uniqueProperties;
  }

  public FakeStorageModule create() {
    return new FakeStorageModule(rootPath, collectionPropertyName, tenantId,
      requiredProperties, hasCollectionDelete, recordName, uniqueProperties);
  }

  public FakeStorageModuleBuilder withRootPath(String rootPath) {

    String newCollectionPropertyName = collectionPropertyName == null
      ? rootPath.substring(rootPath.lastIndexOf("/") + 1)
      : collectionPropertyName;

    return new FakeStorageModuleBuilder(
      rootPath,
      newCollectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties);
  }

  public FakeStorageModuleBuilder withCollectionPropertyName(String collectionPropertyName) {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties);
  }

  public FakeStorageModuleBuilder withRecordName(String recordName) {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.hasCollectionDelete,
      recordName,
      this.uniqueProperties);
  }

  public FakeStorageModuleBuilder withRequiredProperties(Collection<String> requiredProperties) {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      requiredProperties,
      this.hasCollectionDelete,
      this.recordName,
      this.uniqueProperties);
  }

  public FakeStorageModuleBuilder withRequiredProperties(String... requiredProperties) {
    return withRequiredProperties(Arrays.asList(requiredProperties));
  }

  public FakeStorageModuleBuilder withUniqueProperties(Collection<String> uniqueProperties) {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.hasCollectionDelete,
      this.recordName,
      uniqueProperties);
  }

  public FakeStorageModuleBuilder withUniqueProperties(String... uniqueProperties) {
    return withUniqueProperties(Arrays.asList(uniqueProperties));
  }

  public FakeStorageModuleBuilder disallowCollectionDelete() {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      false,
      this.recordName,
      this.uniqueProperties);
  }
}
