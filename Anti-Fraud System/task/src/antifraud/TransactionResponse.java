package antifraud;

public class TransactionResponse {

    private String result;
    private String info;

    public void setResult(String result) {
        this.result = result;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public TransactionResponse() {}

    public String getResult() {
        return result;
    }
}
