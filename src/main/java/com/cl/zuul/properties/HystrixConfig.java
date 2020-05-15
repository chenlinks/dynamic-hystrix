package com.cl.zuul.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author chenling
 * @date 2020/5/15  14:50
 * @since V1.0.0
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "hystrix")
public class HystrixConfig {


   private boolean enable = false;
}
