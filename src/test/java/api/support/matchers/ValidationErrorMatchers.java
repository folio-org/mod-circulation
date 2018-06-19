package api.support.matchers;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.core.IsCollectionContaining;

import java.util.List;

import static org.folio.circulation.support.JsonArrayHelper.mapToList;

public class ValidationErrorMatchers {
  public static TypeSafeDiagnosingMatcher<JsonObject> error(Matcher<ValidationError> matcher) {

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
          if(!error.hasParameterWithKey(key)) {
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
}
