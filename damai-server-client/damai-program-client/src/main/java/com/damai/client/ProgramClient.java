package com.damai.client;

import com.damai.common.ApiResponse;
import com.damai.dto.ReduceRemainNumberDto;
import com.damai.dto.TicketCategoryListDto;
import com.damai.vo.TicketCategoryDetailVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static com.damai.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目服务 feign
 * @author: 阿星不是程序员
 **/
@Component
@FeignClient(value = SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+"program-service",fallback = ProgramClientFallback.class)
public interface ProgramClient {
    
    /**
     * 更新座位为锁定和扣减余票数量
     * @param reduceRemainNumberDto 参数
     * @return 结果
     * */
    @PostMapping("/program/interior/reduce/remain/number")
    ApiResponse<Boolean> operateSeatLockAndTicketCategoryRemainNumber(ReduceRemainNumberDto reduceRemainNumberDto);

    /**
     * 查询票档集合
     * @param ticketCategoryDto 参数
     * @return 结果
     * */
    @PostMapping(value = "/ticket/category/select/list")
    ApiResponse<List<TicketCategoryDetailVo>> selectList(TicketCategoryListDto ticketCategoryDto);
    
    /**
     * 获取所有节目id集合
     * @return 结果
     * */
    @PostMapping(value = "/program/all/list")
    ApiResponse<List<Long>> allList();
}
