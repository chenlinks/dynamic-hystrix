package com.cl.zuul.entity;

import lombok.Data;

/**
 * @author chenling
 * @date 2020/5/15  18:22
 * @since V1.0.0
 */
@Data
public class Payload<T> {

    private String code;

    private String msg;

    private T context;
}
