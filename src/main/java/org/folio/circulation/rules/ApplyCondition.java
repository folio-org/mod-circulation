package org.folio.circulation.rules;

import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.ITEM_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.LOAN_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.LOCATION_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.PATRON_TYPE_ID_NAME;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.MultiMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.With;

@Getter
@ToString
@AllArgsConstructor
public final class ApplyCondition {
  private final String loanTypeId;
  private final String locationId;
  private final String materialTypeId;
  private final String patronGroupId;
  @With
  private final Location location;

  public MultiMap toMap() {
    return caseInsensitiveMultiMap()
    .add(ITEM_TYPE_ID_NAME, materialTypeId)
    .add(LOAN_TYPE_ID_NAME, loanTypeId)
    .add(PATRON_TYPE_ID_NAME, patronGroupId)
    .add(LOCATION_ID_NAME, locationId);
  }

  public static ApplyCondition forItem(Item item, User user) {
    return new ApplyCondition(item.determineLoanTypeForItem(), item.getLocationId(),
      item.getMaterialTypeId(), user.getPatronGroupId(), item.getLocation());
  }

  public static ApplyCondition forRequest(WebContext context) {
    return new ApplyCondition(context.getParameter(LOAN_TYPE_ID_NAME),
      context.getParameter(LOCATION_ID_NAME), context.getParameter(ITEM_TYPE_ID_NAME),
      context.getParameter(PATRON_TYPE_ID_NAME), null);
  }
}
