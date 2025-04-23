package com.damai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: api调用数据 dto
 * @author: 阿星不是程序员
 **/
@Data
@Schema(title="ApiDataDto", description ="api被限制调用记录")
public class AddApiDataDto {
    
    @Schema(name ="headVersion", type ="String", description ="headVersion")
    private String headVersion;
    
    @Schema(name ="apiAddress", type ="String", description ="apiAddress")
    private String apiAddress;
    
    @Schema(name ="apiMethod", type ="String", description ="apiMethod")
    private String apiMethod;
    
    @Schema(name ="apiBody", type ="String", description ="apiBody")
    private String apiBody;
    
    @Schema(name ="apiParams", type ="String", description ="apiParams")
    private String apiParams;
    
    @Schema(name ="apiUrl", type ="String", description ="apiUrl")
    private String apiUrl;
    
    @Schema(name ="callDayTime", type ="String", description ="callDayTime")
    private String callDayTime;
    
    @Schema(name ="callHourTime", type ="String", description ="callHourTime")
    private String callHourTime;
    
    @Schema(name ="callMinuteTime", type ="String", description ="callMinuteTime")
    private String callMinuteTime;
    
    @Schema(name ="callSecondTime", type ="String", description ="callSecondTime")
    private String callSecondTime;
    
    @Schema(name ="type", type ="Integer", description ="type")
    private Integer type;
}
