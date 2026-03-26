package top.chiloven.vmrecord.storage.jdbc;

import org.slf4j.Logger;
import top.chiloven.vmrecord.config.PluginConfig;
import top.chiloven.vmrecord.model.RecordField;
import top.chiloven.vmrecord.storage.RecordStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public final class JdbcRecordStorage implements RecordStorage {

    private final Logger logger;
    private final Path dataDirectory;
    private final PluginConfig config;
    private final Dialect dialect;

    private Connection connection;
    private List<RecordField> fields;
    private String insertSql;

    public JdbcRecordStorage(Logger logger, Path dataDirectory, PluginConfig config, String dialectName) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.config = config;
        this.dialect = Dialect.from(dialectName);
    }

    @Override
    public void initialize(List<RecordField> fields) throws Exception {
        this.fields = List.copyOf(fields);
        loadDriver();
        this.connection = DriverManager.getConnection(buildJdbcUrl(), config.storage.database.username, config.storage.database.password);
        this.connection.setAutoCommit(true);
        ensureTable();
        this.insertSql = buildInsertSql();
    }

    @Override
    public void write(LinkedHashMap<String, Object> values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            int index = 1;
            for (RecordField field : fields) {
                Object value = values.get(field.columnName());
                statement.setString(index++, value == null ? "" : String.valueOf(value));
            }
            statement.executeUpdate();
        }
    }

    private void loadDriver() throws ClassNotFoundException {
        String configured = config.storage.database.driverClassName;
        String driver = configured == null || configured.isBlank() ? dialect.defaultDriverClass() : configured;
        Class.forName(driver);
    }

    private String buildJdbcUrl() {
        if (config.storage.database.jdbcUrl != null && !config.storage.database.jdbcUrl.isBlank()) {
            return config.storage.database.jdbcUrl;
        }
        return switch (dialect) {
            case SQLITE -> "jdbc:sqlite:" + dataDirectory.resolve(config.storage.database.sqlite.file).toAbsolutePath();
            case MYSQL -> {
                PluginConfig.Mysql mysql = config.storage.database.mysql;
                StringBuilder suffix = new StringBuilder();
                suffix.append("useSSL=").append(mysql.useSsl);
                if (mysql.parameters != null && !mysql.parameters.isBlank()) {
                    suffix.append('&').append(mysql.parameters);
                }
                yield "jdbc:mysql://" + mysql.host + ':' + mysql.port + '/' + mysql.database + '?' + suffix;
            }
            case H2 -> {
                PluginConfig.H2 h2 = config.storage.database.h2;
                String suffix = h2.parameters == null || h2.parameters.isBlank() ? "" : ';' + h2.parameters;
                yield "jdbc:h2:file:" + dataDirectory.resolve(h2.file).toAbsolutePath() + suffix;
            }
            case POSTGRESQL -> {
                PluginConfig.Postgresql postgresql = config.storage.database.postgresql;
                String suffix = postgresql.parameters == null || postgresql.parameters.isBlank() ? "" : "?" + postgresql.parameters;
                yield "jdbc:postgresql://" + postgresql.host + ':' + postgresql.port + '/' + postgresql.database + suffix;
            }
        };
    }

    private void ensureTable() throws SQLException {
        String table = config.storage.database.table;
        String createSql = "CREATE TABLE IF NOT EXISTS " + table + " (" + fields.stream()
                .map(field -> field.columnName() + " " + dialect.columnType(field))
                .collect(Collectors.joining(", ")) + ')';
        try (Statement statement = connection.createStatement()) {
            statement.execute(createSql);
        }

        Map<String, String> existingColumns = readExistingColumns(table);
        List<RecordField> missing = fields.stream()
                .filter(field -> !existingColumns.containsKey(field.columnName().toLowerCase(Locale.ROOT)))
                .toList();
        for (RecordField field : missing) {
            String alterSql = "ALTER TABLE " + table + " ADD COLUMN " + field.columnName() + ' ' + dialect.columnType(field);
            try (Statement statement = connection.createStatement()) {
                statement.execute(alterSql);
            }
        }
    }

    private Map<String, String> readExistingColumns(String table) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        Map<String, String> existing = new java.util.HashMap<>();
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, table, null)) {
            while (resultSet.next()) {
                existing.put(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT), resultSet.getString("TYPE_NAME"));
            }
        }
        return existing;
    }

    private String buildInsertSql() {
        String table = config.storage.database.table;
        List<String> columns = fields.stream().map(RecordField::columnName).toList();
        List<String> placeholders = new ArrayList<>();
        for (int index = 0; index < columns.size(); index++) {
            placeholders.add("?");
        }
        return "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + String.join(", ", placeholders) + ')';
    }

    @Override
    public void close() throws IOException {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new IOException("Failed to close JDBC connection", exception);
        }
    }

    private enum Dialect {
        SQLITE("org.sqlite.JDBC"),
        MYSQL("com.mysql.cj.jdbc.Driver"),
        H2("org.h2.Driver"),
        POSTGRESQL("org.postgresql.Driver");

        private final String defaultDriverClass;

        Dialect(String defaultDriverClass) {
            this.defaultDriverClass = defaultDriverClass;
        }

        static Dialect from(String raw) {
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "sqlite" -> SQLITE;
                case "mysql" -> MYSQL;
                case "h2" -> H2;
                case "postgresql" -> POSTGRESQL;
                default -> throw new IllegalArgumentException("Unsupported dialect: " + raw);
            };
        }

        String defaultDriverClass() {
            return defaultDriverClass;
        }

        String columnType(RecordField field) {
            if (field == RecordField.MESSAGE) {
                return switch (this) {
                    case MYSQL -> "LONGTEXT";
                    case SQLITE, H2, POSTGRESQL -> "TEXT";
                };
            }
            return switch (this) {
                case MYSQL, H2, POSTGRESQL -> "VARCHAR(255)";
                case SQLITE -> "TEXT";
            };
        }
    }

}
