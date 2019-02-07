package org.folio.circulation.loanrules;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.Test;

public class NameConverterTest {
  private Map<String,Map<String,String>> name2uuid = new HashMap<>();
  private Map<String,Map<String,String>> uuid2name = new HashMap<>();
  {
    Map<String,String> m = new HashMap<>();
    m.put("book", "123");
    m.put("dvd", "987");
    name2uuid.put("m", m);
    Map<String,String> policy = new HashMap<>();
    policy.put("loan-fallback", "0");
    policy.put("request-fallback", "1");
    policy.put("notice-fallback", "2");
    policy.put("policy-x", "3");
    policy.put("policy-y", "4");
    policy.put("policy-z", "5");
    name2uuid.put("policy", policy);

    for (Entry<String,Map<String,String>> typeMap : name2uuid.entrySet()) {
      String type = typeMap.getKey();
      Map<String,String> map = typeMap.getValue();
      Map<String,String> inverted = new HashMap<>();
      for (Entry<String,String> entry : map.entrySet()) {
        inverted.put(entry.getValue(), entry.getKey());
      }
      uuid2name.put(type, inverted);
    }
  }

  private String loanRulesNames = String.join("\n",
      "priority: last-line",
      "fallback-policy: l loan-fallback r request-fallback n notice-fallback",
      "m book withoutreplacement dvd + t withoutreplacement: l policy-x r policy-y n policy-z",
      ""
  );
  private String loanRulesUuids = String.join("\n",
      "priority: last-line",
      "fallback-policy: l 0 r 1 n 2",
      "m 123 withoutreplacement 987 + t withoutreplacement: l 3 r 4 n 5",
      ""
  );

  private String convertNames(String loanRules, Map<String,Map<String,String>> replacements) {
    CharStream inputStream = CharStreams.fromString(loanRules);
    LoanRulesLexer lexer = new LoanRulesLexer(inputStream);
    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    LoanRulesParser parser = new LoanRulesParser(tokenStream);
    parser.removeErrorListeners(); // remove ConsoleErrorListener
    parser.addErrorListener(new ErrorListener());
    ParseTree parseTree = parser.loanRulesFile();
    ParseTreeWalker walker = new ParseTreeWalker();
    NameConverter nameConverter = new NameConverter(tokenStream, replacements);
    walker.walk(nameConverter, parseTree);
    return nameConverter.getText();
  }

  @Test
  public void replaceNames() {
    assertThat(convertNames(loanRulesNames, name2uuid), is(loanRulesUuids));
  }

  @Test
  public void replaceUuids() {
    assertThat(convertNames(loanRulesUuids, uuid2name), is(loanRulesNames));
  }
}
