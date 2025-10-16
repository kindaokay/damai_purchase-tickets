-- damai_order_0 库
USE damai_order_0;
DROP TABLE IF EXISTS `d_sharding_route_mapping`;
CREATE TABLE `d_sharding_route_mapping` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `logical_shard_id` int NOT NULL COMMENT '逻辑分片ID（0-1023）',
    `physical_database_suffix` varchar(64) NOT NULL COMMENT '物理数据库名后缀（适用于所有库类型）',
    `physical_table_suffix` int NOT NULL COMMENT '物理表后缀（0-7，适用于所有表类型）',
    `version` int NOT NULL DEFAULT '1' COMMENT '版本号（用于热更新）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `edit_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `status` int NOT NULL DEFAULT '1',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_logical_shard_id` (`logical_shard_id`)
) ENGINE=InnoDB COMMENT='虚拟分片路由映射表';
truncate table d_sharding_route_mapping;
-- 物理分片0：{库前缀}_0.{表前缀}_0 → 虚拟分片0-127
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '0' AS physical_database_suffix,
    0 AS physical_table_suffix
FROM
    (SELECT 0 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 0 AND n <= 127;

-- 物理分片1：{库前缀}_0.{表前缀}_1 → 虚拟分片128-255
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '0' AS physical_database_suffix,
    1 AS physical_table_suffix
FROM
    (SELECT 128 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 128 AND n <= 255;

-- 物理分片2：{库前缀}_0.{表前缀}_2 → 虚拟分片256-383
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '0' AS physical_database_suffix,
    2 AS physical_table_suffix
FROM
    (SELECT 256 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 256 AND n <= 383;

-- 物理分片3：{库前缀}_0.{表前缀}_3 → 虚拟分片384-511
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '0' AS physical_database_suffix,
    3 AS physical_table_suffix
FROM
    (SELECT 384 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 384 AND n <= 511;

-- 物理分片4：{库前缀}_1.{表前缀}_0 → 虚拟分片512-639
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '1' AS physical_database_suffix,
    0 AS physical_table_suffix
FROM
    (SELECT 512 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 512 AND n <= 639;

-- 物理分片5：{库前缀}_1.{表前缀}_1 → 虚拟分片640-767
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '1' AS physical_database_suffix,
    1 AS physical_table_suffix
FROM
    (SELECT 640 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 640 AND n <= 767;

-- 物理分片6：{库前缀}_1.{表前缀}_2 → 虚拟分片768-895
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '1' AS physical_database_suffix,
    2 AS physical_table_suffix
FROM
    (SELECT 768 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 768 AND n <= 895;

-- 物理分片7：{库前缀}_1.{表前缀}_3 → 虚拟分片896-1023
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '1' AS physical_database_suffix,
    3 AS physical_table_suffix
FROM
    (SELECT 896 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 896 AND n <= 1023;

-- damai_order_0 库
USE damai_order_1;
DROP TABLE IF EXISTS `d_sharding_route_mapping`;
CREATE TABLE `d_sharding_route_mapping` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `logical_shard_id` int NOT NULL COMMENT '逻辑分片ID（0-1023）',
    `physical_database_suffix` varchar(64) NOT NULL COMMENT '物理数据库名后缀（适用于所有库类型）',
    `physical_table_suffix` int NOT NULL COMMENT '物理表后缀（0-7，适用于所有表类型）',
    `version` int NOT NULL DEFAULT '1' COMMENT '版本号（用于热更新）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `edit_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `status` int NOT NULL DEFAULT '1',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_logical_shard_id` (`logical_shard_id`)
) ENGINE=InnoDB COMMENT='虚拟分片路由映射表';
truncate table d_sharding_route_mapping;
-- 物理分片0：{库前缀}_0.{表前缀}_0 → 虚拟分片0-127
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '0' AS physical_database_suffix,
    0 AS physical_table_suffix
FROM
    (SELECT 0 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 0 AND n <= 127;

-- 物理分片1：{库前缀}_0.{表前缀}_1 → 虚拟分片128-255
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '0' AS physical_database_suffix,
    1 AS physical_table_suffix
FROM
    (SELECT 128 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 128 AND n <= 255;

-- 物理分片2：{库前缀}_0.{表前缀}_2 → 虚拟分片256-383
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '0' AS physical_database_suffix,
    2 AS physical_table_suffix
FROM
    (SELECT 256 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 256 AND n <= 383;

-- 物理分片3：{库前缀}_0.{表前缀}_3 → 虚拟分片384-511
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '0' AS physical_database_suffix,
    3 AS physical_table_suffix
FROM
    (SELECT 384 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 384 AND n <= 511;

-- 物理分片4：{库前缀}_1.{表前缀}_0 → 虚拟分片512-639
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '1' AS physical_database_suffix,
    0 AS physical_table_suffix
FROM
    (SELECT 512 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 512 AND n <= 639;

-- 物理分片5：{库前缀}_1.{表前缀}_1 → 虚拟分片640-767
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '1' AS physical_database_suffix,
    1 AS physical_table_suffix
FROM
    (SELECT 640 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 640 AND n <= 767;

-- 物理分片6：{库前缀}_1.{表前缀}_2 → 虚拟分片768-895
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '1' AS physical_database_suffix,
    2 AS physical_table_suffix
FROM
    (SELECT 768 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 768 AND n <= 895;

-- 物理分片7：{库前缀}_1.{表前缀}_3 → 虚拟分片896-1023
INSERT INTO d_sharding_route_mapping (logical_shard_id, physical_database_suffix, physical_table_suffix)
SELECT
    n AS logical_shard_id,
    '1' AS physical_database_suffix,
    3 AS physical_table_suffix
FROM
    (SELECT 896 + a + b*10 + c*100 AS n
     FROM
         (SELECT 0 AS a UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t1,
         (SELECT 0 AS b UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t2,
         (SELECT 0 AS c UNION SELECT 1) t3
    ) numbers
WHERE n >= 896 AND n <= 1023;