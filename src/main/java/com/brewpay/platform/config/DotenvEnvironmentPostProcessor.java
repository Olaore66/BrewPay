package com.brewpay.platform.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Loads a gitignored {@code .env} for a local run. A real environment variable wins. Tests keep the
 * Testcontainers database.
 */
public final class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (runningUnderTest() || !Files.isRegularFile(Path.of(".env"))) {
      return;
    }
    Map<String, Object> values = new LinkedHashMap<>();
    try {
      Dotenv.parse(Files.readAllLines(Path.of(".env"), StandardCharsets.UTF_8))
          .forEach(values::put);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read .env");
    }
    if (!values.isEmpty()) {
      environment.getPropertySources().addLast(new MapPropertySource("dotenv", values));
    }
  }

  private static boolean runningUnderTest() {
    for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
      String name = frame.getClassName();
      if (name.startsWith("org.springframework.boot.test.") || name.startsWith("org.junit.")) {
        return true;
      }
    }
    return false;
  }
}
