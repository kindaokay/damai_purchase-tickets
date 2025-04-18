package com.damai.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 需要进行添加的数据
 * @author: 阿星不是程序员
 **/
@Data
@Schema(title="ReconciliationTaskData", description ="需要进行添加的数据")
public class ReconciliationTaskData {
    
    @Schema(name ="programId", type ="Long", description ="节目id")
    private Long programId;
    
    @Schema(name ="addRedisRecordData", type ="Map", description ="需要向redis添加的数据")
    private Map<String, ProgramRecord> addRedisRecordData;
    
}
