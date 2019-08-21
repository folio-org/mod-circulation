package api.support.matchers;

import static org.folio.circulation.support.JsonArrayHelper.mapToList;
import static org.folio.circulation.support.JsonArrayHelper.toStream;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.core.IsCollectionContaining;

import io.vertx.core.json.JsonObject;

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
        final List<ValidationError> errors = mapToList(representation, "errors", ValidationErrorMatchers::fromJson);

        iterableMatcher.describeMismatch(errors, description);

        return iterableMatcher.matches(errors);
      }
    };
  }

  public static TypeSafeDiagnosingMatcher<HttpFailure> isErrorWith(Matcher<ValidationError> matcher) {
    return new TypeSafeDiagnosingMatcher<HttpFailure>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("Validation error which ").appendDescriptionOf(matcher);
      }

      @Override
      protected boolean matchesSafely(HttpFailure failure, Description description) {
        if(failure instanceof ValidationErrorFailure) {
          final Matcher<Iterable<? super ValidationError>> iterableMatcher
            = IsCollectionContaining.hasItem(matcher);

          final Collection<ValidationError> errors = ((ValidationErrorFailure) failure).getErrors();

          iterableMatcher.describeMismatch(errors, description);

          return iterableMatcher.matches(errors);
        }
        else {
          description.appendText("is not a validation error failure");
          return false;
        }
      }
    };
  }
  
  public static TypeSafeDiagnosingMatcher<ValidationError> hasNullParameter(String key) {
    return hasParameter(key, null);
  }

  public static TypeSafeDiagnosingMatcher<ValidationError> hasUUIDParameter(String key, UUID value) {
    return hasParameter(key, value.toString());
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

        if (!hasParameter) {
          if (!error.hasParameter(key)) {
            description.appendText("does not have parameter ").appendValue(key);
          } else {
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

  public static TypeSafeDiagnosingMatcher<ValidationError> hasMessageContaining(String message) {
    return new TypeSafeDiagnosingMatcher<ValidationError>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("has message ").appendValue(message);
      }

      @Override
      protected boolean matchesSafely(ValidationError error, Description description) {
        final Matcher<Object> matcher = hasProperty("message", containsString(message));

        matcher.describeMismatch(error, description);

        return matcher.matches(error);
      }
    };
  }

  private static ValidationError fromJson(JsonObject representation) {
    final Map<String, String> parameters = toStream(representation, "parameters")
      .filter(Objects::nonNull)
      .filter(p -> p.containsKey("key"))
      .filter(p -> p.containsKey("value"))
      .filter(p -> p.getString("key") != null)
      .filter(p -> p.getString("value") != null)
      .collect(Collectors.toMap(
        p -> p.getString("key"),
        p -> p.getString("value")));

    return new ValidationError(
      getProperty(representation, "message"), parameters);
  }
}
