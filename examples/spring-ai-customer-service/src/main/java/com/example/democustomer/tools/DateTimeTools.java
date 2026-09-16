package com.example.democustomer.tools;

import io.iaoep.sdk.annotation.ObservedTool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DateTime Tools — 示例工具,集成 IAOEP SDK 注解。
 *
 * <p>{@code @ObservedTool} 会在每次调用时产生 tool.execute Span。</p>
 */
@Component
public class DateTimeTools {

    @ObservedTool(name = "get_current_time")
    public String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
