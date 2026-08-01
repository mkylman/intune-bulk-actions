package com.mkylm.intunebulk.graph;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Windows implementation of {@link SecureTokenStore} backed by DPAPI through PowerShell.
 *
 * <p>The serialized cache is encrypted per-current-user before writing to disk.
 */
public final class WindowsSecureTokenStore implements SecureTokenStore {
  private static final String DEFAULT_SUBDIR = "IntuneBulkActions";
  private static final String DEFAULT_FILE = "token-cache.dpapi";

  private final Path encryptedCachePath;

  public WindowsSecureTokenStore() {
    this(resolveDefaultPath());
  }

  public WindowsSecureTokenStore(Path encryptedCachePath) {
    this.encryptedCachePath = encryptedCachePath.toAbsolutePath().normalize();
  }

  @Override
  public Optional<String> loadSerializedCache() {
    ensureWindows();
    if (!Files.isRegularFile(encryptedCachePath)) {
      return Optional.empty();
    }

    String script =
        "$enc = Get-Content -Path $args[0] -Raw\n"
            + "if ([string]::IsNullOrWhiteSpace($enc)) { return }\n"
            + "$sec = ConvertTo-SecureString $enc\n"
            + "$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)\n"
            + "try {\n"
            + "  $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)\n"
            + "  [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($plain))\n"
            + "} finally {\n"
            + "  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)\n"
            + "}\n";

    String output = runPowerShell(script, encryptedCachePath.toString());
    if (output.isBlank()) {
      return Optional.empty();
    }
    byte[] decoded = Base64.getDecoder().decode(output.trim());
    return Optional.of(new String(decoded, StandardCharsets.UTF_8));
  }

  @Override
  public void saveSerializedCache(String serializedCache) {
    ensureWindows();
    if (serializedCache == null) {
      throw new IllegalArgumentException("serializedCache must not be null");
    }
    try {
      Path parent = encryptedCachePath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed creating token cache directory.", e);
    }

    String payload = Base64.getEncoder().encodeToString(serializedCache.getBytes(StandardCharsets.UTF_8));
    String script =
        "$plain = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($args[0]))\n"
            + "$sec = ConvertTo-SecureString $plain -AsPlainText -Force\n"
            + "$enc = ConvertFrom-SecureString $sec\n"
            + "Set-Content -Path $args[1] -Value $enc -NoNewline -Encoding UTF8\n";
    runPowerShell(script, payload, encryptedCachePath.toString());
  }

  @Override
  public void clear() {
    try {
      Files.deleteIfExists(encryptedCachePath);
    } catch (IOException e) {
      throw new IllegalStateException("Failed clearing token cache: " + encryptedCachePath, e);
    }
  }

  private static void ensureWindows() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (!os.contains("win")) {
      throw new UnsupportedOperationException("WindowsSecureTokenStore is only supported on Windows.");
    }
  }

  private static Path resolveDefaultPath() {
    String localAppData = System.getenv("LOCALAPPDATA");
    if (localAppData != null && !localAppData.isBlank()) {
      return Path.of(localAppData).resolve(DEFAULT_SUBDIR).resolve(DEFAULT_FILE);
    }
    return Path.of(System.getProperty("user.home", ".")).resolve(".intune-bulk-actions").resolve(DEFAULT_FILE);
  }

  private static String runPowerShell(String script, String... args) {
    ProcessBuilder pb = new ProcessBuilder(buildCommand(script, args));
    pb.redirectErrorStream(false);
    try {
      Process process = pb.start();
      String stdout = readAll(process.getInputStream());
      String stderr = readAll(process.getErrorStream());
      boolean finished = process.waitFor(15, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException("PowerShell token-store command timed out.");
      }
      int exit = process.exitValue();
      if (exit != 0) {
        throw new IllegalStateException(
            "PowerShell token-store command failed (exit " + exit + "): " + stderr.trim());
      }
      return stdout;
    } catch (IOException e) {
      throw new IllegalStateException("Failed launching PowerShell for token-store operation.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted during token-store PowerShell operation.", e);
    }
  }

  private static String[] buildCommand(String script, String... args) {
    String[] cmd = new String[7 + (args == null ? 0 : args.length)];
    cmd[0] = "powershell";
    cmd[1] = "-NoProfile";
    cmd[2] = "-NonInteractive";
    cmd[3] = "-ExecutionPolicy";
    cmd[4] = "Bypass";
    cmd[5] = "-Command";
    cmd[6] = script;
    for (int i = 0; args != null && i < args.length; i++) {
      cmd[7 + i] = args[i];
    }
    return cmd;
  }

  private static String readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int read;
    while ((read = in.read(buffer)) >= 0) {
      out.write(buffer, 0, read);
    }
    return out.toString(StandardCharsets.UTF_8);
  }
}
