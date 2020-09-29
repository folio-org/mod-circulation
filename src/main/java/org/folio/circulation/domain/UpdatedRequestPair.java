package org.folio.circulation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class UpdatedRequestPair {
  final Request original;
  final Request updated;
}
