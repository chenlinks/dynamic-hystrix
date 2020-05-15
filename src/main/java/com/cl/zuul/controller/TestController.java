package com.cl.zuul.controller;

import com.cl.zuul.entity.Person;
import com.cl.zuul.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author chenling
 * @date 2020/5/15  17:19
 * @since V1.0.0
 */
@RestController
@RequestMapping
public class TestController {

    @Autowired
    private TestService testService;

    @GetMapping("/test")
    public String test(){

        return testService.test();
    }

    @GetMapping("/find")
    public Object testFind(){
        return testService.testFind();
    }
}
