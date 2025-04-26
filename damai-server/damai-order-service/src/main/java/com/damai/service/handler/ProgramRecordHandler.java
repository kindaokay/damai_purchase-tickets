package com.damai.service.handler;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.damai.core.RedisKeyManage;
import com.damai.domain.ProgramRecord;
import com.damai.entity.Order;
import com.damai.entity.OrderProgram;
import com.damai.entity.OrderTicketUser;
import com.damai.entity.OrderTicketUserRecord;
import com.damai.enums.BaseCode;
import com.damai.enums.HandleStatus;
import com.damai.enums.ReconciliationStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.mapper.OrderMapper;
import com.damai.mapper.OrderProgramMapper;
import com.damai.mapper.OrderTicketUserMapper;
import com.damai.mapper.OrderTicketUserRecordMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.util.SplitUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.damai.constant.Constant.GLIDE_LINE;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 记录流水处理
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ProgramRecordHandler {

    @Autowired
    private RedisCache redisCache;
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private OrderTicketUserMapper orderTicketUserMapper;
    
    @Autowired
    private OrderTicketUserRecordMapper orderTicketUserRecordMapper;
    
    @Autowired
    private OrderProgramMapper orderProgramMapper;
    
    /**
     * 向redis中添加补偿的记录，从未完成记录中转移到完整的记录
     * */
    @Transactional(rollbackFor = Exception.class)
    public void add(int retryCount,Long programId,Map<String, ProgramRecord> completeRedisCordMap,Map<String, String> totalProgramRecordMap){
        int maxRetryCount = 5;
        if (retryCount > maxRetryCount) {
            log.error("添加记录流水失败超过最大重试次数,retryCount:{} programId:{}, completeRedisCordMap:{}, totalProgramRecordMap:{}", retryCount,programId, completeRedisCordMap, totalProgramRecordMap);
            throw new DaMaiFrameException(BaseCode.MAX_RETRY_COUNT);
        }
        try {
            Set<String> keyList = new HashSet<>();
            //把数据库中的订单、购票人订单、购票人订单记录都修改成对账完成状态
            addKeyList(keyList,completeRedisCordMap);
            addKeyList(keyList,totalProgramRecordMap);
            for (final String key : keyList) {
                String[] split = SplitUtil.toSplit(key);
                Long identifierId = Long.valueOf(split[0]);
                Long userId = Long.valueOf(split[1]);
                int result = updateDbOrderTicketUserRecordStatus(programId, identifierId, userId, ReconciliationStatus.RECONCILIATION_SUCCESS);
                log.info("修改数据库记录流水成功, programId:{}, identifierId:{}, userId:{}, result:{}", programId, identifierId, userId, result);
            }
            if (CollectionUtil.isNotEmpty(totalProgramRecordMap)) {
                //从旧地记录中删除
                redisCache.delForHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_RECORD, programId),totalProgramRecordMap.keySet());
            }
            if (CollectionUtil.isNotEmpty(totalProgramRecordMap)) {
                //目前所有的记录添加到完成的记录中 key：记录类型_记录标识_用户id value：记录标识
                redisCache.putHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_RECORD_FINISH, programId), totalProgramRecordMap);
            }
            if (CollectionUtil.isNotEmpty(completeRedisCordMap)) {
                //将新补充的记录添加到redis对比完成的记录中
                redisCache.putHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_RECORD_FINISH, programId), completeRedisCordMap);
                log.info("添加记录流水成功, programId:{}, completeRedisCordMap:{}, totalProgramRecordMap:{}", programId, completeRedisCordMap, totalProgramRecordMap);
            }
        }catch (Exception e) {
            log.warn("添加记录流水失败进行重试, programId:{}, completeRedisCordMap:{}, totalProgramRecordMap:{}", programId, completeRedisCordMap, totalProgramRecordMap, e);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                log.error("Thread sleep interrupted", ex);
            }
            retryCount++;
            add(retryCount, programId, completeRedisCordMap, totalProgramRecordMap);
        }
    }
    
    public void addKeyList(Set<String> keyList,Map<String,?> map){
        if (CollectionUtil.isEmpty(map)) {
            return;
        }
        for (final Entry<String, ?> entry : map.entrySet()) {
            String[] split = SplitUtil.toSplit(entry.getKey());
            keyList.add(split[1] + GLIDE_LINE + split[2]);
        }
    }
    
    @Transactional(rollbackFor = Exception.class)
    public int updateDbOrderTicketUserRecordStatus(Long programId, Long identifierId, Long userId, ReconciliationStatus reconciliationStatus) {
        List<Order> orderList = orderMapper.selectList(Wrappers.lambdaQuery(Order.class).eq(Order::getProgramId, programId).eq(Order::getIdentifierId, identifierId).eq(Order::getUserId, userId).eq(Order::getReconciliationStatus, ReconciliationStatus.RECONCILIATION_NO.getCode()));
        if (CollectionUtil.isEmpty(orderList)) {
            return 0;
        }
        Order updateOrder = new Order();
        updateOrder.setReconciliationStatus(reconciliationStatus.getCode());
        //将订单的对账状态更新为已对账
        orderMapper.update(updateOrder, Wrappers.lambdaUpdate(Order.class)
                .eq(Order::getProgramId, programId)
                .eq(Order::getIdentifierId, identifierId)
                .eq(Order::getUserId, userId)
                .eq(Order::getReconciliationStatus, ReconciliationStatus.RECONCILIATION_NO.getCode()));
        Long orderNumber = orderList.get(0).getOrderNumber();
        //将购票人订单的对账状态更新为已对账
        OrderTicketUser updateOrderTicketUser = new OrderTicketUser();
        updateOrderTicketUser.setReconciliationStatus(reconciliationStatus.getCode());
        orderTicketUserMapper.update(updateOrderTicketUser,Wrappers.lambdaUpdate(OrderTicketUser.class)
                .eq(OrderTicketUser::getOrderNumber, orderNumber)
                .eq(OrderTicketUser::getReconciliationStatus, ReconciliationStatus.RECONCILIATION_NO.getCode()));
        //将订单节目的对账状态更新为已对账
        OrderProgram updateOrderProgram = new OrderProgram();
        updateOrderProgram.setHandleStatus(HandleStatus.YES_HANDLE.getCode());
        orderProgramMapper.update(updateOrderProgram,Wrappers.lambdaUpdate(OrderProgram.class)
                .eq(OrderProgram::getOrderNumber, orderNumber)
                .eq(OrderProgram::getHandleStatus, HandleStatus.NO_HANDLE.getCode())
                .eq(OrderProgram::getProgramId, programId));
        //将购票人订单记录的对账状态更新为已对账
        OrderTicketUserRecord updateOrderTicketUserRecord = new OrderTicketUserRecord();
        updateOrderTicketUserRecord.setReconciliationStatus(reconciliationStatus.getCode());
        return orderTicketUserRecordMapper.update(updateOrderTicketUserRecord,Wrappers.lambdaUpdate(OrderTicketUserRecord.class)
                .eq(OrderTicketUserRecord::getOrderNumber, orderNumber)
                .eq(OrderTicketUserRecord::getReconciliationStatus, ReconciliationStatus.RECONCILIATION_NO.getCode()));
    }
}
