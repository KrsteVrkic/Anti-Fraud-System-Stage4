package antifraud;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class SusIpAddress {

    @Id
    @GeneratedValue()
    private long id;
    private String ip;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
