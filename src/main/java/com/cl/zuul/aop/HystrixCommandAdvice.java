package com.cl.zuul.aop;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cl.zuul.properties.HystrixConfig;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Iterator;

/**
 * @author chenling
 * @date 2020/5/15  14:08
 * @since V1.0.0
 */

@Component
@Aspect
@Slf4j
public class HystrixCommandAdvice {

    // 服务降级方法配置前缀
    private static String HYSTRIX_METHOD_PREFIX="hystrix.method.";

    // 服务降级方法超时时间配置前缀
    private static String HYSTRIX_TIMEOUT_PREFIX="hystrix.timeout.";

    // 服务降级方法并发数配置前缀
    private static String HYSTIRX_REQUEST_SIZE_PREFIX="hystrix.requestSize.";

    // 服务降级方法策略配置前缀 使用信号量还是线程池
    private static String HYSTRIX_ISOLATION_STRATEGE_PREFIX = "hystrix.isolationStrategy.";

    // 服务降级方法返回类型配置前缀
    private static String HYSTRIX_DEFAULT_RETURN_PREFIX="hystrix.default.return.";


    @Autowired
    private HystrixConfig hystrixConfig;


    @Autowired
    private Environment environment;



    // 定义切点，拦截某些特定第三方服务
    @Pointcut("execution(* com.cl.zuul.service.*.*(..))")
    public void hystrixPointcut(){}

    /**
     * 切面
     */
    @Around("hystrixPointcut()")
    public Object runCommand(final ProceedingJoinPoint pJoinPoint)  throws Throwable {

        log.info("\n\n-------------------- 正在执行切面---------------------------");
        // 获取服务签名 public xxxx.returnObject.class. com.xxxx.serice.method()
        String signature = pJoinPoint.getSignature().toLongString();
        String serviceMethod = signature.substring(signature.lastIndexOf(" ")+1,signature.lastIndexOf("("));

//        DynamicPropertyFactory factory = DynamicPropertyFactory.getInstance();
//        boolean hystrixServiceBoolean = factory.getBooleanProperty(HYSTRIX_METHOD_PREFIX+serviceMethod, false).get();
//
        Boolean requiredProperty = environment.getProperty(HYSTRIX_METHOD_PREFIX + serviceMethod, Boolean.class,false);
        // 降级开关打开状态并且配置此业务方法的降级方案
        // iSystemSettingService为我们自己定义的字典表，保存业务的开关和配置信息
        if(requiredProperty && hystrixConfig.isEnable()){
            // 使用Hystrix服务降级封装的方法，具体代码下面有示例
            return wrapWithHystrixCommnad(pJoinPoint).execute();
        }
        // 没有配置此业务方法的降级方案，则直接执行
        try{
            return pJoinPoint.proceed();
        }catch (Throwable throwable){
            throw (Exception) throwable;
        }
    }


    /**
     * 使用hystrix封装业务执行的方法
     * @param pJoinPoint
     * @return
     */
      private HystrixCommand<Object> wrapWithHystrixCommnad(final ProceedingJoinPoint  pJoinPoint){
          // 获取降级服务的方法签名
          //如：public xxxx.return.class com.xxxx.serice.method()
          String signature = pJoinPoint.getSignature().toLongString();
          // 解析出方法签名中返回的类型 如：xxxx.return.class
          String returnClass = signature.substring(signature.indexOf(" ")+1,signature.lastIndexOf(" "));
          // 解析出就去签名中的方法 如：com.xxxx.serice.method
          String serviceMethod = signature.substring(signature.lastIndexOf(" ")+1,signature.lastIndexOf("("));
          // 获取配置文件中的配置数据，
          DynamicPropertyFactory dynamicPropertyFactory = DynamicPropertyFactory.getInstance();
          // 超时时间 默认5秒
          int timeout = dynamicPropertyFactory.getIntProperty(HYSTRIX_TIMEOUT_PREFIX+serviceMethod, 5000).get();

          log.info("返回值：{}",HYSTRIX_DEFAULT_RETURN_PREFIX+serviceMethod);

          // 返回值类型 默认空即void
          String defaultReturn = dynamicPropertyFactory.getStringProperty(HYSTRIX_DEFAULT_RETURN_PREFIX+serviceMethod,"").get();

          // 请求数大小 默认50个 只有在策略使用信号量时再会用到
          int  requestSize= dynamicPropertyFactory.getIntProperty(HYSTIRX_REQUEST_SIZE_PREFIX+serviceMethod, 50).get();

          // 隔离策略
          String strategy= dynamicPropertyFactory .getStringProperty(HYSTRIX_ISOLATION_STRATEGE_PREFIX+serviceMethod, "THREAD").get();

          String declaringTypeName = pJoinPoint.getSignature().getDeclaringTypeName();

          String commandKey = pJoinPoint.getSignature().getName();
          String commonGroupKey = declaringTypeName.substring(declaringTypeName.lastIndexOf(".")+1)+"."+commandKey;

          return new HystrixCommand<Object>(setter(commonGroupKey,commandKey,timeout,requestSize,strategy)) {

              @Override
              protected Object run() throws Exception {
                  try {
                      Object object  = pJoinPoint.proceed();
                      return object;
                      } catch (Throwable throwable) {
                      throw (Exception) throwable;
                     }
                  }


              /**
               * 以下四种情况将触发getFallback调用：
               * 1）run()方法抛出非HystrixBadRequestException异常
               * 2）run()方法调用超时
               * 3）断路器开启拦截调用
               * 4）线程池/队列/信号量是否跑满
               * 实现getFallback()后，执行命令时遇到以上4种情况将被fallback接管，
               * 不会抛出异常或其他
               */
              @Override
              protected Object getFallback() {

                        log.info("服务触发了降级 {} 超时时间 {} 方法返回类型 {} 是否启动了熔断 {}",serviceMethod,timeout,returnClass,this.isCircuitBreakerOpen());
                      try{
                              return generateClass(returnClass,defaultReturn);
                          }
                     catch (Exception e){
                              log.error("生成降级返回对象异常",e);
                          }
                      return  null;
                    }
               };
      }

