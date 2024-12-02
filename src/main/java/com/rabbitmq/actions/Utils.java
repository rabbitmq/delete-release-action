/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.actions;

import static java.lang.String.format;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

abstract class Utils {

  static void testSequence() {
    Consumer<String> display = Utils::logGreen;
    String message;
    int exitCode = 0;
    try {
      String testUri = "https://www.wikipedia.org/";
      logYellow("Starting test sequence, trying to reach " + testUri);
      HttpRequest request = HttpRequest.newBuilder().uri(new URI(testUri)).GET().build();
      try (HttpClient client =
          HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build()) {
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        int statusClass = response.statusCode() - response.statusCode() % 100;
        message = "Response code is " + response.statusCode();
        if (statusClass != 200) {
          display = Utils::logRed;
          exitCode = 1;
        }
      }
    } catch (Exception e) {
      message = "Error during test sequence: " + e.getMessage();
      display = Utils::logRed;
      exitCode = 1;
    }
    display.accept(message);
    System.exit(exitCode);
  }

  static void logGreen(String message, Object... args) {
    log("\u001B[32m" + format(message, args) + "\u001B[0m");
  }

  static void logYellow(String message, Object... args) {
    log("\u001B[33m" + format(message, args) + "\u001B[0m");
  }

  static void logRed(String message, Object... args) {
    log("\u001B[31m" + format(message, args) + "\u001B[0m");
  }

  static void log(String message, Object... args) {
    System.out.printf((message) + "%n", args);
  }
}
