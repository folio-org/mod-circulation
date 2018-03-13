package org.folio.circulation.api.support.matchers;

import org.folio.HttpStatus;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

/**
 * Match Response.getStatusCode() against a HttpStatus.
 */
public class ResponseStatusCodeMatcher extends TypeSafeDiagnosingMatcher<Response> {
  /**
   * Match Response.getStatusCode() against a httpStatus.
   * @param httpStatus  the code to match against.
   * @return the matcher
   */
  public static ResponseStatusCodeMatcher hasStatus(HttpStatus httpStatus) {
    return new ResponseStatusCodeMatcher(httpStatus);
  }

  private final HttpStatus httpStatus;

  /**
   * Set status code.
   * @param httpStatus  the status code to match against.
   */
  public ResponseStatusCodeMatcher(HttpStatus httpStatus) {
    this.httpStatus = httpStatus;
  }

  @Override
  protected boolean matchesSafely(Response response, Description description) {
    if (response.getStatusCode() == httpStatus.toInt()) {
      return true;
    }
    description.appendText("a Response with statusCode of ")
      .appendValue(response.getStatusCode());
    if (response.hasBody()) {
      description.appendText(" and this body: " + response.getBody());
    }
    return false;
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("a Response with statusCode of ").appendValue(httpStatus.toInt())
      .appendText(" (").appendText(httpStatus.toString()).appendText(")");
  }
}
