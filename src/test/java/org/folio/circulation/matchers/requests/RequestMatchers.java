package org.folio.circulation.matchers.requests;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;

import org.folio.circulation.domain.Request;
import org.hamcrest.Description;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class RequestMatchers {
  public static Matcher<Request> hasNoPosition() {
    return new NoPositionMatcher();
  }

  public static Matcher<Request> inPosition(Integer position) {
    return new PositionMatcher(position);
  }

  private static class NoPositionMatcher extends TypeSafeDiagnosingMatcher<Request> {
    @Override
    protected boolean matchesSafely(Request request, Description mismatchDescription) {
      final var matcher = is(nullValue());

      mismatchDescription.appendText("request has position ").appendValue(request.getPosition());

      return matcher.matches(request.getPosition());
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("request has no position");
    }
  }

  private static class PositionMatcher extends FeatureMatcher<Request, Integer> {
    PositionMatcher(Integer position) {
      super(equalTo(position), "request with position", "position");
    }

    @Override
    protected Integer featureValueOf(Request actual) {
      return actual.getPosition();
    }
  }
}
