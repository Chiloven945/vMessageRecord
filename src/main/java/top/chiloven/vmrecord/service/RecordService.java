package top.chiloven.vmrecord.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import top.chiloven.vmrecord.config.PluginConfig;
import top.chiloven.vmrecord.model.RecordEntry;
import top.chiloven.vmrecord.model.RecordField;
import top.chiloven.vmrecord.storage.RecordStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class RecordService implements AutoCloseable {

    private final Logger logger;
    private final RecordStorage storage;
    private final ObjectMapper mapper;
    private final ExecutorService executor;
    private final List<RecordField> fields;
    private volatile boolean closed;

    public RecordService(Logger logger, PluginConfig config, RecordStorage storage) {
        this.logger = logger;
        this.storage = storage;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vMessageRecord-Writer");
            thread.setDaemon(true);
            return thread;
        });
        this.fields = resolveFields(config);
    }

    public void initialize() throws Exception {
        storage.initialize(fields);
    }

    public void submit(RecordEntry entry) {
        if (closed) {
            return;
        }
        executor.execute(() -> {
            try {
                storage.write(selectFields(entry));
            } catch (Exception exception) {
                logger.error("Failed to persist record", exception);
            }
        });
    }

    public List<RecordField> fields() {
        return fields;
    }

    private LinkedHashMap<String, Object> selectFields(RecordEntry entry) {
        Map<String, Object> raw = mapper.convertValue(entry, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        LinkedHashMap<String, Object> selected = new LinkedHashMap<>();
        for (RecordField field : fields) {
            selected.put(field.columnName(), raw.getOrDefault(field.columnName(), ""));
        }
        return selected;
    }

    private List<RecordField> resolveFields(PluginConfig config) {
        List<RecordField> resolved = new ArrayList<>();
        List<String> configuredFields = config.recording.includedFields == null ? List.of() : config.recording.includedFields;
        for (String configuredField : configuredFields) {
            RecordField.from(configuredField).ifPresentOrElse(resolved::add,
                    () -> logger.warn("Ignoring unsupported field '{}'. Supported: {}",
                            configuredField, RecordField.supportedNames()));
        }
        if (resolved.isEmpty()) {
            resolved.addAll(List.of(RecordField.values()));
        }
        return List.copyOf(resolved);
    }

    @Override
    public void close() throws IOException {
        closed = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        storage.close();
    }

}
