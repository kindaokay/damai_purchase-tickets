USE damai_program_1;

CREATE TABLE `d_program_record_task_0` (
   `id` bigint NOT NULL COMMENT '主键id',
   `program_id` bigint NOT NULL COMMENT '节目表id',
   `handle_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '处理状态 1:未处理 1:已处理',
   `create_time` datetime NOT NULL COMMENT '创建时间',
   `edit_time` datetime NOT NULL COMMENT '编辑时间',
   `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '1:正常 0:删除',
   PRIMARY KEY (`id`),
   KEY `program_record_task_create_time_idx` (`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节目对账记录任务表';

CREATE TABLE `d_program_record_task_1` (
   `id` bigint NOT NULL COMMENT '主键id',
   `program_id` bigint NOT NULL COMMENT '节目表id',
   `handle_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '处理状态 1:未处理 1:已处理',
   `create_time` datetime NOT NULL COMMENT '创建时间',
   `edit_time` datetime NOT NULL COMMENT '编辑时间',
   `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '1:正常 0:删除',
   PRIMARY KEY (`id`),
   KEY `program_record_task_create_time_idx` (`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节目对账记录任务表';