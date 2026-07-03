package server.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves safepath/ project root (frontend + config) for Maven JAR or IDE runs. */
public final class AppPaths {

  private static Path root;

  private AppPaths() {}

  public static Path root() throws IOException {
    if (root != null) return root;

    String envRoot = System.getenv("SAFEPATH_ROOT");
    if (envRoot != null && !envRoot.isBlank()) {
      Path env = Paths.get(envRoot.trim()).toAbsolutePath().normalize();
      if (hasFrontend(env)) {
        root = env;
        return root;
      }
      System.err.println("[AppPaths] SAFEPATH_ROOT has no frontend/: " + env);
    }

    Path cwd = Paths.get("").toAbsolutePath().normalize();
    if (hasFrontend(cwd)) {
      root = cwd;
      return root;
    }

    Path nested = cwd.resolve("safepath").normalize();
    if (hasFrontend(nested)) {
      root = nested;
      return root;
    }

    if ("target".equals(String.valueOf(cwd.getFileName()))) {
      Path parent = cwd.getParent();
      if (parent != null && hasFrontend(parent)) {
        root = parent;
        return root;
      }
    }

    Path fromCode = resolveFromCodeSource();
    if (fromCode != null && hasFrontend(fromCode)) {
      root = fromCode;
      return root;
    }

    throw new IOException(
        "Cannot find frontend/ folder. Set SAFEPATH_ROOT or run from the safepath directory:\n"
            + "  cd safepath\n"
            + "  mvn clean package\n"
            + "  java -jar target/safepath-1.0.0.jar\n"
            + "Current directory was: " + cwd);
  }

  public static Path frontendDir() throws IOException {
    return root().resolve("frontend");
  }

  private static Path resolveFromCodeSource() {
    try {
      Path location = Paths.get(AppPaths.class.getProtectionDomain()
          .getCodeSource().getLocation().toURI()).normalize();

      if (Files.isRegularFile(location) && location.toString().endsWith(".jar")) {
        Path parent = location.getParent();
        if (parent != null && "target".equals(String.valueOf(parent.getFileName()))) {
          Path projectRoot = parent.getParent();
          if (projectRoot != null) return projectRoot;
        }
        return parent;
      }

      if (Files.isDirectory(location)) {
        if ("classes".equals(String.valueOf(location.getFileName()))) {
          Path target = location.getParent();
          return target != null ? target.getParent() : null;
        }
        if ("out".equals(String.valueOf(location.getFileName()))) {
          return location.getParent();
        }
      }
    } catch (Exception ignored) {
      /* fall through */
    }
    return null;
  }

  private static boolean hasFrontend(Path base) {
    return base != null && Files.isDirectory(base.resolve("frontend"));
  }
}
