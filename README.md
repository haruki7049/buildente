# buildente

A build scripting library for Java (and probably other JVM languages in the future), as the build.zig from ziglang.

## How to use

On your project root, Create `Buildente.java` and then write as the following:

```java
import dev.haruki7049.buildente.Build;
import dev.haruki7049.buildente.BuildScript;
import dev.haruki7049.buildente.Executable;
import dev.haruki7049.buildente.FatJarStep;
import dev.haruki7049.buildente.ManifestConfig;
import dev.haruki7049.buildente.Module;
import dev.haruki7049.buildente.RunStep;

public class Buildente implements BuildScript {
  @Override
  public void build(Build b) {
    Module mod = b.createModule("src");

    mod.addDependency("buildente");

    ManifestConfig manifest =
        new ManifestConfig()
            .setMainClass("com.example.Main")
            .addAttribute("Built-By", "Buildente")
            .addAttribute("Implementation-Title", "Hello Example")
            .addAttribute("Implementation-Version", "1.0.0");

    Executable exe = b.addExecutable("com.example.Main", mod, manifest);

    FatJarStep fat = b.addFatJar("hello", exe);

    RunStep run = b.addRunArtifact(exe);

    b.getInstallStep().dependOn(fat);
    b.step("run", "Compile and run the Hello example").dependOn(run);
  }
}
```

## Supported Java version

Java 17 or above.
