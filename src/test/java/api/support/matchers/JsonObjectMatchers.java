package api.support.matchers;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import static org.folio.circulation.support.JsonArrayHelper.toStream;

public class JsonObjectMatchers {
  public static TypeSafeMatcher<JsonObject> hasSoleErrorMessageContaining(String message) {
    return new TypeSafeMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a sole validation error message containing: %s", message));
      }

      @Override
      protected boolean matchesSafely(JsonObject body) {
        System.out.println(String.format("Inspecting for validation errors: %s",
          body.encodePrettily()));

        return toStream(body, "errors", ValidationError::fromJson)
          .anyMatch(error -> StringUtils.contains(error.getMessage(), message));
      }
    };
  }

  public static TypeSafeMatcher<JsonObject> hasSoleErrorFor(String propertyName, String propertyValue) {
    return new TypeSafeMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a sole validation error message for property \"%s\" : \"%s\"", propertyName, propertyValue));
      }

      @Override
      protected boolean matchesSafely(JsonObject body) {
        System.out.println(String.format("Inspecting for validation errors: %s",
          body.encodePrettily()));

        return toStream(body, "errors", ValidationError::fromJson)
          .anyMatch(error -> error.hasParameter(propertyName, propertyValue));
      }
    };
  }

  public static TypeSafeMatcher<JsonObject> hasErrorMessageContaining(String message) {
    return new TypeSafeMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a validation error message containing: %s", message));
      }

      @Override
      protected boolean matchesSafely(JsonObject body) {
        System.out.println(String.format("Inspecting for validation errors: %s",
          body.encodePrettily()));

        return toStream(body, "errors", ValidationError::fromJson)
          .anyMatch(error -> StringUtils.contains(error.getMessage(), message));
      }
    };
  }
}
