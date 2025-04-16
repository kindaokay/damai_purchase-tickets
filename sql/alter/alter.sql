ALTER TABLE damai_order_0.d_order_0 ADD identifier_id bigint NULL COMMENT '记录id';
ALTER TABLE damai_order_0.d_order_0 CHANGE identifier_id identifier_id bigint NULL COMMENT '记录id' AFTER order_number;


ALTER TABLE damai_order_0.d_order_1 ADD identifier_id bigint NULL COMMENT '记录id';
ALTER TABLE damai_order_0.d_order_1 CHANGE identifier_id identifier_id bigint NULL COMMENT '记录id' AFTER order_number;

ALTER TABLE damai_order_0.d_order_2 ADD identifier_id bigint NULL COMMENT '记录id';
ALTER TABLE damai_order_0.d_order_2 CHANGE identifier_id identifier_id bigint NULL COMMENT '记录id' AFTER order_number;

ALTER TABLE damai_order_0.d_order_3 ADD identifier_id bigint NULL COMMENT '记录id';
ALTER TABLE damai_order_0.d_order_3 CHANGE identifier_id identifier_id bigint NULL COMMENT '记录id' AFTER order_number;



ALTER TABLE damai_order_1.d_order_0 ADD identifier_id bigint NULL COMMENT '记录id';
ALTER TABLE damai_order_1.d_order_0 CHANGE identifier_id identifier_id bigint NULL COMMENT '记录id' AFTER order_number;


ALTER TABLE damai_order_1.d_order_1 ADD identifier_id bigint NULL COMMENT '记录id';
ALTER TABLE damai_order_1.d_order_1 CHANGE identifier_id identifier_id bigint NULL COMMENT '记录id' AFTER order_number;

ALTER TABLE damai_order_1.d_order_2 ADD identifier_id bigint NULL COMMENT '记录id';
ALTER TABLE damai_order_1.d_order_2 CHANGE identifier_id identifier_id bigint NULL COMMENT '记录id' AFTER order_number;

ALTER TABLE damai_order_1.d_order_3 ADD identifier_id bigint NULL COMMENT '记录id';
ALTER TABLE damai_order_1.d_order_3 CHANGE identifier_id identifier_id bigint NULL COMMENT '记录id' AFTER order_number;


ALTER TABLE damai_order_0.d_order_0 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_0.d_order_0 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_0.d_order_1 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_0.d_order_1 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_0.d_order_2 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_0.d_order_2 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_0.d_order_3 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_0.d_order_3 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_1.d_order_0 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_1.d_order_0 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_1.d_order_1 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_1.d_order_1 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_1.d_order_2 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_1.d_order_2 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_1.d_order_3 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_1.d_order_3 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_0.d_order_ticket_user_0 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_0.d_order_ticket_user_0 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_0.d_order_ticket_user_1 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_0.d_order_ticket_user_1 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_0.d_order_ticket_user_2 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_0.d_order_ticket_user_2 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_0.d_order_ticket_user_3 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_0.d_order_ticket_user_3 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_1.d_order_ticket_user_0 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_1.d_order_ticket_user_0 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_1.d_order_ticket_user_1 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_1.d_order_ticket_user_1 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_1.d_order_ticket_user_2 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_1.d_order_ticket_user_2 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;

ALTER TABLE damai_order_1.d_order_ticket_user_3 ADD reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕';
ALTER TABLE damai_order_1.d_order_ticket_user_3 CHANGE reconciliation_status reconciliation_status int(3) DEFAULT 1 NULL COMMENT '对账状态 1:未对账 -1:对账完成有问题 1:对账完成没有问题 2:对账有问题处理完毕' AFTER order_status;