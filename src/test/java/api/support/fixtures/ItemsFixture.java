package api.support.fixtures;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.folio.circulation.domain.ItemStatus;
import api.support.http.IndividualResource;

import api.support.builders.HoldingBuilder;
import api.support.builders.InstanceBuilder;
import api.support.builders.ItemBuilder;
import api.support.http.ItemResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class ItemsFixture {
  private final ResourceClient itemsClient;
  private final ResourceClient holdingsClient;
  private final ResourceClient instancesClient;
  private final MaterialTypesFixture materialTypesFixture;
  private final LoanTypesFixture loanTypesFixture;
  private final LocationsFixture locationsFixture;
  private final RecordCreator instanceTypeRecordCreator;
  private final RecordCreator contributorNameTypeRecordCreator;

  public ItemsFixture(
    MaterialTypesFixture materialTypesFixture,
    LoanTypesFixture loanTypesFixture,
    LocationsFixture locationsFixture,
    ResourceClient instanceTypesClient,
    ResourceClient contributorNameTypesClient) {

    itemsClient = ResourceClient.forItems();
    holdingsClient = ResourceClient.forHoldings();
    instancesClient = ResourceClient.forInstances();
    this.materialTypesFixture = materialTypesFixture;
    this.loanTypesFixture = loanTypesFixture;
    this.locationsFixture = locationsFixture;

    instanceTypeRecordCreator = new RecordCreator(instanceTypesClient,
      instanceType -> getProperty(instanceType, "name"));

    contributorNameTypeRecordCreator = new RecordCreator(
      contributorNameTypesClient, nameType -> getProperty(nameType, "name"));
  }

  public void cleanUp() {

    //TODO: Also clean up created instances, holdings record and items
    instanceTypeRecordCreator.cleanUp();
    contributorNameTypeRecordCreator.cleanUp();
  }

  public ItemResource basedUponDunkirk(
    Function<HoldingBuilder, HoldingBuilder> additionalHoldingsRecordProperties,
    Function<InstanceBuilder, InstanceBuilder> additionalInstanceProperties,
    Function<ItemBuilder, ItemBuilder> additionalItemProperties) {

    return applyAdditionalProperties(
      additionalHoldingsRecordProperties,
      additionalInstanceProperties,
      additionalItemProperties,
      InstanceExamples.basedUponDunkirk(booksInstanceTypeId(), getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponDunkirk(materialTypesFixture.videoRecording().getId(),
        loanTypesFixture.canCirculate().getId())
    );
  }

  public ItemResource basedUponDunkirk() {
    return basedUponDunkirk(identity(), identity(), identity());
  }

  public IndividualResource basedUponDunkirkWithCustomHoldingAndLocation(UUID holdingsId, UUID locationId) {
    JsonObject item1 = ItemExamples.basedUponDunkirk(
        materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId())
      .forHolding(holdingsId)
      .available()
      .withTemporaryLocation(locationId)
      .create();

    return itemsClient.create(item1);
  }

  public IndividualResource createItemWithHoldingsAndLocation(UUID holdingsId, UUID locationId) {
    JsonObject item = ItemExamples.basedUponDunkirk(UUID.randomUUID(), loanTypesFixture.canCirculate().getId())
      .forHolding(holdingsId)
      .available()
      .withBarcode(UUID.randomUUID().toString())
      .withTemporaryLocation(locationId)
      .withMaterialType(materialTypesFixture.videoRecording().getId())
      .create();

    return itemsClient.create(item);
  }

  public IndividualResource basedUponDunkirkWithCustomHoldingAndLocationAndCheckedOut(UUID holdingsId, UUID locationId) {

    JsonObject item1 = ItemExamples.basedUponDunkirk(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId())
      .forHolding(holdingsId)
      .checkOut()
      .withTemporaryLocation(locationId)
      .create();

    return itemsClient.create(item1);
  }
  public IndividualResource basedUponDunkirkWithCustomHoldingAndLocationAndStatusInProcess(UUID holdingsId, UUID locationId) {

    JsonObject item1 = ItemExamples.basedUponDunkirk(materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId())
      .forHolding(holdingsId)
      .inProcess()
      .withTemporaryLocation(locationId)
      .create();

    return itemsClient.create(item1);
  }

  public ItemResource basedUponSmallAngryPlanet() {
    return basedUponSmallAngryPlanet(identity());
  }

  public ItemResource basedUponSmallAngryPlanet(String barcode) {
    return basedUponSmallAngryPlanet(item -> item.withBarcode(barcode));
  }

  public ItemResource basedUponSmallAngryPlanet(ItemBuilder itemBuilder,
    HoldingBuilder holdingBuilder) {

    return basedUponSmallAngryPlanet(
      holdings -> holdingBuilder,
      identity(),
      item -> itemBuilder);
  }

  public ItemResource basedUponSmallAngryPlanet(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties) {

    return basedUponSmallAngryPlanet(
      identity(),
      additionalItemProperties);
  }

  public ItemResource basedUponSmallAngryPlanet(
    Function<HoldingBuilder, HoldingBuilder> additionalHoldingsRecordProperties,
    Function<ItemBuilder, ItemBuilder> additionalItemProperties) {

    return basedUponSmallAngryPlanet(additionalHoldingsRecordProperties,
      identity(), additionalItemProperties);
  }

  public ItemResource basedUponSmallAngryPlanet(
    Function<HoldingBuilder, HoldingBuilder> additionalHoldingsRecordProperties,
    Function<InstanceBuilder, InstanceBuilder> additionalInstanceProperties,
    Function<ItemBuilder, ItemBuilder> additionalItemProperties) {

    return applyAdditionalProperties(
      additionalHoldingsRecordProperties,
      additionalInstanceProperties,
      additionalItemProperties,
      InstanceExamples.basedUponSmallAngryPlanet(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponSmallAngryPlanet(materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId()));
  }

  public void basedUponSmallAngryPlanetWithNoItem(
    Function<HoldingBuilder, HoldingBuilder> additionalHoldingsRecordProperties,
    Function<InstanceBuilder, InstanceBuilder> additionalInstanceProperties) {

    applyAdditionalProperties(
      additionalHoldingsRecordProperties,
      additionalInstanceProperties,
      InstanceExamples.basedUponSmallAngryPlanet(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings());
  }

  public ItemResource basedUponSmallAngryPlanet(
    Function<HoldingBuilder, HoldingBuilder> additionalHoldingsRecordProperties,
    Function<InstanceBuilder, InstanceBuilder> additionalInstanceProperties,
    Function<ItemBuilder, ItemBuilder> additionalItemProperties,
    String barcode) {

    return applyAdditionalProperties(
      additionalHoldingsRecordProperties,
      additionalInstanceProperties,
      additionalItemProperties,
      InstanceExamples.basedUponSmallAngryPlanet(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponSmallAngryPlanet(materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId(), barcode));
  }

  public ItemResource basedUponNod() {
    return basedUponNod(identity());
  }

  public ItemResource basedUponNod(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties) {

    return applyAdditionalProperties(
      identity(),
      identity(),
      additionalItemProperties,
      InstanceExamples.basedUponNod(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponNod(materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId()));
  }

  public ItemResource basedUponTemeraire() {
    return basedUponTemeraire(identity());
  }

  public ItemResource basedUponTemeraire(
    Function<HoldingBuilder, HoldingBuilder> additionalHoldingsRecordProperties,
    Function<ItemBuilder, ItemBuilder> additionalItemProperties) {

    return applyAdditionalProperties(
      additionalHoldingsRecordProperties,
      identity(),
      additionalItemProperties,
      InstanceExamples.basedUponTemeraire(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponTemeraire(materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId()));
  }

  public ItemResource basedUponTemeraire(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties) {

    return basedUponTemeraire(identity(), additionalItemProperties);
  }

  public ItemResource basedUponUprooted() {

    return basedUponUprooted(identity());
  }

  public ItemResource basedUponUprooted(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties) {

    return applyAdditionalProperties(
      identity(),
      identity(),
      additionalItemProperties,
      InstanceExamples.basedUponUprooted(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponUprooted(materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId()));
  }

  public ItemResource basedUponInterestingTimes() {

    return basedUponInterestingTimes(identity());
  }

  public ItemResource basedUponInterestingTimes(
    Function<ItemBuilder, ItemBuilder> additionalItemProperties) {

    return applyAdditionalProperties(
      identity(),
      identity(),
      additionalItemProperties,
      InstanceExamples.basedUponInterestingTimes(booksInstanceTypeId(),
        getPersonalContributorNameTypeId()),
      thirdFloorHoldings(),
      ItemExamples.basedUponInterestingTimes(materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId()));
  }

  private ItemResource applyAdditionalProperties(
    Function<HoldingBuilder, HoldingBuilder> additionalHoldingsRecordProperties,
    Function<InstanceBuilder, InstanceBuilder> additionalInstanceProperties,
    Function<ItemBuilder, ItemBuilder> additionalItemProperties,
    InstanceBuilder instanceBuilder,
    HoldingBuilder holdingsRecordBuilder,
    ItemBuilder itemBuilder) {

    return create(
      additionalInstanceProperties.apply(instanceBuilder),
      additionalHoldingsRecordProperties.apply(holdingsRecordBuilder),
      additionalItemProperties.apply(itemBuilder));
  }

  private void applyAdditionalProperties(
    Function<HoldingBuilder, HoldingBuilder> additionalHoldingsRecordProperties,
    Function<InstanceBuilder, InstanceBuilder> additionalInstanceProperties,
    InstanceBuilder instanceBuilder, HoldingBuilder holdingsRecordBuilder) {

    createHoldingAndInstanceWithNoItem(
      additionalInstanceProperties.apply(instanceBuilder),
      additionalHoldingsRecordProperties.apply(holdingsRecordBuilder));
  }

  private ItemResource create(
    InstanceBuilder instanceBuilder,
    HoldingBuilder holdingsRecordBuilder,
    ItemBuilder itemBuilder) {

    IndividualResource instance = instancesClient.create(
      instanceBuilder.withInstanceTypeId(booksInstanceTypeId()));

    IndividualResource holding = holdingsClient.create(
      holdingsRecordBuilder.forInstance(instance.getId()));

    final IndividualResource item = itemsClient.create(
      itemBuilder.forHolding(holding.getId())
        .create());

    return new ItemResource(item, holding, instance);
  }

  private void createHoldingAndInstanceWithNoItem(
    InstanceBuilder instanceBuilder,
    HoldingBuilder holdingsRecordBuilder) {

    IndividualResource instance = instancesClient.create(
      instanceBuilder.withInstanceTypeId(booksInstanceTypeId()));

    holdingsClient.create(holdingsRecordBuilder.forInstance(instance.getId()));
  }

  public HoldingBuilder thirdFloorHoldings() {

    return new HoldingBuilder()
      .withPermanentLocation(locationsFixture.thirdFloor())
      .withNoTemporaryLocation()
      .withCallNumber("123456")
      .withCallNumberPrefix("PREFIX")
      .withCallNumberSuffix("SUFFIX");
  }

  public IndividualResource setupDeclaredLostItem() {
    IndividualResource declaredLostItem = basedUponSmallAngryPlanet(ItemBuilder::declaredLost);
    assertThat(declaredLostItem.getResponse().getJson().getJsonObject("status").getString("name"), is(ItemStatus.DECLARED_LOST.getValue()));

    return declaredLostItem;
  }

  public HoldingBuilder applyCallNumberHoldings(
    String callNumber,
    String callNumberPrefix,
    String callNumberSuffix,
    List<String> copyNumbers) {

    return new HoldingBuilder()
      .withPermanentLocation(locationsFixture.thirdFloor())
      .withNoTemporaryLocation()
      .withCallNumber(callNumber)
      .withCallNumberPrefix(callNumberPrefix)
      .withCallNumberSuffix(callNumberSuffix)
      .withCopyNumbers(copyNumbers);
  }

  public InstanceBuilder instanceBasedUponSmallAngryPlanet() {
    return InstanceExamples.basedUponSmallAngryPlanet(booksInstanceTypeId(),
      getPersonalContributorNameTypeId());
  }

  public List<ItemResource> createMultipleItemsForTheSameInstance(int size) {
    return createMultipleItemForTheSameInstance(size,
      new ArrayList<>(Collections.nCopies(size, identity())));
  }

  //New item is created for different additional properties
  public List<ItemResource> createMultipleItemForTheSameInstance(int size,
    List<Function<ItemBuilder, ItemBuilder>> itemAdditionalProperties) {
    if (itemAdditionalProperties.size() != size) {
      throw new AssertionError("Number of item additional properties should be equal to the size param");
    }
    UUID instanceId = UUID.randomUUID();
    InstanceBuilder sapInstanceBuilder = instanceBasedUponSmallAngryPlanet()
      .withId(instanceId);

    return IntStream.range(0, size)
      .mapToObj(num -> basedUponSmallAngryPlanet(
        holdingsBuilder -> holdingsBuilder.forInstance(instanceId),
        instanceBuilder -> sapInstanceBuilder,
        itemBuilder -> itemAdditionalProperties.get(num)
          .apply(itemBuilder.withBarcode("0000" + num))))
      .collect(Collectors.toList());
  }

  public List<ItemResource> createMultipleItemsOnePerInstance(int size,
    List<Function<ItemBuilder, ItemBuilder>> itemAdditionalProperties) {

    if (itemAdditionalProperties.size() != size) {
      throw new AssertionError("Number of item additional properties should be equal to the size param");
    }

    return IntStream.range(0, size)
      .mapToObj(num -> basedUponSmallAngryPlanet(
        identity(),
        identity(),
        itemBuilder -> itemAdditionalProperties.get(num)
          .apply(itemBuilder.withBarcode("0000" + num))))
      .toList();
  }

  private UUID booksInstanceTypeId() {

    final JsonObject booksInstanceType = new JsonObject();

    write(booksInstanceType, "name", "Books");
    write(booksInstanceType, "code", "BO");
    write(booksInstanceType, "source", "tests");

    return instanceTypeRecordCreator.createIfAbsent(booksInstanceType).getId();
  }

  private UUID getPersonalContributorNameTypeId() {

    final JsonObject personalName = new JsonObject();

    write(personalName, "name", "Personal name");

    return contributorNameTypeRecordCreator.createIfAbsent(personalName).getId();
  }

  public Function<ItemBuilder, ItemBuilder> addCallNumberStringComponents(String prefix) {
    return itemBuilder -> itemBuilder
      .withCallNumber(prefix + "itCn", prefix + "itCnPrefix", prefix + "itCnSuffix")
      .withEnumeration(prefix + "enumeration1")
      .withChronology(prefix + "chronology")
      .withVolume(prefix + "vol.1")
      .withDisplaySummary(prefix + "displaySummary");
  }

  public Function<ItemBuilder, ItemBuilder> addCallNumberStringComponents() {
    return addCallNumberStringComponents("");
  }

  public ItemResource getById(UUID id) {
    return new ItemResource(new IndividualResource(itemsClient.getById(id)), null, null);
  }
}
