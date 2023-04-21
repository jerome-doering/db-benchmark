package org.example;

import java.time.LocalDateTime;
import java.util.Objects;

public class Main {

  public static void main(String[] args) {
    try {
      final var runner = new Runner();
      System.out.printf("[%s]: Started%n", LocalDateTime.now());
      runner.run();
      System.out.printf("[%s]: Finished%n", LocalDateTime.now());
    } catch (Throwable e) {
      System.err.println(e.getMessage() + " " + (Objects.nonNull(e.getCause()) ? e.getCause().getMessage() : ""));
      for (StackTraceElement line : e.getStackTrace()) {
        System.err.println(line.toString());
      }
    }
  }
}