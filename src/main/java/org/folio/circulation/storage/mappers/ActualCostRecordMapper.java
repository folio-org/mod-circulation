package org.folio.circulation.storage.mappers;

import static org.folio.circulation.domain.representations.CallNumberComponentsRepresentation.createCallNumberComponents;
import static org.folio.circulation.domain.representations.ContributorsToNamesMapper.mapContributorNamesToJson;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.Collection;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordFeeFine;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordIdentifier;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordInstance;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordItem;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordLoan;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordUser;
import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.ItemLossType;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ActualCostRecordMapper {

  private ActualCostRecordMapper() {
  }

  public static JsonObject toJson(ActualCostRecord actualCostRecord) {
    JsonObject json = new JsonObject();
    write(json, "id", actualCostRecord.getId());
    write(json, "lossType", actualCostRecord.getLossType().getValue());
    write(json, "lossDate", actualCostRecord.getLossDate());
    write(json, "expirationDate", actualCostRecord.getExpirationDate());
    write(json, "status", actualCostRecord.getStatus().getValue());

    JsonObject userJson = new JsonObject();
    ActualCostRecordUser user = actualCostRecord.getUser();
    if (user != null) {
      write(userJson, "id", user.getId());
      write(userJson, "barcode", user.getBarcode());
      write(userJson, "firstName", user.getFirstName());
      write(userJson, "lastName", user.getLastName());
      write(userJson, "middleName", user.getMiddleName());
      write(userJson, "patronGroupId", user.getPatronGroupId());
      write(userJson, "patronGroup", user.getPatronGroup());

      write(json, "user", userJson);
    }

    JsonObject loanJson = new JsonObject();
    ActualCostRecordLoan loan = actualCostRecord.getLoan();
    if (loan != null) {
      write(loanJson, "id", loan.getId());
      write(json, "loan", loanJson);
    }

    JsonObject itemJson = new JsonObject();
    ActualCostRecordItem item = actualCostRecord.getItem();
    if (item != null) {
      write(itemJson, "id", item.getId());
      write(itemJson, "barcode", item.getBarcode());
      write(itemJson, "materialTypeId", item.getMaterialTypeId());
      write(itemJson, "materialType", item.getMaterialType());
      write(itemJson, "permanentLocationId", item.getPermanentLocationId());
      write(itemJson, "permanentLocation", item.getPermanentLocation());
      write(itemJson, "effectiveLocationId", item.getEffectiveLocationId());
      write(itemJson, "effectiveLocation", item.getEffectiveLocation());
      write(itemJson, "loanTypeId", item.getLoanTypeId());
      write(itemJson, "loanType", item.getLoanType());
      write(itemJson, "holdingsRecordId", item.getHoldingsRecordId());
      write(itemJson, "volume", item.getVolume());
      write(itemJson, "enumeration", item.getEnumeration());
      write(itemJson, "chronology", item.getChronology());
      write(itemJson, "displaySummary", item.getDisplaySummary());
      write(itemJson, "copyNumber", item.getCopyNumber());
      write(itemJson, "effectiveCallNumberComponents",
        createCallNumberComponents(item.getEffectiveCallNumberComponents()));

      write(json, "item", itemJson);
    }

    JsonObject instanceJson = new JsonObject();
    ActualCostRecordInstance instance = actualCostRecord.getInstance();
    if (item != null) {
      write(instanceJson, "id", instance.getId());
      write(instanceJson, "title", instance.getTitle());
      write(instanceJson, "identifiers", mapIdentifiersToJson(instance.getIdentifiers()));
      write(instanceJson, "contributors", mapContributorNamesToJson(instance.getContributors()));

      write(json, "instance", instanceJson);
    }

    JsonObject feeFineJson = new JsonObject();
    ActualCostRecordFeeFine feeFine = actualCostRecord.getFeeFine();
    if (feeFine != null) {
      if (feeFine.getAccountId() != null) {
        write(feeFineJson, "accountId", feeFine.getAccountId());
      }

      write(feeFineJson, "ownerId", feeFine.getOwnerId());
      write(feeFineJson, "owner", feeFine.getOwner());
      write(feeFineJson, "typeId", feeFine.getTypeId());
      write(feeFineJson, "type", feeFine.getType());

      write(json, "feeFine", feeFineJson);
    }

    return json;
  }

  public static ActualCostRecord toDomain(JsonObject representation) {
    if (representation == null ) {
      return null;
    }

    JsonObject user = getObjectProperty(representation, "user");
    JsonObject loan = getObjectProperty(representation, "loan");
    JsonObject item = getObjectProperty(representation, "item");
    JsonObject instance = getObjectProperty(representation, "instance");
    JsonObject feeFine = getObjectProperty(representation, "feeFine");

    return new ActualCostRecord(getProperty(representation, "id"),
      ActualCostRecord.Status.from(getProperty(representation, "status")),
      ItemLossType.from(getProperty(representation, "lossType")),
      getDateTimeProperty(representation, "lossDate"),
      getDateTimeProperty(representation, "expirationDate"),
      new ActualCostRecordUser()
        .withId(getProperty(user, "id"))
        .withBarcode(getProperty(user, "barcode"))
        .withFirstName(getProperty(user, "firstName"))
        .withLastName(getProperty(user, "lastName"))
        .withMiddleName(getProperty(user, "middleName"))
        .withPatronGroup(getProperty(user, "patronGroup"))
        .withPatronGroupId(getProperty(user, "patronGroupId")),
      new ActualCostRecordLoan()
        .withId(getProperty(loan, "id")),
      new ActualCostRecordItem()
        .withId(getProperty(item, "id"))
        .withBarcode(getProperty(item, "barcode"))
        .withMaterialTypeId(getProperty(item, "materialTypeId"))
        .withMaterialType(getProperty(item, "materialType"))
        .withPermanentLocationId(getProperty(item, "permanentLocationId"))
        .withPermanentLocation(getProperty(item, "permanentLocation"))
        .withEffectiveLocationId(getProperty(item, "effectiveLocationId"))
        .withEffectiveLocation(getProperty(item, "effectiveLocation"))
        .withLoanTypeId(getProperty(item, "loanTypeId"))
        .withLoanType(getProperty(item, "loanType"))
        .withHoldingsRecordId(getProperty(item, "holdingsRecordId"))
        .withVolume(getProperty(item, "volume"))
        .withEnumeration(getProperty(item, "enumeration"))
        .withChronology(getProperty(item, "chronology"))
        .withDisplaySummary(getProperty(item, "displaySummary"))
        .withCopyNumber(getProperty(item, "copyNumber"))
        .withEffectiveCallNumberComponents(CallNumberComponents.fromItemJson(item)),
      new ActualCostRecordInstance()
        .withId(getProperty(instance, "id"))
        .withTitle(getProperty(instance, "title"))
        .withIdentifiers(getArrayProperty(instance, "identifiers").stream()
          .map(JsonObject.class::cast)
          .map(ActualCostRecordIdentifier::fromRepresentation)
          .toList())
        .withContributors(getArrayProperty(instance, "contributors").stream()
          .map(JsonObject.class::cast)
          .map(new ContributorMapper()::toDomain)
          .toList()),
      new ActualCostRecordFeeFine()
        .withAccountId(getProperty(feeFine, "accountId"))
        .withOwnerId(getProperty(feeFine, "ownerId"))
        .withOwner(getProperty(feeFine, "owner"))
        .withTypeId(getProperty(feeFine, "typeId"))
        .withType(getProperty(feeFine, "type")),
      getNestedDateTimeProperty(representation, "metadata", "createdDate"));
  }

  private static JsonArray mapIdentifiersToJson(Collection<ActualCostRecordIdentifier> identifiers) {
    return new JsonArray(identifiers.stream()
      .map(JsonObject::mapFrom)
      .toList());
  }
}
