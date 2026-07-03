package server.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves safepath/ root whether you run from safepath/ or the repo root. */
public final class AppPaths {

  private static Path root;

  private AppPaths() {}

  public static Path root() throws IOException {
    if (root != null) return root;

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

    try {
      Path fromClass = Paths.get(AppPaths.class.getProtectionDomain()
          .getCodeSource().getLocation().toURI()).normalize();
      Path candidate = "out".equals(String.valueOf(fromClass.getFileName()))
          ? fromClass.getParent()
          : fromClass.getParent().getParent();
      if (candidate != null && hasFrontend(candidate)) {
        root = candidate.normalize();
        return root;
      }
    } catch (Exception ignored) {
      /* fallback to cwd checks only */
    }

    throw new IOException(
        "Cannot find frontend/ folder. Run the server from the safepath directory:\n"
            + "  cd safepath\n"
            + "  java -cp out server.Server\n"
            + "Current directory was: " + cwd);
  }

  public static Path frontendDir() throws IOException {
    return root().resolve("frontend");
  }

  private static boolean hasFrontend(Path base) {
    return Files.isDirectory(base.resolve("frontend"));
  }
}
