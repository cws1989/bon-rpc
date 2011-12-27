package rpc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specify the request type id, the request type id should be unique among methods
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequestTypeId {

    /**
     * The request type id, should start from 1.
     */
    int value();
}
