package rpc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicate it is a blocking request, this will block the calling thread until a respond received or expired.
 * If this annotation is not specified, it will perform non-blocking request.
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Blocking {

    /**
     * True to perform blocking request, false to perform non-blocking request.
     */
    boolean value();
}
