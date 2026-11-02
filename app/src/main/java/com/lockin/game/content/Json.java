package com.lockin.game.content;

import java.util.*;

/** Strict, dependency-free JSON reader, shared by Android and the host validator. */
public final class Json {
  private final String file, text;
  private int pos;

  private Json(String file, String text) {
    this.file = file;
    this.text = text;
  }

  public static Node parse(String file, String text) {
    Json p = new Json(file, text);
    Object value = p.value(0);
    p.space();
    if (p.pos != text.length()) throw p.error("unexpected trailing input");
    return new Node(value, file);
  }

  private IllegalArgumentException error(String message) {
    int line = 1;
    for (int i = 0; i < pos; i++) if (text.charAt(i) == '\n') line++;
    return new IllegalArgumentException(file + ":" + line + ": " + message);
  }

  private void space() {
    while (pos < text.length() && " \r\n\t".indexOf(text.charAt(pos)) >= 0) pos++;
  }

  private boolean eat(char c) {
    space();
    if (pos < text.length() && text.charAt(pos) == c) {
      pos++;
      return true;
    }
    return false;
  }

  private void need(char c) {
    if (!eat(c)) throw error("expected '" + c + "'");
  }

  private Object value(int depth) {
    if (depth > 64) throw error("nesting exceeds 64");
    space();
    if (pos == text.length()) throw error("missing value");
    char c = text.charAt(pos);
    if (c == '"') return string();
    if (eat('{')) {
      Map<String, Object> m = new LinkedHashMap<>();
      if (eat('}')) return m;
      do {
        space();
        if (pos == text.length() || text.charAt(pos) != '"') throw error("expected property name");
        String k = string();
        need(':');
        if (m.containsKey(k)) throw error("duplicate property '" + k + "'");
        m.put(k, value(depth + 1));
      } while (eat(','));
      need('}');
      return m;
    }
    if (eat('[')) {
      List<Object> a = new ArrayList<>();
      if (eat(']')) return a;
      do {
        a.add(value(depth + 1));
      } while (eat(','));
      need(']');
      return a;
    }
    for (String literal : new String[] {"true", "false", "null"})
      if (text.startsWith(literal, pos)) {
        pos += literal.length();
        return literal.equals("null") ? null : literal.equals("true");
      }
    int start = pos;
    if (pos < text.length() && text.charAt(pos) == '-') pos++;
    if (pos == text.length()) throw error("invalid number");
    if (text.charAt(pos) == '0') pos++;
    else {
      if (text.charAt(pos) < '1' || text.charAt(pos) > '9') throw error("invalid value");
      while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
    }
    if (pos < text.length() && text.charAt(pos) == '.') {
      pos++;
      int s = pos;
      while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
      if (s == pos) throw error("missing fraction");
    }
    if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
      pos++;
      if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) pos++;
      int s = pos;
      while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
      if (s == pos) throw error("missing exponent");
    }
    try {
      double n = Double.parseDouble(text.substring(start, pos));
      if (!Double.isFinite(n)) throw error("number is not finite");
      return n;
    } catch (NumberFormatException e) {
      throw error("invalid number");
    }
  }

  private String string() {
    need('"');
    StringBuilder b = new StringBuilder();
    while (pos < text.length()) {
      char c = text.charAt(pos++);
      if (c == '"') return b.toString();
      if (c < 32) throw error("control character in string");
      if (c == '\\') {
        if (pos == text.length()) throw error("unterminated escape");
        c = text.charAt(pos++);
        switch (c) {
          case '"':
          case '\\':
          case '/':
            b.append(c);
            break;
          case 'b':
            b.append('\b');
            break;
          case 'f':
            b.append('\f');
            break;
          case 'n':
            b.append('\n');
            break;
          case 'r':
            b.append('\r');
            break;
          case 't':
            b.append('\t');
            break;
          case 'u':
            if (pos + 4 > text.length()) throw error("short unicode escape");
            try {
              b.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
            } catch (NumberFormatException e) {
              throw error("invalid unicode escape");
            }
            pos += 4;
            break;
          default:
            throw error("invalid escape");
        }
      } else b.append(c);
    }
    throw error("unterminated string");
  }

  public static final class Node {
    private final Object value;
    public final String path;

    public Node(Object value, String path) {
      this.value = value;
      this.path = path;
    }

    public IllegalArgumentException error(String m) {
      return new IllegalArgumentException(path + ": " + m);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object() {
      if (!(value instanceof Map)) throw error("expected object");
      return (Map<String, Object>) value;
    }

    public boolean has(String k) {
      return object().containsKey(k);
    }

    public Node get(String k) {
      if (!has(k)) throw error("missing required property '" + k + "'");
      return new Node(object().get(k), path + "." + k);
    }

    public Set<String> keys() {
      return Collections.unmodifiableSet(object().keySet());
    }

    public void only(String... keys) {
      Set<String> allowed = new HashSet<>(Arrays.asList(keys));
      for (String k : object().keySet())
        if (!allowed.contains(k)) throw get(k).error("unsupported property");
    }

    public String string() {
      if (!(value instanceof String)) throw error("expected string");
      return (String) value;
    }

    public String str(String k) {
      return get(k).string();
    }

    public String str(String k, String fallback) {
      return has(k) ? str(k) : fallback;
    }

    public boolean bool() {
      if (!(value instanceof Boolean)) throw error("expected boolean");
      return (Boolean) value;
    }

    public boolean bool(String k, boolean fallback) {
      return has(k) ? get(k).bool() : fallback;
    }

    public double number() {
      if (!(value instanceof Double)) throw error("expected number");
      return (Double) value;
    }

    public double num(String k, double fallback) {
      return has(k) ? get(k).number() : fallback;
    }

    public double range(double min, double max) {
      double n = number();
      if (n < min || n > max) throw error("must be in [" + min + ", " + max + "]");
      return n;
    }

    public int integer(int min, int max) {
      double n = range(min, max);
      if (n != Math.rint(n)) throw error("expected integer");
      return (int) n;
    }

    public float f(String k, float min, float max) {
      return (float) get(k).range(min, max);
    }

    public List<Node> list() {
      if (!(value instanceof List)) throw error("expected array");
      List<Node> result = new ArrayList<>();
      List<?> list = (List<?>) value;
      for (int i = 0; i < list.size(); i++) result.add(new Node(list.get(i), path + "[" + i + "]"));
      return result;
    }
  }
}
