package top.chiloven.vmrecord.model;

public record PlayerMetaSnapshot(
        String prefix,
        String suffix
) {

    public static PlayerMetaSnapshot empty() {
        return new PlayerMetaSnapshot("", "");
    }

}
