package com.damai.service;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.damai.core.RedisKeyManage;
import com.damai.dto.ProgramManageDto;
import com.damai.entity.TicketCategory;
import com.damai.mapper.TicketCategoryMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.vo.TicketCategoryDetailManageVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目后台管理 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class ProgramManageService  {
    
    @Autowired
    private TicketCategoryMapper ticketCategoryMapper;
    
    @Autowired
    private RedisCache redisCache;
    
    
    public List<TicketCategoryDetailManageVo> ticketCategoryList(ProgramManageDto programManageDto) {
        List<TicketCategory> ticketCategorieList = ticketCategoryMapper.selectList(Wrappers.lambdaQuery(TicketCategory.class)
                .eq(TicketCategory::getProgramId, programManageDto.getProgramId())
                .orderByAsc(TicketCategory::getPrice));
        return ticketCategorieList.stream().map(ticketCategory -> {
            TicketCategoryDetailManageVo ticketCategoryDetailManageVo = new TicketCategoryDetailManageVo();
            BeanUtil.copyProperties(ticketCategory,ticketCategoryDetailManageVo);
            ticketCategoryDetailManageVo.setDbRemainNumber(ticketCategory.getRemainNumber());
            //Key:票档id，value:节目id
            Map<String, Long> ticketCategoryRemainNumber =
                    redisCache.getAllMapForHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION,
                            ticketCategory.getProgramId(),ticketCategory.getId()), Long.class);
            if (CollectionUtil.isNotEmpty(ticketCategoryRemainNumber)) {
                ticketCategoryDetailManageVo.setRedisRemainNumber(ticketCategoryRemainNumber.get(ticketCategory.getId().toString()));
            }
            return ticketCategoryDetailManageVo;
        }).collect(Collectors.toList());
    }
}
