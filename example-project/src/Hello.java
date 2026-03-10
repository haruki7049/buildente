package com.example;

/**
 * A minimal example program compiled and run by Buildente.
 *
 * <p>This file is intentionally kept simple so it works as a quick
 * end-to-end smoke test for the build system:
 *
 * <pre>
 *   Step 1: Executable.execute()  ->  javac -d build/classes Hello.java
 *   Step 2: RunStep.execute()     ->  java  -cp build/classes dev.haruki7049.buildente.example.Hello
 * </pre>
 */
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello from Buildente!");
        System.out.println("  -> This file was compiled and executed by the Buildente build system.");
    }
}
