package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.resources.handlers.error.OverridableBlockType.ITEM_LIMIT_BLOCK;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.support.results.Result;

public class OverrideItemLimitValidator extends OverrideValidator {
  private final Map<String, String> headers;
  private final CheckOutByBarcodeRequest request;

  public OverrideItemLimitValidator(Map<String, String> headers,
    CheckOutByBarcodeRequest request) {

    super(request.getOverrideBlocks().getComment());
    this.headers = headers;
    this.request = request;
  }

  @Override
  public CompletableFuture<Result<Boolean>> isOverridingForbidden() {
    return ofAsync(() -> !(request.getOverrideBlocks() != null
      && request.getOverrideBlocks().getItemLimitBlock() != null
      && headers.get(OKAPI_PERMISSIONS) != null
      && ITEM_LIMIT_BLOCK.getRequiredOverridePermissions().stream()
      .allMatch(permission -> headers.get(OKAPI_PERMISSIONS).contains(permission))));
  }
}
