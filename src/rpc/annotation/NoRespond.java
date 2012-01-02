package rpc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicate that there will be no respond message after receiving this message. It can reduce the traffic.
 * After using this, the request will be forced to perform non-blocking request, with no expiry notification and receive no respond.
 * Note that if the return type is not null, this will lost effect.
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NoRespond {
}
