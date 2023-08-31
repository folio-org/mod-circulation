package org.folio.circulation.infrastructure.storage.requests;

import java.util.List;

import org.folio.circulation.domain.Item;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RequestPolicyContext {
  private final String requestPolicyId;
  private final List<Item> items;
}
