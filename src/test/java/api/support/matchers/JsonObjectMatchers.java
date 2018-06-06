package api.support.matchers;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.List;

import static org.folio.circulation.support.JsonArrayHelper.toList;

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

        if(!body.containsKey("errors")) {
          return false;
        }

        final List<JsonObject> errors = toList(body.getJsonArray("errors"));

        if(errors.size() == 1) {
          return errors.get(0).getString("message").contains(message);
        }
        else
          return false;
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

        if(!body.containsKey("errors")) {
          return false;
        }

        final List<JsonObject> errors = toList(body.getJsonArray("errors"));

        if(errors.isEmpty()) {
          return false;
        }

        final JsonObject error = errors.get(0);

        if(!error.containsKey("parameters")) {
          return false;
        }

        List<JsonObject> parameters = toList(error.getJsonArray("parameters"));

        if(errors.isEmpty()) {
          return false;
        }

        return parameters.stream()
          .anyMatch(parameter ->
            StringUtils.equals(parameter.getString("key"), propertyName) &&
            StringUtils.equals(parameter.getString("value"), propertyValue));
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

        if (!body.containsKey("errors")) {
          return false;
        }

        final List<JsonObject> errors = toList(body.getJsonArray("errors"));

        return errors.stream()
          .anyMatch(error -> error.getString("message").contains(message));
      }
    };
  }
}
