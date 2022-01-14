package org.folio.circulation.services.support;

import java.util.HashMap;
import java.util.Map;

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
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;

@Getter
@Setter
@With
@AllArgsConstructor
@NoArgsConstructor
public class ItemsInTransitReportContext {
  // All maps contain (id -> entity) pairs
  // All entities need to be fetched in batches

  // Items and related records
  private Map<String, Item> items;
  private Map<String, Holdings> holdingsRecords;
  private Map<String, Instance> instances;
  private Map<String, Location> locations;

  // Loans
  private Map<String, Loan> loans;

  // Requests
  private Map<String, Request> requests = new HashMap<>();
  private Map<String, User> users;
  private Map<String, PatronGroup> patronGroups;

  // Service points are needed for items, loans and requests
  private Map<String, ServicePoint> servicePoints;
}
