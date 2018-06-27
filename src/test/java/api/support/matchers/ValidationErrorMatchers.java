package api.support.matchers;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.core.IsCollectionContaining;

import java.util.List;

import static org.folio.circulation.support.JsonArrayHelper.mapToList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;

public class ValidationErrorMatchers {
  public static TypeSafeDiagnosingMatcher<JsonObject> hasErrorWith(Matcher<ValidationError> matcher) {

    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("Validation error which ").appendDescriptionOf(matcher);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation, Description description) {
        final Matcher<Iterable<? super ValidationError>> iterableMatcher = IsCollectionContaining.hasItem(matcher);
        final List<ValidationError> errors = mapToList(representation, "errors", ValidationError::fromJson);

        iterableMatcher.describeMismatch(errors, description);

        return iterableMatcher.matches(errors);
      }
    };
  }

  public static TypeSafeDiagnosingMatcher<ValidationError> hasParameter(String key, String value) {
    return new TypeSafeDiagnosingMatcher<ValidationError>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("has parameter with key ").appendValue(key)
          .appendText(" and value ").appendValue(value);
      }

      @Override
      protected boolean matchesSafely(ValidationError error, Description description) {
        final boolean hasParameter = error.hasParameter(key, value);

        if(!hasParameter) {
          if(!error.hasParameter(key)) {
            description.appendText("does not have parameter ").appendValue(key);
          }
          else {
            description.appendText("parameter has value ").appendValue(error.getParameter(key));
          }
        }

        return hasParameter;
      }
    };
  }

  public static TypeSafeDiagnosingMatcher<ValidationError> hasMessage(String message) {
    return new TypeSafeDiagnosingMatcher<ValidationError>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("has message ").appendValue(message);
      }

      @Override
      protected boolean matchesSafely(ValidationError error, Description description) {
        final Matcher<Object> matcher = hasProperty("message", equalTo(message));

        matcher.describeMismatch(error, description);

        return matcher.matches(error);
      }
    };
  }
}
