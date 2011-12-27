package rpc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicating not to use request id to shorten 1-4 bytes for each request pack. Usually for short and large amount of requests.
 * After using this, the request will be forced to perform non-blocking request, with no expiry notification and receive no respond.
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NoRequestId {
}
