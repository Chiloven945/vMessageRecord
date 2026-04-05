package top.chiloven.vmrecord.config;

import java.util.ArrayList;
import java.util.List;

public final class PluginConfig {

    public Recording recording = new Recording();
    public Storage storage = new Storage();

    public static final class Recording {

        public boolean recordChat = true;
        public boolean recordPrivateMessages = true;
        public String timeFormat = "yyyy-MM-dd HH:mm:ss";
        public List<String> includedFields = new ArrayList<>(List.of(
                "type",
                "timestamp",
                "server",
                "sender_name",
                "sender_uuid",
                "sender_prefix",
                "sender_suffix",
                "receiver_name",
                "receiver_uuid",
                "receiver_prefix",
                "receiver_suffix",
                "command",
                "message"
        ));

    }

    public static final class Storage {

        public String type = "csv";
        public Csv csv = new Csv();
        public Database database = new Database();

    }

    public static final class Csv {

        public String directory = "records";
        public String charset = "UTF-8";
        public boolean alwaysQuote = true;

    }

    public static final class Database {

        public String table = "vmessage_records";
        public String jdbcUrl = "";
        public String username = "";
        public String password = "";
        public String driverClassName = "";
        public Hikari hikari = new Hikari();
        public Sqlite sqlite = new Sqlite();
        public Mysql mysql = new Mysql();
        public H2 h2 = new H2();
        public Postgresql postgresql = new Postgresql();

    }

    public static final class Hikari {

        public int maximumPoolSize = 4;
        public int minimumIdle = 1;
        public long connectionTimeoutMs = 10000L;
        public long validationTimeoutMs = 5000L;
        public long idleTimeoutMs = 600000L;
        public long maxLifetimeMs = 1800000L;
        public long keepaliveTimeMs = 0L;
        public long initializationFailTimeoutMs = 1L;
        public long leakDetectionThresholdMs = 0L;
        public String connectionTestQuery = "";

    }

    public static final class Sqlite {

        public String file = "records.db";

    }

    public static final class Mysql {

        public String host = "127.0.0.1";
        public int port = 3306;
        public String database = "vmessage_record";
        public boolean useSsl = false;
        public String parameters = "allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";

    }

    public static final class H2 {

        public String file = "records";
        public String parameters = "MODE=MySQL;DATABASE_TO_LOWER=TRUE";

    }

    public static final class Postgresql {

        public String host = "127.0.0.1";
        public int port = 5432;
        public String database = "vmessage_record";
        public String parameters = "stringtype=unspecified";

    }

}
