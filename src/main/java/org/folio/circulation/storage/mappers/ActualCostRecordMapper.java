package org.folio.circulation.storage.mappers;

import static org.folio.circulation.domain.representations.CallNumberComponentsRepresentation.createCallNumberComponents;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.stream.Collectors;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordFeeFine;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordIdentifier;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordInstance;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordItem;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordLoan;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordUser;
import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.ItemLossType;

import io.vertx.core.json.JsonObject;

public class ActualCostRecordMapper {

  private ActualCostRecordMapper() {
  }

  public static JsonObject toJson(ActualCostRecord actualCostRecord) {
    JsonObject json = new JsonObject();
    write(json, "lossType", actualCostRecord.getLossType().getValue());
    write(json, "lossDate", actualCostRecord.getLossDate());
    write(json, "expirationDate", actualCostRecord.getExpirationDate());

    JsonObject userJson = new JsonObject();
    ActualCostRecordUser user = actualCostRecord.getUser();
    if (user != null) {
      write(userJson, "id", user.getId());
      write(userJson, "barcode", user.getBarcode());
      write(userJson, "firstName", user.getFirstName());
      write(userJson, "lastName", user.getLastName());
      write(userJson, "middleName", user.getMiddleName());

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
      write(itemJson, "loanTypeId", item.getLoanTypeId());
      write(itemJson, "loanType", item.getLoanType());
      write(itemJson, "holdingsRecordId", item.getHoldingsRecordId());
      write(itemJson, "effectiveCallNumberComponents",
        createCallNumberComponents(item.getEffectiveCallNumberComponents()));

      write(json, "item", itemJson);
    }

    JsonObject instanceJson = new JsonObject();
    ActualCostRecordInstance instance = actualCostRecord.getInstance();
    if (item != null) {
      write(instanceJson, "id", instance.getId());
      write(instanceJson, "title", instance.getTitle());
      write(instanceJson, "identifiers", instance.getIdentifiers());

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
      ItemLossType.from(getProperty(representation, "lossType")),
      getDateTimeProperty(representation, "lossDate"),
      getDateTimeProperty(representation, "expirationDate"),
      new ActualCostRecordUser()
        .withId(getProperty(user, "id"))
        .withBarcode(getProperty(user, "barcode"))
        .withFirstName(getProperty(user, "firstName"))
        .withLastName(getProperty(user, "lastName"))
        .withMiddleName(getProperty(user, "middleName")),
      new ActualCostRecordLoan()
        .withId(getProperty(loan, "id")),
      new ActualCostRecordItem()
        .withId(getProperty(item, "id"))
        .withBarcode(getProperty(item, "barcode"))
        .withMaterialTypeId(getProperty(item, "materialTypeId"))
        .withMaterialType(getProperty(item, "materialType"))
        .withPermanentLocationId(getProperty(item, "permanentItemLocationId"))
        .withPermanentLocation(getProperty(item, "permanentItemLocation"))
        .withLoanTypeId(getProperty(item, "loanTypeId"))
        .withLoanType(getProperty(item, "loanType"))
        .withHoldingsRecordId(getProperty(item, "holdingsRecordId"))
        .withEffectiveCallNumberComponents(CallNumberComponents.fromItemJson(item)),
      new ActualCostRecordInstance()
        .withId(getProperty(instance, "id"))
        .withTitle(getProperty(instance, "title"))
        .withIdentifiers(getArrayProperty(instance, "identifiers").stream()
          .map(JsonObject.class::cast)
          .map(ActualCostRecordIdentifier::fromRepresentation)
          .collect(Collectors.toList())),
      new ActualCostRecordFeeFine()
        .withAccountId(getProperty(feeFine, "accountId"))
        .withOwnerId(getProperty(feeFine, "ownerId"))
        .withOwner(getProperty(feeFine, "owner"))
        .withOwnerId(getProperty(feeFine, "typeId"))
        .withOwner(getProperty(feeFine, "type")),
      getNestedDateTimeProperty(representation, "metadata", "createdDate"));
  }
}
