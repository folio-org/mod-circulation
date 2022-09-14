package org.folio.circulation.domain;

import java.util.Collection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor
@AllArgsConstructor
@With
@Getter
public class ActualCostRecordInstance {
  private String id;
  private String title;
  private Collection<ActualCostRecordIdentifier> identifiers;
}
