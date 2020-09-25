package com.ffa.demo;

/**
 * @author fanfangan
 * @desc t
 * @date 2020-09-25
 */


import java.lang.reflect.Method;

public interface MethodInvocation {

    Method getMethod();

    Object[] getArguments();


}
