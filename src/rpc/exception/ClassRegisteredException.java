package rpc.exception;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class ClassRegisteredException extends Exception {

    public ClassRegisteredException() {
        super();
    }

    public ClassRegisteredException(String message) {
        super(message);
    }

    public ClassRegisteredException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClassRegisteredException(Throwable cause) {
        super(cause);
    }
}
