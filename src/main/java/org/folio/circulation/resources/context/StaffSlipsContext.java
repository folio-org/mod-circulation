package org.folio.circulation.resources.context;

import java.util.Collection;

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
  private Collection<Item> items;
}
