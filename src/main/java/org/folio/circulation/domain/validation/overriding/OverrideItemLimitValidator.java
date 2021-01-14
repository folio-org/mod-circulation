package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.domain.validation.overriding.OverridePermissions.OVERRIDE_ITEM_LIMIT_BLOCK;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;

public class OverrideItemLimitValidator extends OverrideValidator {
  private final Map<String, String> headers;
  private final CheckOutByBarcodeRequest request;

  public OverrideItemLimitValidator(Function<String, ValidationErrorFailure> overridingErrorFunction,
    Map<String, String> headers, CheckOutByBarcodeRequest request) {

    super(overridingErrorFunction);
    this.headers = headers;
    this.request = request;
  }

  @Override
  public CompletableFuture<Result<Boolean>> isOverridingForbidden() {
    return ofAsync(() -> !(request.getOverrideBlocks() != null
      && request.getOverrideBlocks().getItemLimitBlock() != null
      && headers.get(OKAPI_PERMISSIONS) != null
      && headers.get(OKAPI_PERMISSIONS).contains(OVERRIDE_ITEM_LIMIT_BLOCK.getValue())));
  }
}
