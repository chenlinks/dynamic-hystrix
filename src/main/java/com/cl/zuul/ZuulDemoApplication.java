package com.cl.zuul;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.SpringCloudApplication;

@SpringCloudApplication
public class ZuulDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZuulDemoApplication.class, args);
    }

}
