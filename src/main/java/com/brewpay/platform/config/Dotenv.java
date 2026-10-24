package com.brewpay.platform.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads KEY=VALUE lines. Values are not logged. */
final class Dotenv {

  private Dotenv() {}

  static Map<String, String> parse(List<String> lines) {
    Map<String, String> values = new LinkedHashMap<>();
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      if (trimmed.startsWith("export ")) {
        trimmed = trimmed.substring("export ".length()).trim();
      }
      int separator = trimmed.indexOf('=');
      if (separator <= 0) {
        continue;
      }
      String key = trimmed.substring(0, separator).trim();
      String value = unquote(trimmed.substring(separator + 1));
      if (!key.isEmpty()) {
        values.put(key, value);
      }
    }
    return Map.copyOf(values);
  }

  private static String unquote(String value) {
    if (value.length() >= 2) {
      char open = value.charAt(0);
      char close = value.charAt(value.length() - 1);
      if ((open == '"' && close == '"') || (open == '\'' && close == '\'')) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }
}
