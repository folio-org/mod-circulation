package org.folio.circulation.services.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.PatronGroup;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

@Getter
@With
@AllArgsConstructor
public class ItemsInTransitReportContext {
  // Maps contain (entityId, entity) pairs for all entities if not stated otherwise in the comment
  // above the field

  // Items and related records
  private final Map<String, Item> items;
  private final Map<String, Holdings> holdingsRecords;
  private final Map<String, Instance> instances;
  private final Map<String, Location> locations;

  // Loans
  // key: item ID, value: loan
  private final Map<String, Loan> loans;

  // Requests
  // key: item ID, value: request
  private final Map<String, Request> requests;
  private final Map<String, User> users;
  private final Map<String, PatronGroup> patronGroups;

  // Service points are needed for items, loans and requests
  private final Map<String, ServicePoint> servicePoints;

  public ItemsInTransitReportContext() {
    items = new HashMap<>();
    holdingsRecords = new HashMap<>();
    instances = new HashMap<>();
    locations = new HashMap<>();
    loans = new HashMap<>();
    requests = new HashMap<>();
    users = new HashMap<>();
    patronGroups = new HashMap<>();
    servicePoints = new HashMap<>();
  }

  public String getInstanceId(Item item) {
    return Optional.ofNullable(item)
      .map(Item::getHoldingsRecordId)
      .map(holdingsRecords::get)
      .map(Holdings::getInstanceId)
      .orElse(null);
  }
}
