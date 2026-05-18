package org.hatrack.frauholle.eodhd.internal;

import org.hatrack.frauholle.eodhd.JsonParseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal recursive-descent JSON parser (JDK-only). Produces a generic value
 * graph: object → {@code Map<String,Object>}, array → {@code List<Object>},
 * string → {@code String}, number → {@code String} (raw text, never a
 * {@code double}), boolean → {@code Boolean}, null → {@code null}.
 */
public final class JsonParser {

    private final String text;
    private int pos;

    private JsonParser(String text) {
        this.text = text;
    }

    public static Object parse(String json) {
        if (json == null) {
            throw new JsonParseException("JSON input is null");
        }
        JsonParser parser = new JsonParser(json);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos != parser.text.length()) {
            throw new JsonParseException("unexpected trailing content at position " + parser.pos);
        }
        return value;
    }

    private Object parseValue() {
        if (pos >= text.length()) {
            throw new JsonParseException("unexpected end of input");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield parseNumber();
                }
                throw new JsonParseException("unexpected character '" + c + "' at position " + pos);
            }
        };
    }

    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new JsonParseException("expected a string key at position " + pos);
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, parseValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == '}') {
                pos++;
                return object;
            } else {
                throw new JsonParseException("expected ',' or '}' at position " + pos);
            }
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> array = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(parseValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == ']') {
                pos++;
                return array;
            } else {
                throw new JsonParseException("expected ',' or ']' at position " + pos);
            }
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw new JsonParseException("unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return builder.toString();
            }
            if (c == '\\') {
                if (pos >= text.length()) {
                    throw new JsonParseException("unterminated escape sequence");
                }
                char escape = text.charAt(pos++);
                switch (escape) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw new JsonParseException("truncated unicode escape");
                        }
                        try {
                            builder.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException e) {
                            throw new JsonParseException("invalid unicode escape at position " + pos);
                        }
                        pos += 4;
                    }
                    default -> throw new JsonParseException("invalid escape '\\" + escape + "'");
                }
            } else {
                builder.append(c);
            }
        }
    }

    private String parseNumber() {
        int start = pos;
        while (pos < text.length() && "+-.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String number = text.substring(start, pos);
        if (number.isEmpty()) {
            throw new JsonParseException("invalid number at position " + start);
        }
        return number;
    }

    private Boolean parseBoolean() {
        if (text.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new JsonParseException("invalid literal at position " + pos);
    }

    private Object parseNull() {
        if (text.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new JsonParseException("invalid literal at position " + pos);
    }

    private void expect(char expected) {
        if (pos >= text.length() || text.charAt(pos) != expected) {
            throw new JsonParseException("expected '" + expected + "' at position " + pos);
        }
        pos++;
    }

    private char peek() {
        if (pos >= text.length()) {
            throw new JsonParseException("unexpected end of input");
        }
        return text.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }
}
