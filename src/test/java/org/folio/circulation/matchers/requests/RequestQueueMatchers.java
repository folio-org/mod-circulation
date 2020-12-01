package org.folio.circulation.matchers.requests;

import static org.hamcrest.CoreMatchers.equalTo;

import org.folio.circulation.domain.RequestQueue;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;

public class RequestQueueMatchers {
  public static Matcher<RequestQueue> hasSize(Integer expectedSize) {
    return new RequestQueueSizeMatcher(expectedSize);
  }
  
  private static class RequestQueueSizeMatcher extends FeatureMatcher<RequestQueue, Integer> {
    private RequestQueueSizeMatcher(Integer expectedSize) {
      super(equalTo(expectedSize), "a request queue with size", "request queue size");
    }

    @Override
    protected Integer featureValueOf(RequestQueue actual) {
      return actual.size();
    }
  }
}
