package com.cl.zuul.service.impl;

import com.cl.zuul.entity.Payload;
import com.cl.zuul.entity.Person;
import com.cl.zuul.service.TestService;
import org.springframework.stereotype.Service;

/**
 * @author chenling
 * @date 2020/5/15  17:12
 * @since V1.0.0
 */
@Service
public class TestServiceImpl implements TestService {


    @Override
    public String test() {

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "chenling";
    }

    @Override
    public Payload testFind() {

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Person person = new Person();
        Payload Payload = new Payload();
        Payload.setContext(person);
        return Payload;
    }
}
