package org.folio.circulation.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.spi.ContextAwareBase;

/**
 * Configure logback.
 * <p>
 * Using this a Configurator class is much faster because it avoids parsing a logback.xml file.
 * For unit tests logback-test.xml must be used because there can be only one Configurator class.
 * <p>
 * <a href="https://logback.qos.ch/manual/configuration.html">https://logback.qos.ch/manual/configuration.html</a>
 */
public class LogbackConfigurator extends ContextAwareBase implements Configurator {
  public LogbackConfigurator() {
  }

  @Override
  public void configure(LoggerContext loggerContext) {
    addInfo("Setting up default configuration.");

    ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<ILoggingEvent>();
    consoleAppender.setContext(loggerContext);
    consoleAppender.setName("console");
    LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<ILoggingEvent>();
    encoder.setContext(loggerContext);

    // same as ch.qos.logback.classic.layout.TTLLLayout()
    PatternLayout layout = new PatternLayout();
    layout.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
    layout.setContext(loggerContext);
    layout.start();
    encoder.setLayout(layout);

    consoleAppender.setEncoder(encoder);
    consoleAppender.start();

    Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
    rootLogger.addAppender(consoleAppender);
    rootLogger.setLevel(Level.ERROR);

    loggerContext.getLogger("io.netty").setLevel(Level.WARN);
  }
}
