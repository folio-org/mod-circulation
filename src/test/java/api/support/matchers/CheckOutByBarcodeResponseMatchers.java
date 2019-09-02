package api.support.matchers;

import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.allOf;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Matcher;

public class CheckOutByBarcodeResponseMatchers {
  public static Matcher<ValidationError> hasUserBarcodeParameter(IndividualResource user) {
    return hasParameter("userBarcode", user.getJson().getString("barcode"));
  }

  public static Matcher<ValidationError> hasItemBarcodeParameter(IndividualResource item) {
    return hasParameter("itemBarcode", item.getJson().getString("barcode"));
  }

  public static Matcher<ValidationError> hasProxyUserBarcodeParameter(IndividualResource proxyUser) {
    return hasParameter("proxyUserBarcode", proxyUser.getJson().getString("barcode"));
  }

  public static Matcher<ValidationError> hasServicePointParameter(String servicePoint) {
    return hasParameter("checkoutServicePointId", servicePoint);
  }

  public static Matcher<ValidationError> hasLoanPolicyParameters(IndividualResource loanPolicy) {
    return allOf(
      hasParameter("loanPolicyId", loanPolicy.getId().toString()),
      hasParameter("loanPolicyName", loanPolicy.getJson().getString("name"))
    );
  }
}
