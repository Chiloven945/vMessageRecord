package top.chiloven.vmrecord.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RecordEntry {

    public RecordType type;
    public String timestamp;
    public String server;
    public String senderName;
    public String senderUuid;
    public String senderPrefix;
    public String senderSuffix;
    public String receiverName;
    public String receiverUuid;
    public String receiverPrefix;
    public String receiverSuffix;
    public String command;
    public String message;

}
