package antifraud;


import jakarta.validation.constraints.NotNull;

public class RegistrationRequest {

    @NotNull
    private String name;
    @NotNull
    private String username;
    @NotNull
    private String password;

    public RegistrationRequest() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
