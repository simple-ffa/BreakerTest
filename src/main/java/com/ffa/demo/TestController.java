package com.ffa.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

import static sun.print.CUPSPrinter.getPort;

/**
 * @author fanfangan
 * @desc TestController
 * @date 2020-09-25
 */

@RestController
public class TestController {
    private final static Random random = new Random();

    @MyHystrixCommand(timeout = 1000, fallback = "callable")
    @GetMapping("/test")
    public Result test(@RequestParam("message") String message) throws Exception {
        Result result = new Result();
        String string = await(message);

        boolean res = random.nextBoolean();
        result.setCode(res ? 200 : 500);
        result.setData(string);
        return result;
    }
    @MyHystrixCommand(timeout = 1000, fallback = "callable")
    @GetMapping("/test1")
    public Result test1(@RequestParam("message") String message) throws Exception {
        Result result = new Result();
        String string = await(message);

        boolean res = random.nextBoolean();
        result.setCode(res ? 200 : 500);
        result.setData(string);
        return result;
    }

    public Result callable(String message) {
        Result result = new Result();
        String string = " system busy";
        result.setCode(200);
        result.setData(string);
        return result;
    }

    private String await(String message) throws InterruptedException {
        int value = random.nextInt(200);
        System.out.println("say() cost " + value + "ms");
        Thread.sleep(value);
        System.out.println("port:" + getPort() + ",接收到消息-say:" + message);
        return "port:" + getPort() + ",Hello," + message;
    }

}
