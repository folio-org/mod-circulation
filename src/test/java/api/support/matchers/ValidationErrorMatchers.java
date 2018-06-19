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
  public static TypeSafeDiagnosingMatcher<JsonObject> error(
    Matcher<ValidationError> matcher) {

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
}
