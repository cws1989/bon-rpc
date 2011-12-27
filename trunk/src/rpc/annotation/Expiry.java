package rpc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Expiry time. If not specified, it will use the default expiry time.
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Expiry {

    /**
     * The default expiry time, in milli second
     */
    public static final int defaultExpiryTime = 10000;

    /**
     * Expiry time, in milli second
     */
    int value();
}
