package org.folio.circulation.rules;

import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.ITEM_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.LOAN_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.LOCATION_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.PATRON_TYPE_ID_NAME;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
public final class RulesExecutionParameters {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
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

  public static RulesExecutionParameters forItem(Item item, User user) {
    log.debug("forItem:: parameters item: {}, user: {}", item, user);
    return new RulesExecutionParameters(item.getLoanTypeId(), item.getEffectiveLocationId(),
      item.getMaterialTypeId(), user.getPatronGroupId(), item.getLocation());
  }

  public static RulesExecutionParameters forRequest(WebContext context) {
    log.debug("forRequest:: parameters requestId {}", context.getRequestId());
    return new RulesExecutionParameters(context.getStringParameter(LOAN_TYPE_ID_NAME),
      context.getStringParameter(LOCATION_ID_NAME), context.getStringParameter(ITEM_TYPE_ID_NAME),
      context.getStringParameter(PATRON_TYPE_ID_NAME), null);
  }
}
