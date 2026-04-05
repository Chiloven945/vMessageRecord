package top.chiloven.vmrecord.storage.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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

    private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 10_000L;
    private static final long DEFAULT_VALIDATION_TIMEOUT_MS = 5_000L;
    private static final long DEFAULT_IDLE_TIMEOUT_MS = 600_000L;
    private static final long DEFAULT_MAX_LIFETIME_MS = 1_800_000L;
    private static final long DEFAULT_KEEPALIVE_TIME_MS = 0L;

    private final Logger logger;
    private final Path dataDirectory;
    private final PluginConfig config;
    private final Dialect dialect;

    private HikariDataSource dataSource;
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
        this.dataSource = createDataSource();
        try (Connection connection = dataSource.getConnection()) {
            ensureTable(connection);
        }
        this.insertSql = buildInsertSql();
    }

    @Override
    public void write(LinkedHashMap<String, Object> values) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertSql)) {
            int index = 1;
            for (RecordField field : fields) {
                Object value = values.get(field.columnName());
                statement.setString(index++, value == null ? "" : String.valueOf(value));
            }
            statement.executeUpdate();
        }
    }

    private HikariDataSource createDataSource() throws ClassNotFoundException {
        String jdbcUrl = buildJdbcUrl();
        String configured = config.storage.database.driverClassName;
        String driverClassName = resolveDriverClassName(configured == null || configured.isBlank()
                ? dialect.defaultDriverClass()
                : configured);

        Class.forName(driverClassName);

        PluginConfig.Hikari pool = config.storage.database.hikari;
        int maximumPoolSize = Math.max(1, pool.maximumPoolSize);
        int minimumIdle = Math.clamp(pool.minimumIdle, 0, maximumPoolSize);

        if (dialect == Dialect.SQLITE) {
            maximumPoolSize = 1;
            minimumIdle = 1;
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("vMessageRecord-" + dialect.name().toLowerCase(Locale.ROOT));
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setDriverClassName(driverClassName);
        hikariConfig.setUsername(config.storage.database.username);
        hikariConfig.setPassword(config.storage.database.password);
        hikariConfig.setAutoCommit(true);
        hikariConfig.setMaximumPoolSize(maximumPoolSize);
        hikariConfig.setMinimumIdle(minimumIdle);
        hikariConfig.setConnectionTimeout(sanitizeDuration(pool.connectionTimeoutMs, DEFAULT_CONNECTION_TIMEOUT_MS));
        hikariConfig.setValidationTimeout(sanitizeDuration(pool.validationTimeoutMs, DEFAULT_VALIDATION_TIMEOUT_MS));
        hikariConfig.setIdleTimeout(sanitizeDuration(pool.idleTimeoutMs, DEFAULT_IDLE_TIMEOUT_MS));
        hikariConfig.setMaxLifetime(sanitizeDuration(pool.maxLifetimeMs, DEFAULT_MAX_LIFETIME_MS));

        long keepaliveTime = sanitizeDurationAllowZero(pool.keepaliveTimeMs, DEFAULT_KEEPALIVE_TIME_MS);
        if (keepaliveTime > 0) {
            hikariConfig.setKeepaliveTime(keepaliveTime);
        }

        if (pool.initializationFailTimeoutMs >= -1L) {
            hikariConfig.setInitializationFailTimeout(pool.initializationFailTimeoutMs);
        }
        if (pool.leakDetectionThresholdMs > 0L) {
            hikariConfig.setLeakDetectionThreshold(pool.leakDetectionThresholdMs);
        }
        if (pool.connectionTestQuery != null && !pool.connectionTestQuery.isBlank()) {
            hikariConfig.setConnectionTestQuery(pool.connectionTestQuery);
        }

        if (dialect == Dialect.SQLITE) {
            hikariConfig.addDataSourceProperty("journal_mode", "WAL");
            hikariConfig.addDataSourceProperty("busy_timeout", "5000");
        }

        logger.info("Using JDBC pool '{}': driver={}, url={}, maxPoolSize={}, minimumIdle={}",
                hikariConfig.getPoolName(), driverClassName, maskJdbcUrl(jdbcUrl), maximumPoolSize, minimumIdle);

        return new HikariDataSource(hikariConfig);
    }

    private long sanitizeDuration(long configured, long fallback) {
        return configured > 0L ? configured : fallback;
    }

    private long sanitizeDurationAllowZero(long configured, long fallback) {
        return configured >= 0L ? configured : fallback;
    }

    private String resolveDriverClassName(String driver) {
        return switch (driver) {
            case "com.mysql.cj.jdbc.Driver", "top.chiloven.vmrecord.libs.mysql.cj.jdbc.Driver" ->
                    "top.chiloven.vmrecord.libs.mysql.cj.jdbc.Driver";
            case "org.h2.Driver", "top.chiloven.vmrecord.libs.h2.Driver" -> "top.chiloven.vmrecord.libs.h2.Driver";
            case "org.postgresql.Driver", "top.chiloven.vmrecord.libs.postgresql.Driver" ->
                    "top.chiloven.vmrecord.libs.postgresql.Driver";
            case "org.sqlite.JDBC" -> "org.sqlite.JDBC";
            default -> driver;
        };
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

    private void ensureTable(Connection connection) throws SQLException {
        String table = config.storage.database.table;
        String createSql = "CREATE TABLE IF NOT EXISTS " + table + " (" + fields.stream()
                .map(field -> field.columnName() + " " + dialect.columnType(field))
                .collect(Collectors.joining(", ")) + ')';
        try (Statement statement = connection.createStatement()) {
            statement.execute(createSql);
        }

        Map<String, String> existingColumns = readExistingColumns(connection, table);
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

    private Map<String, String> readExistingColumns(Connection connection, String table) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        Map<String, String> existing = new HashMap<>();
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

    private String maskJdbcUrl(String jdbcUrl) {
        int questionMarkIndex = jdbcUrl.indexOf('?');
        if (questionMarkIndex < 0) {
            return jdbcUrl;
        }
        return jdbcUrl.substring(0, questionMarkIndex) + "?...";
    }

    @Override
    public void close() throws IOException {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private enum Dialect {
        SQLITE("org.sqlite.JDBC"),
        MYSQL("top.chiloven.vmrecord.libs.mysql.cj.jdbc.Driver"),
        H2("top.chiloven.vmrecord.libs.h2.Driver"),
        POSTGRESQL("top.chiloven.vmrecord.libs.postgresql.Driver");

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
