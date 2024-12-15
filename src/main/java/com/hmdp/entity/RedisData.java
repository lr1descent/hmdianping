package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author lr1descent
 * @version 1.0 2024-12-14
 */
@Data
public class RedisData {
    Object data;
    LocalDateTime expireTime;
}
