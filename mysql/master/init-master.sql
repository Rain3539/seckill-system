-- ── 主库初始化脚本 ──────────────────────────────────────────────────
-- 1. 建库建表（与原 init.sql 相同内容）
-- 2. 创建业务账号
-- 3. 创建复制账号

CREATE DATABASE IF NOT EXISTS seckill_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE seckill_db;

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

CREATE TABLE IF NOT EXISTS `product` (
    `id`          BIGINT         NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(200)   NOT NULL,
    `description` TEXT,
    `price`       DECIMAL(10,2)  NOT NULL,
    `stock`       INT            NOT NULL DEFAULT 0,
    `image_url`   VARCHAR(500)   DEFAULT NULL,
    `status`      TINYINT        NOT NULL DEFAULT 1,
    `created_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='普通商品表';

CREATE TABLE IF NOT EXISTS `seckill_product` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT,
    `name`          VARCHAR(200)   NOT NULL,
    `description`   TEXT,
    `origin_price`  DECIMAL(10,2)  NOT NULL,
    `seckill_price` DECIMAL(10,2)  NOT NULL,
    `total_stock`   INT            NOT NULL DEFAULT 0,
    `avail_stock`   INT            NOT NULL DEFAULT 0,
    `locked_stock`  INT            NOT NULL DEFAULT 0,
    `version`       INT            NOT NULL DEFAULT 0,
    `image_url`     VARCHAR(500)   DEFAULT NULL,
    `start_time`    DATETIME       NOT NULL,
    `end_time`      DATETIME       NOT NULL,
    `status`        TINYINT        NOT NULL DEFAULT 1,
    `created_at`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';

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
    `status`       TINYINT        NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消',
    `created_at`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 普通商品初始数据
INSERT INTO `product` (`name`, `description`, `price`, `stock`, `image_url`, `status`) VALUES
('iPhone 15 Pro',   'A17 Pro芯片，钛金属机身，4800万像素主摄，支持USB-C传输。', 7999.00, 1000, '/static/images/product_1.png', 1),
('小米14 Ultra',    '徕卡光学大师镜头，骁龙8 Gen3处理器，5000mAh超大电池。',    5999.00, 500,  '/static/images/product_2.png', 1),
('AirPods Pro 2',  'H2芯片主动降噪，自适应通透模式，续航长达6小时。',            1899.00, 2000, '/static/images/product_3.png', 1),
('MacBook Pro 14',  'M3 Pro芯片，Liquid Retina XDR屏幕，18小时超长续航。',      14999.00, 300, '/static/images/product_4.png', 1),
('iPad Air 5',      'M1芯片，10.9英寸全面屏，支持Apple Pencil 2代。',            4799.00, 800, '/static/images/product_5.png', 1);

-- 秒杀商品初始数据
INSERT INTO `seckill_product`
    (`name`, `description`, `origin_price`, `seckill_price`,
     `total_stock`, `avail_stock`, `image_url`, `start_time`, `end_time`, `status`) VALUES
('Switch OLED 秒杀版',
 '任天堂Switch OLED版，7英寸OLED屏，原价2799元，秒杀价1999！限量100台。',
 2799.00, 1999.00, 100, 100, '/static/images/seckill_1.png',
 DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), 1),
('索尼 WH-1000XM5',
 '头戴式旗舰降噪耳机，30小时续航。原价2999，秒杀价1599！',
 2999.00, 1599.00, 50, 50, '/static/images/seckill_2.png',
 DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_ADD(NOW(), INTERVAL 3 HOUR), 1),
('DJI Mini 4 Pro',
 '4K/60fps超高清拍摄，全向避障。原价4799，秒杀价3299！限量30台。',
 4799.00, 3299.00, 30, 30, '/static/images/seckill_3.png',
 DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 5 HOUR), 1);

-- ── 业务账号（主从都需要）──────────────────────────────────────────
CREATE USER IF NOT EXISTS 'seckill'@'%'    IDENTIFIED WITH mysql_native_password BY 'seckill123';
GRANT ALL PRIVILEGES ON seckill_db.* TO 'seckill'@'%';

-- 只读账号（从库业务查询专用）
CREATE USER IF NOT EXISTS 'seckill_ro'@'%' IDENTIFIED WITH mysql_native_password BY 'seckill_ro123';
GRANT SELECT ON seckill_db.* TO 'seckill_ro'@'%';

-- ── 主从复制专用账号 ──────────────────────────────────────────────
CREATE USER IF NOT EXISTS 'replicator'@'%' IDENTIFIED WITH mysql_native_password BY 'Replicator@123';
GRANT REPLICATION SLAVE ON *.* TO 'replicator'@'%';

FLUSH PRIVILEGES;
