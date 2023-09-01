package org.folio.circulation.infrastructure.storage.requests;

import java.util.Set;

import org.folio.circulation.domain.Item;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RequestPolicyContext {
  private final String requestPolicyId;
  private final Set<Item> items;
}
