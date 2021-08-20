package org.folio.circulation.matchers.requests;

import static org.hamcrest.CoreMatchers.equalTo;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.hamcrest.Description;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

class RequestQueueMatchers {
  public static Matcher<RequestQueue> hasSize(Integer expectedSize) {
    return new SizeMatcher(expectedSize);
  }

  public static Matcher<RequestQueue> doesNotInclude(Request request) {
    return new DoesNotIncludeMatcher(request);
  }

  public static Matcher<RequestQueue> includes(Request request) {
    return new IncludesMatcher(request);
  }

  private static class SizeMatcher extends FeatureMatcher<RequestQueue, Integer> {
    private SizeMatcher(Integer expectedSize) {
      super(equalTo(expectedSize), "a request queue with size", "request queue size");
    }

    @Override
    protected Integer featureValueOf(RequestQueue actual) {
      return actual.size();
    }
  }

  private static class IncludesMatcher extends TypeSafeDiagnosingMatcher<RequestQueue> {
    private final Request request;

    private IncludesMatcher(Request request) {
      this.request = request;
    }

    @Override
    protected boolean matchesSafely(RequestQueue queue, Description mismatchDescription) {
      final var contains = queue.contains(request);

      if (!contains) {
        mismatchDescription.appendText(" does not contain request with ID ").appendValue(request.getId());
      }

      return contains;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("does contain request with ID ").appendValue(request.getId());
    }
  }

  private static class DoesNotIncludeMatcher extends TypeSafeDiagnosingMatcher<RequestQueue> {
    private final Request request;

    private DoesNotIncludeMatcher(Request request) {
      this.request = request;
    }

    @Override
    protected boolean matchesSafely(RequestQueue queue, Description mismatchDescription) {
      final var contains = queue.contains(request);

      if (contains) {
        mismatchDescription.appendText(" does contain request with ID ").appendValue(request.getId());
      }

      return !contains;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("does not contain request with ID ").appendValue(request.getId());
    }
  }
}
