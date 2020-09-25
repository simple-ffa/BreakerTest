package com.ffa.demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * @author fanfangan
 * @desc CircuitBreaker
 * @date 2020-09-24
 */
@Component
@Aspect
public class CircuitBreaker {

    //调用次数
    private int invokeThreshold = 10;
    //失败阈值
    private int failedThreshold = 5;
    //熔断重试时间间隔（毫秒）
    private long retryInterval = 10000;

   //存储每个方法的返回结果已经熔断信息
    private ConcurrentHashMap<String, MethodResult> methodResultConcurrentHashMap = new ConcurrentHashMap<>();

    private ExecutorService executorService = Executors.newFixedThreadPool(10);

    class MethodResult {
        //方法的返回结果
        private Queue<Boolean> resultQueue = new LinkedList<>();
        private int invokeCount;
        private int failedCount;
        //熔断开关
        private Boolean invokeCallBack = false;
        //熔断开始时间
        private Long startInvokeCallBack = 0L;


        synchronized public void addResult(Boolean result) {

            if (resultQueue.size() == invokeThreshold) {
                Boolean remove = resultQueue.poll();
                if (!remove) {
                    failedCount++;
                }
            } else {
                invokeCount++;
            }
            if (!result) {
                failedCount++;
            }
            if (invokeCount == invokeThreshold && failedCount >= failedThreshold) {
                invokeCallBack = true;
                startInvokeCallBack = System.currentTimeMillis();
            }
        }

        synchronized public void clear() {
            invokeCount = 0;
            failedCount = 0;
            invokeCallBack = false;
            startInvokeCallBack = 0L;
            resultQueue.clear();
        }

        @Override
        public String toString() {
            return "MethodResult{" +
                    "resultQueue=" + resultQueue +
                    ", invokeCount=" + invokeCount +
                    ", failedCount=" + failedCount +
                    ", invokeCallBack=" + invokeCallBack +
                    ", startInvokeCallBack=" + startInvokeCallBack +
                    '}';
        }
    }


    @Pointcut("@annotation(com.ffa.demo.MyHystrixCommand)")
    void anyRPCAnnotatedMethodCall() {
    }

    @Around("anyRPCAnnotatedMethodCall()")
    public Object executeAnnotatedMethod(ProceedingJoinPoint aJoinPoint) throws Throwable {
//        System.out.println(methodResultConcurrentHashMap.size());
//        methodResultConcurrentHashMap.values().stream().forEach(item -> System.out.println(item.toString()));
        BeforeAdviceMethodInvocationAdapter mi = BeforeAdviceMethodInvocationAdapter.createFrom(aJoinPoint);
        Method method = mi.getMethod();
        Object[] args = mi.getArguments();
        Result res;
        String signature = aJoinPoint.getSignature().toString();
        if (method.isAnnotationPresent(MyHystrixCommand.class)) {
            MyHystrixCommand annotation = method.getAnnotation(MyHystrixCommand.class);
            int timeout = annotation.timeout();
            String fallback = annotation.fallback();

            Boolean invokeCallBack = false;
            MethodResult methodResult = methodResultConcurrentHashMap.get(signature);
            if (methodResult != null) {
                invokeCallBack = methodResult.invokeCallBack;
                if (invokeCallBack && System.currentTimeMillis() - methodResult.startInvokeCallBack > retryInterval) {
                    invokeCallBack = false;
                    methodResult.clear();
                }
            }
            methodResult = methodResult == null ? new MethodResult() : methodResult;

            if (!invokeCallBack) {
                Future<Result> future = executorService.submit(() -> {
                    Result returnValue;
                    try {
                        returnValue = (Result) aJoinPoint.proceed(args);
                    } catch (Throwable throwable) {
                        throw new Exception(throwable);
                    }
                    return returnValue;
                });
                //超时时间
                try {
                    res = future.get(timeout, TimeUnit.MILLISECONDS);
                } catch (InterruptedException | TimeoutException e) {
                    future.cancel(true);
                    methodResult.addResult(false);
                    methodResultConcurrentHashMap.put(signature, methodResult);
                    return invokeFallbackMethod(method, aJoinPoint.getTarget(), fallback, args);

                }
            } else {
                return invokeFallbackMethod(method, aJoinPoint.getTarget(), fallback, args);
            }

            Boolean result = res != null && res.getCode() == 200;
            methodResult.addResult(result);
            methodResultConcurrentHashMap.put(signature, methodResult);
            return res;

        }
        return null;
    }

    private Result invokeFallbackMethod(Method method, Object bean, String fallback, Object[] arguments) throws Exception {
        // 查找 fallback 方法
        Method fallbackMethod = findFallbackMethod(method, bean, fallback);
        return (Result) fallbackMethod.invoke(bean, arguments);
    }

    private Method findFallbackMethod(Method method, Object bean, String fallbackMethodName) throws
            NoSuchMethodException {
        // 通过被拦截方法的参数类型列表结合方法名，从同一类中找到 fallback 方法
        Class beanClass = bean.getClass();
        Method fallbackMethod = beanClass.getMethod(fallbackMethodName, method.getParameterTypes());
        return fallbackMethod;
    }

    @PreDestroy
    private void destroy() {
        executorService.shutdown();
    }
}