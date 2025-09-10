package com.damai.scheduletask;

import com.damai.BusinessThreadPool;
import com.damai.core.RedisKeyManage;
import com.damai.domain.DiscardOrder;
import com.damai.domain.OrderCreateMq;
import com.damai.dto.OrderTicketUserCreateDto;
import com.damai.enums.DiscardOrderReason;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.OrderService;
import com.damai.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 订单服务定时任务重置
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class OrderDataTask {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private RedisCache redisCache;
    
    @Scheduled(cron = "0 0 23 * * ?")
    public void executeTask(){
        BusinessThreadPool.execute( () -> {
            try {
                log.info("订单服务定时任务重置执行");
                orderService.delOrderAndOrderTicketUser();
                
                //模拟废弃订单数据
                OrderCreateMq orderCreateMq = new OrderCreateMq();
                orderCreateMq.setCreateOrderTime(DateUtils.parse(1757516177000L));
                orderCreateMq.setIdentifierId(1421864797540605952L);
                orderCreateMq.setOrderNumber(1965791442215448582L);
                orderCreateMq.setOrderPrice(new BigDecimal(2000));
                orderCreateMq.setOrderVersion(4);
                orderCreateMq.setProgramId(34L);
                orderCreateMq.setProgramItemPicture("https://s21.ax1x.com/2024/06/07/pkYzl9J.jpg");
                orderCreateMq.setProgramPermitChooseSeat(0);
                orderCreateMq.setProgramPlace("工人体育馆");
                orderCreateMq.setProgramShowTime(DateUtils.parse(1758371400000L));
                orderCreateMq.setProgramTitle("周杰伦“嘉年华”世界巡回演唱会");
                orderCreateMq.setUserId(1421653760027484162L);
                
                List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = new ArrayList<>();
                OrderTicketUserCreateDto orderTicketUserCreateDto = new OrderTicketUserCreateDto();
                orderTicketUserCreateDto.setCreateOrderTime(DateUtils.parse(1757516177000L));
                orderTicketUserCreateDto.setOrderNumber(1965791442215448582L);
                orderTicketUserCreateDto.setOrderPrice(new BigDecimal(2000));
                orderTicketUserCreateDto.setProgramId(34L);
                orderTicketUserCreateDto.setSeatId(10251L);
                orderTicketUserCreateDto.setSeatInfo("551排1列");
                orderTicketUserCreateDto.setTicketCategoryId(46L);
                orderTicketUserCreateDto.setTicketUserId(1421653760027500032L);
                orderTicketUserCreateDto.setUserId(1421653760027484162L);
                orderTicketUserCreateDtoList.add(orderTicketUserCreateDto);
                orderCreateMq.setOrderTicketUserCreateDtoList(orderTicketUserCreateDtoList);
                
                redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.DISCARD_ORDER, 34));
                redisCache.leftPushForList(RedisKeyBuild.createRedisKey(RedisKeyManage.DISCARD_ORDER, 34),new DiscardOrder(orderCreateMq, DiscardOrderReason.CONSUMER_DELAY.getCode()));
            }catch (Exception e) {
                log.error("executeTask error",e);
            }
        });
    }
}
