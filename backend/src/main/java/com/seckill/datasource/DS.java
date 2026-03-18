package com.seckill.datasource;

import java.lang.annotation.*;

/**
 * 数据源切换注解
 * <p>标注在 Service 方法或类上，AOP 拦截后切换到对应数据源。</p>
 * <ul>
 *   <li>写操作（INSERT / UPDATE / DELETE）→ 不标注或 @DS(MASTER)</li>
 *   <li>读操作（SELECT）→ @DS(SLAVE)</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DS {
    DataSourceType value() default DataSourceType.MASTER;
}
