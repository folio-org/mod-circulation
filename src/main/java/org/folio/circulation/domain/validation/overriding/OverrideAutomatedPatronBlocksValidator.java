package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.domain.validation.overriding.OverridePermissions.OVERRIDE_PATRON_BLOCK;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.support.results.Result;

public class OverrideAutomatedPatronBlocksValidator extends OverrideValidator {
  private final Map<String, String> headers;
  private final CheckOutByBarcodeRequest request;

  public OverrideAutomatedPatronBlocksValidator(Map<String, String> headers,
    CheckOutByBarcodeRequest request) {

    super(request.getOverrideBlocks().getComment());
    this.headers = headers;
    this.request = request;
  }

  @Override
  public CompletableFuture<Result<Boolean>> isOverridingForbidden() {
    return ofAsync(() -> !(request.getOverrideBlocks() != null
      && request.getOverrideBlocks().getPatronBlock() != null
      && headers.get(OKAPI_PERMISSIONS) != null
      && headers.get(OKAPI_PERMISSIONS).contains(OVERRIDE_PATRON_BLOCK.getValue())));
  }
}
