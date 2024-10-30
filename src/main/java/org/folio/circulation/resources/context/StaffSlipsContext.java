package org.folio.circulation.resources.context;

import java.util.Collection;
import java.util.Map;

import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@With
public class StaffSlipsContext {
  private MultipleRecords<Location> locations;
  private MultipleRecords<Request> requests;
  private MultipleRecords<Request> tlrRequests;
  private MultipleRecords<Instance> instances;
  private MultipleRecords<Holdings> holdings;
  private Collection<Item> items;
  private Map<Request, String> requestToInstanceIdMap;
  private Map<Request, Holdings> requestToHoldingMap;

}
