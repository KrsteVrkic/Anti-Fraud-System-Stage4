package antifraud;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteResponse {

    private String userName;
    private String status;

    public DeleteResponse(String userName, String status) {
        this.userName = userName;
        this.status = status;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @JsonProperty("username")
    public String getUserName() {
        return userName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
