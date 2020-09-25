package com.ffa.demo;

import java.lang.annotation.*;

/**
 * @author fanfangan
 * @desc MyHystrixCommand
 * @date 2020-09-25
 */



@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface MyHystrixCommand {

    int timeout() default 0;

    String fallback() default "";
}