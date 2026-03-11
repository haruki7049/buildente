package com.example;

import dev.haruki7049.buildente.Build;
import java.util.ArrayList;

public class Hello {
  public static void hello() {
    System.out.println("Hello from Hello class...");
    Build b = new Build(new ArrayList<>());
    System.out.println("Build instance is created on Hello class...");
  }
}
