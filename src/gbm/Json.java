package gbm;
import java.util.*;
/** Minimal JSON codec. No Java object deserialization on the network. */
public final class Json {
  public static Map < String, Object > obj(Object...kv) {
    Map < String, Object > m = new LinkedHashMap < >();
    for (int i = 0; i < kv.length; i += 2) m.put(kv [i].toString(), kv [i + 1]);
    return m;
  }
  @SuppressWarnings("unchecked") public static Map < String, Object > map(Object x) {
    return x instanceof Map ?(Map < String, Object >) x : new LinkedHashMap < >();
  }
  @SuppressWarnings("unchecked") public static List < Object > list(Object x) {
    return x instanceof List ?(List < Object >) x : new ArrayList < >();
  }
  public static String str(Map < String, Object > m, String k) {
    return Objects.toString(m.get(k), "");
  }
  public static long num(Map < String, Object > m, String k) {
    Object v = m.get(k);
    return v instanceof Number ?((Number) v).longValue() : 0;
  }
  public static boolean yes(Map < String, Object > m, String k) {
    return Boolean.TRUE.equals(m.get(k));
  }
  public static String write(Object x) {
    if (x == null) return "null";
    if (x instanceof Boolean || x instanceof Number) return x.toString();
    if (x instanceof Map < ?, ? > m) {
      StringJoiner s = new StringJoiner(",", "{", "}");
      m.forEach((k, v) -> s.add(write(k.toString()) + ":" + write(v)));
      return s.toString();
    }
    if (x instanceof Collection < ? > c) {
      StringJoiner s = new StringJoiner(",", "[", "]");
      c.forEach(v -> s.add(write(v)));
      return s.toString();
    }
    StringBuilder b = new StringBuilder("\"");
    for (char c : x.toString().toCharArray()) switch (c) {
      case '"' -> b.append("\\\"");
      case '\\' -> b.append("\\\\");
      case '\n' -> b.append("\\n");
      case '\r' -> b.append("\\r");
      case '\t' -> b.append("\\t");
      default -> {
        if (c < 32) b.append(String.format("\\u%04x", (int) c));
        else b.append(c);
      }
    }
    return b.append('"').toString();
  }
  public static Object read(String s) {
    Parser p = new Parser(s);
    Object v = p.value();
    p.ws();
    if (p.i != s.length()) throw new IllegalArgumentException("JSON non valido");
    return v;
  }
  static class Parser {
    String s;
    int i;
    int depth;
    Parser(String s) {
      this.s = s;
    }
    void ws() {
      while (i < s.length() && Character.isWhitespace(s.charAt(i))) i ++;
    }
    char take() {
      if (i >= s.length()) throw new IllegalArgumentException("JSON incompleto");
      return s.charAt(i ++);
    }
    Object value() {
      if(++depth>64) throw new IllegalArgumentException("JSON troppo annidato");
      try{return parseValue();}finally{depth--;}
    }
    Object parseValue() {
      ws();
      if (i >= s.length()) throw new IllegalArgumentException("JSON vuoto");
      char c = s.charAt(i);
      if (c == '"') return string();
      if (c == '{') {
        i ++;
        Map < String, Object > m = new LinkedHashMap < >();
        ws();
        if (s.charAt(i) == '}') {
          i ++;
          return m;
        }
        while (true) {
          ws();
          String k = string();
          ws();
          if (take() != ':') throw new IllegalArgumentException("JSON");
          m.put(k, value());
          ws();
          char end = take();
          if (end == '}') return m;
          if (end != ',') throw new IllegalArgumentException("JSON");
        }
      }
      if (c == '[') {
        i ++;
        List < Object > a = new ArrayList < >();
        ws();
        if (s.charAt(i) == ']') {
          i ++;
          return a;
        }
        while (true) {
          a.add(value());
          ws();
          char e = take();
          if (e == ']') return a;
          if (e != ',') throw new IllegalArgumentException("JSON");
        }
      }
      for (String t : List.of("true", "false", "null")) if (s.startsWith(t, i)) {
        i += t.length();
        return t.equals("null") ? null : t.equals("true");
      }
      int st = i;
      while (i < s.length() && "-+0123456789.eE".indexOf(s.charAt(i)) >= 0) i ++;
      if (st == i) throw new IllegalArgumentException("JSON");
      String n = s.substring(st, i);
      if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.valueOf(n);
      return Long.valueOf(n);
    }
    String string() {
      if (take() != '"') throw new IllegalArgumentException("JSON stringa");
      StringBuilder b = new StringBuilder();
      while (true) {
        char c = take();
        if (c == '"') return b.toString();
        if (c == '\\') {
          c = take();
          switch (c) {
            case 'n' -> b.append('\n');
            case 'r' -> b.append('\r');
            case 't' -> b.append('\t');
            case 'b' -> b.append('\b');
            case 'f' -> b.append('\f');
            case 'u' -> {
              b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
              i += 4;
            }
            case '"', '\\', '/' -> b.append(c);
            default -> throw new IllegalArgumentException("Escape JSON");
          }
        } else {
          if (c < 32) throw new IllegalArgumentException("Controllo JSON");
          b.append(c);
        }
      }
    }
  }
}
