package api.loans;

import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;

import api.support.http.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Matcher;

class RenewByBarcodeTests extends RenewalAPITests {
  @Override
  Response attemptRenewal(IndividualResource user, IndividualResource item) {
    return loansFixture.attemptRenewal(user, item);
  }

  @Override
  IndividualResource renew(IndividualResource user, IndividualResource item) {
    return loansFixture.renewLoan(user, item);
  }

  @Override
  Matcher<ValidationError> hasUserRelatedParameter(IndividualResource user) {
    return hasParameter("userBarcode", user.getJson().getString("barcode"));
  }

  @Override
  Matcher<ValidationError> hasItemRelatedParameter(IndividualResource item) {
    return hasParameter("itemBarcode", item.getJson().getString("barcode"));
  }

  @Override
  Matcher<ValidationError> hasItemNotFoundMessage(IndividualResource item) {
    return hasMessage(String.format("No item with barcode %s exists",
      item.getJson().getString("barcode")));
  }
}
