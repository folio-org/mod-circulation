package api.support.matchers;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static org.folio.circulation.support.StreamToListMapper.toList;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.toStream;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsIterableContaining;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ValidationErrorMatchers {
  public static TypeSafeDiagnosingMatcher<JsonObject> hasErrorWith(Matcher<ValidationError> matcher) {
    return new TypeSafeDiagnosingMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("Validation error which ").appendDescriptionOf(matcher);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation, Description description) {
        final Matcher<Iterable<? super ValidationError>> iterableMatcher = IsIterableContaining.hasItem(matcher);
        final List<ValidationError> errors = errorsFromJson(representation);

        iterableMatcher.describeMismatch(errors, description);

        return iterableMatcher.matches(errors);
      }
    };
  }

  public static TypeSafeDiagnosingMatcher<HttpFailure> isErrorWith(Matcher<ValidationError> matcher) {
    return new TypeSafeDiagnosingMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("Validation error which ").appendDescriptionOf(matcher);
      }

      @Override
      protected boolean matchesSafely(HttpFailure failure, Description description) {
        if (failure instanceof ValidationErrorFailure) {
          final Matcher<Iterable<? super ValidationError>> iterableMatcher
            = IsIterableContaining.hasItem(matcher);

          final Collection<ValidationError> errors = ((ValidationErrorFailure) failure).getErrors();

          iterableMatcher.describeMismatch(errors, description);

          return iterableMatcher.matches(errors);
        } else {
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
    return new TypeSafeDiagnosingMatcher<>() {
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
    return new TypeSafeDiagnosingMatcher<>() {
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
    return new TypeSafeDiagnosingMatcher<>() {
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

  public static TypeSafeDiagnosingMatcher<ValidationError> hasCode(ErrorCode errorCode) {
    return new TypeSafeDiagnosingMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("has code ").appendValue(errorCode.toString());
      }

      @Override
      protected boolean matchesSafely(ValidationError error, Description description) {
        final Matcher<Object> matcher = hasProperty("code", equalTo(errorCode));
        matcher.describeMismatch(error, description);
        return matcher.matches(error);
      }
    };
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> hasErrors(int numberOfErrors) {
    return new TypeSafeDiagnosingMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Errors array of size ").appendValue(numberOfErrors);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation, Description description) {
        int actualNumberOfErrors = ofNullable(representation.getJsonArray("errors"))
          .map(JsonArray::size)
          .orElse(0);

        Matcher<Integer> sizeMatcher = Is.is(actualNumberOfErrors);
        sizeMatcher.describeMismatch(actualNumberOfErrors, description);

        return sizeMatcher.matches(numberOfErrors);
      }
    };
  }

  private static ValidationError fromJson(JsonObject representation) {
    String message = getProperty(representation, "message");

    final Map<String, String> parameters = toStream(representation, "parameters")
      .filter(Objects::nonNull)
      .filter(p -> p.containsKey("key"))
      .filter(p -> p.containsKey("value"))
      .filter(p -> p.getString("key") != null)
      .filter(p -> p.getString("value") != null)
      .collect(Collectors.toMap(
        p -> p.getString("key"),
        p -> p.getString("value")));

    String code = getProperty(representation, "code");

    if (code != null) {
      return new ValidationError(message, parameters, ErrorCode.valueOf(code));
    } else {
      return new ValidationError(message, parameters);
    }
  }

  public static List<ValidationError> errorsFromJson(JsonObject representation) {
    return toList(toStream(representation, "errors")
      .map(ValidationErrorMatchers::fromJson));
  }

  public static Matcher<JsonObject> isBlockRelatedError(String message, String blockName,
    String missingPermission) {

    return isBlockRelatedError(message, blockName, singletonList(missingPermission));
  }

  public static Matcher<JsonObject> isBlockRelatedError(String message, String blockName,
    List<String> missingPermissions) {

    return allOf(
      hasJsonPath("message", is(message)),
      hasJsonPath("overridableBlock.name", is(blockName)),
      hasJsonPath("overridableBlock.missingPermissions", hasItems(missingPermissions.toArray()))
    );
  }

  public static Matcher<JsonObject> isInsufficientPermissionsError(
    String blockName, List<String> missingPermissions) {

    return isBlockRelatedError("Insufficient override permissions", blockName, missingPermissions);
  }

  public static Matcher<JsonObject> isInsufficientPermissionsToOverridePatronBlockError() {
    return isInsufficientPermissionsError("patronBlock",
      List.of("circulation.override-patron-block.post"));
  }

}
