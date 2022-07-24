public class InvalidInputTxtException extends Exception {
    public InvalidInputTxtException(String message) {
        super(message);
    }
    public InvalidInputTxtException(String message, Exception exception) {
        super(message, exception);
    }
}
