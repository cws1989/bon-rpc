package rpc.exception;

/**
 * Request expired exception.
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class RequestExpiredException extends RuntimeException {

    private static final long serialVersionUID = 198902110L;

    public RequestExpiredException() {
        super();
    }

    public RequestExpiredException(String message) {
        super(message);
    }

    public RequestExpiredException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequestExpiredException(Throwable cause) {
        super(cause);
    }
}
