package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyFetcher.*;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.Collection;

import org.folio.circulation.support.json.JsonPropertyFetcher;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.With;

@NoArgsConstructor
@AllArgsConstructor
@With
@Getter
public class ActualCostRecord {
  private String id;
  private String userId;
  private String userBarcode;
  private String loanId;
  private LossType lossType;
  private String dateOfLoss;
  private String title;
  private Collection<Identifier> identifiers;
  private String itemBarcode;
  private String loanType;
  private String effectiveCallNumber;
  private String permanentItemLocation;
  private String feeFineOwnerId;
  private String feeFineOwner;
  private String feeFineTypeId;
  private String feeFineType;
}
