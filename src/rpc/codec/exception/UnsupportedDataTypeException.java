package rpc.codec.exception;

/**
 * Thrown when there is any data type not supported by the generator or parser.
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class UnsupportedDataTypeException extends Exception {

    private static final long serialVersionUID = 198902111L;

    public UnsupportedDataTypeException() {
        super();
    }

    public UnsupportedDataTypeException(String message) {
        super(message);
    }

    public UnsupportedDataTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsupportedDataTypeException(Throwable cause) {
        super(cause);
    }
}
