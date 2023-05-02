package org.folio.circulation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
public class UpdatedRequestPair {
  final Request original;
  final Request updated;
}
