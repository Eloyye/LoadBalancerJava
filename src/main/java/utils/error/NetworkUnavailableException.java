package utils.error;

public class NetworkUnavailableException extends RuntimeException {
    public NetworkUnavailableException() {}


    public NetworkUnavailableException(String message) {
        super(message);
    }
    public NetworkUnavailableException(Throwable cause) {
        super(cause);
    }

    public NetworkUnavailableException(String message, Throwable cause) {
        super (message, cause);
    }
}
