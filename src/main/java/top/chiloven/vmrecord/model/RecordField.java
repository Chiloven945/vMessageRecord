package top.chiloven.vmrecord.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum RecordField {

    TYPE("type"),
    TIMESTAMP("timestamp"),
    SERVER("server"),
    SENDER_NAME("sender_name"),
    SENDER_UUID("sender_uuid"),
    SENDER_PREFIX("sender_prefix"),
    SENDER_SUFFIX("sender_suffix"),
    RECEIVER_NAME("receiver_name"),
    RECEIVER_UUID("receiver_uuid"),
    RECEIVER_PREFIX("receiver_prefix"),
    RECEIVER_SUFFIX("receiver_suffix"),
    COMMAND("command"),
    MESSAGE("message");

    private final String columnName;

    RecordField(String columnName) {
        this.columnName = columnName;
    }

    public static Optional<RecordField> from(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(field -> field.columnName.equals(normalized))
                .findFirst();
    }

    public static List<String> supportedNames() {
        return Arrays.stream(values()).map(RecordField::columnName).toList();
    }

    public String columnName() {
        return columnName;
    }

}
