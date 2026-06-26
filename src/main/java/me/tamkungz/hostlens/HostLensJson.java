package me.tamkungz.hostlens;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Iterator;
import java.util.Map;

final class HostLensJson {
    private HostLensJson() {
    }

    static String toJson(Object value) {
        StringBuilder builder = new StringBuilder();
        writeValue(builder, value);
        return builder.toString();
    }

    private static void writeValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
            return;
        }

        if (value instanceof String string) {
            writeString(builder, string);
            return;
        }

        if (value instanceof Character character) {
            writeString(builder, character.toString());
            return;
        }

        if (value instanceof Number number) {
            if (number instanceof Double d && !Double.isFinite(d)) {
                builder.append("null");
            } else if (number instanceof Float f && !Float.isFinite(f)) {
                builder.append("null");
            } else {
                builder.append(number);
            }
            return;
        }

        if (value instanceof Boolean bool) {
            builder.append(bool);
            return;
        }

        if (value instanceof Instant || value instanceof TemporalAccessor) {
            writeString(builder, value.toString());
            return;
        }

        if (value instanceof Map<?, ?> map) {
            writeMap(builder, map);
            return;
        }

        if (value instanceof Iterable<?> iterable) {
            writeIterable(builder, iterable);
            return;
        }

        Class<?> type = value.getClass();
        if (type.isRecord()) {
            writeRecord(builder, value, type);
            return;
        }

        writeString(builder, value.toString());
    }

    private static void writeMap(StringBuilder builder, Map<?, ?> map) {
        builder.append('{');
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            writeString(builder, String.valueOf(entry.getKey()));
            builder.append(':');
            writeValue(builder, entry.getValue());
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }
        builder.append('}');
    }

    private static void writeIterable(StringBuilder builder, Iterable<?> iterable) {
        builder.append('[');
        Iterator<?> iterator = iterable.iterator();
        while (iterator.hasNext()) {
            writeValue(builder, iterator.next());
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }
        builder.append(']');
    }

    private static void writeRecord(StringBuilder builder, Object value, Class<?> type) {
        builder.append('{');
        RecordComponent[] components = type.getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            writeString(builder, component.getName());
            builder.append(':');
            try {
                writeValue(builder, component.getAccessor().invoke(value));
            } catch (ReflectiveOperationException e) {
                writeValue(builder, null);
            }
            if (i + 1 < components.length) {
                builder.append(',');
            }
        }
        builder.append('}');
    }

    private static void writeString(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        builder.append('"');
    }
}
