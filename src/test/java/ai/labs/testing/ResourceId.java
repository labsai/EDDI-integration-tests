package ai.labs.testing;

/**
 * @author ginccc
 */
public class ResourceId {
    private String id;
    private Integer version;

    ResourceId(String id, Integer version) {
        this.id = id;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
