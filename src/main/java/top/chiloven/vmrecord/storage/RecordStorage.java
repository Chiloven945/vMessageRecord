package top.chiloven.vmrecord.storage;

import top.chiloven.vmrecord.model.RecordField;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

public interface RecordStorage extends AutoCloseable {

    void initialize(List<RecordField> fields) throws Exception;

    void write(LinkedHashMap<String, Object> values) throws Exception;

    @Override
    void close() throws IOException;

}
