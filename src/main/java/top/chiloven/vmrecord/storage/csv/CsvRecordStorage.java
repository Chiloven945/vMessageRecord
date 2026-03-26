package top.chiloven.vmrecord.storage.csv;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.slf4j.Logger;
import top.chiloven.vmrecord.config.PluginConfig;
import top.chiloven.vmrecord.model.RecordField;
import top.chiloven.vmrecord.storage.RecordStorage;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;

public final class CsvRecordStorage implements RecordStorage {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Logger logger;
    private final Path csvDirectory;
    private final Charset charset;
    private final boolean alwaysQuote;
    private final Object lock = new Object();

    private List<RecordField> fields;
    private CsvMapper mapper;
    private LocalDate openedDate;
    private Writer currentWriter;
    private SequenceWriter sequenceWriter;

    public CsvRecordStorage(Logger logger, Path dataDirectory, PluginConfig config) {
        this.logger = logger;
        this.csvDirectory = dataDirectory.resolve(config.storage.csv.directory);
        this.charset = Charset.forName(config.storage.csv.charset);
        this.alwaysQuote = config.storage.csv.alwaysQuote;
    }

    @Override
    public void initialize(List<RecordField> fields) throws IOException {
        this.fields = fields;
        this.mapper = new CsvMapper();
        if (alwaysQuote) {
            this.mapper.enable(CsvGenerator.Feature.ALWAYS_QUOTE_STRINGS);
        }
        Files.createDirectories(csvDirectory);
    }

    @Override
    public void write(LinkedHashMap<String, Object> values) throws IOException {
        synchronized (lock) {
            LocalDate today = LocalDate.now();
            ensureWriter(today);
            sequenceWriter.write(values);
            sequenceWriter.flush();
        }
    }

    private void ensureWriter(LocalDate targetDate) throws IOException {
        if (targetDate.equals(openedDate) && sequenceWriter != null) {
            return;
        }
        closeCurrentWriter();

        Path file = csvDirectory.resolve(FILE_DATE.format(targetDate) + ".csv");
        boolean writeHeader = Files.notExists(file) || Files.size(file) == 0L;
        CsvSchema.Builder builder = CsvSchema.builder();
        for (RecordField field : fields) {
            builder.addColumn(field.columnName());
        }
        CsvSchema schema = builder.build().withLineSeparator("\n");
        if (writeHeader) {
            schema = schema.withHeader();
        }

        currentWriter = Files.newBufferedWriter(
                file,
                charset,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE
        );
        ObjectWriter objectWriter = mapper.writer(schema);
        sequenceWriter = objectWriter.writeValues(currentWriter);
        openedDate = targetDate;
        logger.debug("CSV output -> {}", file.toAbsolutePath());
    }

    private void closeCurrentWriter() throws IOException {
        if (sequenceWriter != null) {
            sequenceWriter.flush();
            sequenceWriter.close();
            sequenceWriter = null;
        }
        if (currentWriter != null) {
            currentWriter.flush();
            currentWriter.close();
            currentWriter = null;
        }
        openedDate = null;
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            closeCurrentWriter();
        }
    }

}
