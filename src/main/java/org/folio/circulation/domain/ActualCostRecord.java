package org.folio.circulation.domain;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.With;

@NoArgsConstructor(force = true)
@AllArgsConstructor
@With
@Getter
public class ActualCostRecord {
  private final String id;
  private final Status status;
  private final ItemLossType lossType;
  private final ZonedDateTime lossDate;
  private final ZonedDateTime expirationDate;
  private final ActualCostRecordUser user;
  private final ActualCostRecordLoan loan;
  private final ActualCostRecordItem item;
  private final ActualCostRecordInstance instance;
  private final ActualCostRecordFeeFine feeFine;
  private final ZonedDateTime creationDate;

  @NoArgsConstructor(force = true)
  @AllArgsConstructor
  @With
  @Getter
  public static class ActualCostRecordUser {
    private final String id;
    private final String barcode;
    private final String firstName;
    private final String lastName;
    private final String middleName;
    private final String patronGroupId;
    private final String patronGroup;
  }

  @NoArgsConstructor(force = true)
  @AllArgsConstructor
  @With
  @Getter
  public static class ActualCostRecordLoan {
    private final String id;
  }

  @NoArgsConstructor(force = true)
  @AllArgsConstructor
  @With
  @Getter
  public static class ActualCostRecordItem {
    private final String id;
    private final String barcode;
    private final String materialTypeId;
    private final String materialType;
    private final String permanentLocationId;
    private final String permanentLocation;
    private final String effectiveLocationId;
    private final String effectiveLocation;
    private final String loanTypeId;
    private final String loanType;
    private final String holdingsRecordId;
    private final CallNumberComponents effectiveCallNumberComponents;
    private final String volume;
    private final String enumeration;
    private final String chronology;
    private final String displaySummary;
    private final String copyNumber;
  }

  @NoArgsConstructor(force = true)
  @AllArgsConstructor
  @With
  @Getter
  public static class ActualCostRecordInstance {
    private final String id;
    private final String title;
    private final Collection<ActualCostRecordIdentifier> identifiers;
    private final Collection<Contributor> contributors;
  }

  @NoArgsConstructor(force = true)
  @AllArgsConstructor
  @With
  @Getter
  public static class ActualCostRecordFeeFine {
    private final String accountId;
    private final String ownerId;
    private final String owner;
    private final String typeId;
    private final String type;
  }

  @NoArgsConstructor(force = true)
  @AllArgsConstructor
  @With
  @Getter
  public static class ActualCostRecordIdentifier {
    private final String value;
    private final String identifierTypeId;
    private final String identifierType;

    public static ActualCostRecordIdentifier fromRepresentation(JsonObject representation) {
      return new ActualCostRecordIdentifier()
        .withIdentifierTypeId(representation.getString("identifierTypeId"))
        .withIdentifierType(representation.getString("identifierType"))
        .withValue(representation.getString("value"));
    }
  }

  @Getter
  @RequiredArgsConstructor
  public enum Status {
    OPEN("Open"),
    BILLED("Billed"),
    EXPIRED("Expired"),
    CANCELLED("Cancelled");

    private final String value;

    public static Status from(String statusValue) {
      return Arrays.stream(values())
        .filter(status -> StringUtils.equals(status.getValue(), statusValue))
        .findFirst()
        .orElse(null);
    }
  }
}
