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
  private final Boolean hasCollectionDelete;
  private final String recordName;

  public FakeStorageModuleBuilder() {
    this(null, null, APITestSuite.TENANT_ID, new ArrayList<>(), true, "");
  }

  private FakeStorageModuleBuilder(
    String rootPath,
    String collectionPropertyName,
    String tenantId,
    Collection<String> requiredProperties,
    boolean hasCollectionDelete,
    String recordName) {

    this.rootPath = rootPath;
    this.collectionPropertyName = collectionPropertyName;
    this.tenantId = tenantId;
    this.requiredProperties = requiredProperties;
    this.hasCollectionDelete = hasCollectionDelete;
    this.recordName = recordName;
  }

  public FakeStorageModule create() {
    return new FakeStorageModule(rootPath, collectionPropertyName, tenantId,
      requiredProperties, hasCollectionDelete, recordName);
  }

  public FakeStorageModuleBuilder withRootPath(String rootPath) {
    return new FakeStorageModuleBuilder(
      rootPath,
      rootPath.substring(rootPath.lastIndexOf("/") + 1),
      this.tenantId,
      this.requiredProperties,
      this.hasCollectionDelete,
      this.recordName);
  }

  public FakeStorageModuleBuilder withCollectionPropertyName(String collectionPropertyName) {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.hasCollectionDelete,
      this.recordName);
  }

  public FakeStorageModuleBuilder withRecordName(String recordName) {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      this.hasCollectionDelete,
      recordName);
  }

  public FakeStorageModuleBuilder withRequiredProperties(Collection<String> requiredProperties) {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      requiredProperties,
      this.hasCollectionDelete,
      this.recordName);
  }

  public FakeStorageModuleBuilder disallowCollectionDelete() {
    return new FakeStorageModuleBuilder(
      this.rootPath,
      this.collectionPropertyName,
      this.tenantId,
      this.requiredProperties,
      false,
      this.recordName);
  }

  public FakeStorageModuleBuilder withRequiredProperties(String... requiredProperties) {
    return withRequiredProperties(Arrays.asList(requiredProperties));
  }
}