    // hystrix参数设置
    private HystrixCommand.Setter setter(String commonGroupKey,String commandKey ,int timeout,int requestSize,String strategy) {

          // 使用信号量
        if("SEMAPHORE".equalsIgnoreCase(strategy)){
            return HystrixCommand.Setter
                    .withGroupKey(HystrixCommandGroupKey.Factory
                            .asKey(commonGroupKey==null?"commonGroupKey":commonGroupKey))
                    .andCommandKey(HystrixCommandKey.Factory
                            .asKey(commandKey==null?"commandKey":commandKey))
            // 信号量
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                            .withExecutionIsolationStrategy(HystrixCommandProperties
                                    .ExecutionIsolationStrategy.SEMAPHORE)
                            // 并发数量
                            .withExecutionIsolationSemaphoreMaxConcurrentRequests(requestSize)
                            .withExecutionTimeoutInMilliseconds(timeout));

        }else{
            // 使用线程池
            return HystrixCommand.Setter
                    .withGroupKey(HystrixCommandGroupKey.Factory
                            .asKey(commonGroupKey==null?"commonGroupKey":commonGroupKey))
                    .andCommandKey(HystrixCommandKey.Factory
                            .asKey(commandKey==null?"commandKey":commandKey))
            // 线程池大小
                    .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter()
                            .withCoreSize(requestSize))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                            // 线程池
                            .withExecutionIsolationStrategy(HystrixCommandProperties
                                    .ExecutionIsolationStrategy.THREAD)
                            // 超时时间
                            .withExecutionTimeoutInMilliseconds(timeout)
                            .withExecutionIsolationThreadInterruptOnTimeout(true)
                            .withExecutionTimeoutEnabled(true));

        }


    }


    /**
     * 生成降级返回的数据
     * @param clazzPackage  返回的类
     * @param defaultValue  降级后返回的值
     * @return
     * @throws Exception
     */
    public static Object  generateClass(String clazzPackage , String defaultValue) throws  Exception{


        log.info("\n--------------------------正在服务降级执行返回数据组装-----------------------");
        log.info("\n--------------------------返回类型：{}-----------返回值：{}------------",clazzPackage,defaultValue);
        if(StringUtils.isBlank(clazzPackage) || StringUtils.isBlank(defaultValue)){
            return null;
        }

        log.info("返回的类"+ clazzPackage);
        // 返回的类型是String类型的
        if(clazzPackage.contains("String")){
            String returnStr = new String(defaultValue.getBytes(),"UTF-8");
            log.info("降级返回的字符串 {}",returnStr);
            return returnStr;
        }
        JSONObject hystrixReturnValueJson = JSONObject.parseObject(defaultValue);
        Iterator iterator = hystrixReturnValueJson.keySet().iterator();
        HashMap<String,String> fieldMap = new HashMap<>();
        while(iterator.hasNext()){
            String key = (String)iterator.next();
            String value = new String(hystrixReturnValueJson.getString(key).getBytes(),"UTF-8");
            log.info("字段属性"+ key +"  "+value);
            fieldMap.put(key,value);
        }

        // 使用反射，生成降级后返回的对象
        Class clazz = Class.forName(clazzPackage);
        Object object = clazz.newInstance();
        BeanUtil.fillBeanWithMap(fieldMap,object,true);
        log.info("生成的降级返回类 {} {}",object.toString(), JSON.toJSONString(object));
        return object;
    }





}
