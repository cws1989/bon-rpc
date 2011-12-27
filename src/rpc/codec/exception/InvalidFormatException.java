package rpc.codec.exception;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class InvalidFormatException extends Exception {

    private static final long serialVersionUID = 198902111L;

    public InvalidFormatException() {
        super();
    }

    public InvalidFormatException(String message) {
        super(message);
    }

    public InvalidFormatException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidFormatException(Throwable cause) {
        super(cause);
    }
}
