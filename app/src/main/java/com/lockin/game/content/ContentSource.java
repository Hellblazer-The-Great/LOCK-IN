package com.lockin.game.content;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Asset paths are relative to a single content root. No network, absolute paths or traversal. */
public interface ContentSource {
  InputStream open(String path) throws IOException;

  default String text(String path) throws IOException {
    try (InputStream in = open(path);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) {
        out.write(b, 0, n);
        if (out.size() > 8 * 1024 * 1024) throw new IOException(path + ": JSON exceeds 8 MiB");
      }
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }

  static String path(String base, String relative) {
    if (relative.isEmpty()
        || relative.startsWith("/")
        || relative.contains("\\")
        || relative.contains(":")
        || relative.indexOf('\0') >= 0)
      throw new IllegalArgumentException(base + ": unsafe asset path " + relative);
    java.util.ArrayList<String> parts = new java.util.ArrayList<>();
    String folder = base.contains("/") ? base.substring(0, base.lastIndexOf('/') + 1) : "";
    for (String part : (folder + relative).split("/")) {
      if (part.equals(".") || part.isEmpty()) continue;
      if (part.equals("..")) {
        if (parts.isEmpty())
          throw new IllegalArgumentException(base + ": path escapes content root");
        parts.remove(parts.size() - 1);
      } else parts.add(part);
    }
    return String.join("/", parts);
  }
}
