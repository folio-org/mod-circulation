package api.support.matchers;

import static org.hamcrest.CoreMatchers.is;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.TypeSafeMatcher;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.matchers.JsonPathMatchers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.minidev.json.JSONArray;

public class JsonObjectMatcher {

  public static <T> Matcher<JsonObject> hasJsonPath(String jsonPath, Matcher<T> valueMatcher) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      protected boolean matchesSafely(JsonObject item, Description mismatchDescription) {
        Object actual;
        try {
          actual = JsonPath.parse(item.toString()).read(jsonPath);
          if (actual instanceof JSONArray) {
            actual = new JsonArray(((JSONArray) actual).toJSONString());
          }
        } catch (Exception ex) {
          mismatchDescription.appendText("Exception occurred: ").appendValue(ex);
          return false;
        }

         if (!valueMatcher.matches(actual)) {
          mismatchDescription.appendText("Expected json path [")
            .appendValue(jsonPath).appendText("] evaluated to ")
            .appendDescriptionOf(valueMatcher).appendText(" but actual [")
            .appendValue(actual).appendText("]");

          return false;
        }

        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("Json path [").appendValue(jsonPath)
          .appendText("] ").appendDescriptionOf(valueMatcher);
      }
    };
  }

  public static Matcher<JsonObject> hasNoJsonPath(String jsonPath) {
    return new TypeSafeMatcher<JsonObject>() {
      @Override
      protected boolean matchesSafely(JsonObject item) {
        return JsonPathMatchers.hasNoJsonPath(jsonPath)
          .matches(item.toString());
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("Json has no path [")
          .appendValue(jsonPath).appendText("] ");
      }

      @Override
      protected void describeMismatchSafely(JsonObject item, Description mismatchDescription) {
        final Object actual = JsonPath.parse(item.toString()).read(jsonPath);
        mismatchDescription.appendText("was [").appendValue(actual).appendText("]");
      }
    };
  }

  public static <T> Matcher<JsonObject> hasJsonPath(String jsonPath, T expectedValue) {
    return hasJsonPath(jsonPath, is(expectedValue));
  }

  @SafeVarargs
  public static Matcher<JsonObject> allOfPaths(Matcher<? super String>... jsonPathMatchers) {
    return allOfPaths(Arrays.asList(jsonPathMatchers));
  }

  public static Matcher<JsonObject> allOfPaths(List<Matcher<? super String>> jsonPathMatchers) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      protected boolean matchesSafely(JsonObject jsonObject, Description description) {
        String jsonString = jsonObject.encode();

        List<Matcher<? super String>> notMatched = jsonPathMatchers.stream()
          .filter(m -> !m.matches(jsonString))
          .collect(Collectors.toList());

        if (!notMatched.isEmpty()) {
          description.appendText(" not matched paths: ")
            .appendList("<", ", ", ">", notMatched);
          return false;
        }

        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("json object with: ")
          .appendList("<", ", ", ">", jsonPathMatchers);
      }
    };
  }

  public static Matcher<JsonObject> allOfPaths(Map<String, Matcher<String>> jsonPathMatchers) {
    return JsonObjectMatcher.allOfPaths(toStringMatcher(jsonPathMatchers));
  }

  public static Matcher<? super String> toStringMatcher(Map<String, Matcher<String>> jsonPathMatchers) {
    List<Matcher<? super String>> matchers = jsonPathMatchers.entrySet().stream()
      .map(e -> com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath(e.getKey(), e.getValue()))
      .collect(Collectors.toList());
    return CoreMatchers.allOf(matchers);
  }
}
