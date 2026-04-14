-- ── 订单服务从库初始化脚本 ──────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS order_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE order_db;

CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `username`   VARCHAR(50)  NOT NULL,
    `password`   VARCHAR(255) NOT NULL,
    `email`      VARCHAR(100) DEFAULT NULL,
    `phone`      VARCHAR(20)  DEFAULT NULL,
    `status`     TINYINT      NOT NULL DEFAULT 1,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `order` (
    `id`           BIGINT         NOT NULL AUTO_INCREMENT,
    `order_no`     VARCHAR(64)    NOT NULL,
    `user_id`      BIGINT         NOT NULL,
    `product_id`   BIGINT         NOT NULL,
    `product_type` TINYINT        NOT NULL DEFAULT 0 COMMENT '0普通 1秒杀',
    `product_name` VARCHAR(200)   NOT NULL,
    `quantity`     INT            NOT NULL DEFAULT 1,
    `unit_price`   DECIMAL(10,2)  NOT NULL,
    `amount`       DECIMAL(10,2)  NOT NULL,
    `status`       TINYINT        NOT NULL DEFAULT 0 COMMENT '-1TCC预留中 0待支付 1已支付 2已取消',
    `timeout_at`   DATETIME       DEFAULT NULL COMMENT 'TCC超时时间',
    `created_at`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status_timeout` (`status`, `timeout_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE USER IF NOT EXISTS 'order_ro'@'%' IDENTIFIED WITH mysql_native_password BY 'order_ro123';
GRANT SELECT ON order_db.* TO 'order_ro'@'%';

FLUSH PRIVILEGES;
