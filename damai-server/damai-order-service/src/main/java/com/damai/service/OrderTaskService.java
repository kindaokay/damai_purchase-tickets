package com.damai.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.damai.core.RedisKeyManage;
import com.damai.domain.*;
import com.damai.entity.Order;
import com.damai.entity.OrderTicketUserRecord;
import com.damai.enums.ReconciliationStatus;
import com.damai.enums.RecordType;
import com.damai.mapper.OrderMapper;
import com.damai.mapper.OrderTicketUserRecordMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.damai.constant.Constant.GLIDE_LINE;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 订单任务
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class OrderTaskService {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private OrderTicketUserRecordMapper orderTicketUserRecordMapper;

    @Autowired
    private OrderTicketUserRecordService orderTicketUserRecordService;

    public ExaminationTotalResult reconciliationQuery(Long programId){
        //以redis为标准的对账
        List<ExaminationIdentifierResult> examinationIdentifierResultRedisStandardList = reconciliationRedisStandard(programId);
        //以数据库为标准的对账
        List<ExaminationIdentifierResult> examinationIdentifierResultDbStandardList = reconciliationDbStandard(programId);
        return new ExaminationTotalResult(programId,
                examinationIdentifierResultRedisStandardList,
                examinationIdentifierResultDbStandardList);
    }
    public List<ExaminationIdentifierResult> reconciliationRedisStandard(Long programId) {
        //redis和数据对账结果(节目维度)
        List<ExaminationIdentifierResult> examinationIdentifierResultList = new ArrayList<>();
        Map<String, String> programRecordMap = redisCache.getAllMapForHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_RECORD, programId), String.class);
        //以redis记录为基准的话，redis记录不存在就直接返回
        if (CollectionUtil.isEmpty(programRecordMap)) {
            return examinationIdentifierResultList;
        }
        //key：记录标识_用户id
        //value：记录类型的集合
        Map<String, List<String>> identifierIdAndUserIdMap = regroup(programRecordMap);
        for (Map.Entry<String, List<String>> identifierIdAndUserIdEntry : identifierIdAndUserIdMap.entrySet()) {
            String[] split = toSplit(identifierIdAndUserIdEntry.getKey());
            if (split.length != 2) {
                continue;
            }
            //标识和订单关联
            String identifierId = split[0];
            //用户id
            String userId = split[1];
            //redis中记录类型集合
            List<String> redisRecordTypeList = identifierIdAndUserIdEntry.getValue();
            //购票人订单记录中的座位
            Map<Integer, List<OrderTicketUserRecord>> orderTicketUserRecordMap = new HashMap<>(64);
            //查询订单
            Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class)
                    .eq(Order::getReconciliationStatus, ReconciliationStatus.RECONCILIATION_NO.getCode())
                    .eq(Order::getProgramId, programId)
                    .eq(Order::getUserId, Long.parseLong(userId))
                    .eq(Order::getIdentifierId, Long.parseLong(identifierId)));
            if (Objects.nonNull(order)) {
                //根据订单编号查询购票人订单记录
                List<OrderTicketUserRecord> orderTicketUserRecordList =
                        orderTicketUserRecordService.list(Wrappers.lambdaQuery(OrderTicketUserRecord.class)
                                .eq(OrderTicketUserRecord::getOrderNumber, order.getOrderNumber()));
                if (CollectionUtil.isNotEmpty(orderTicketUserRecordList)) {
                    //购票人订单记录中的座位
                    orderTicketUserRecordMap =
                            orderTicketUserRecordList.stream().collect(Collectors.groupingBy(OrderTicketUserRecord::getRecordTypeCode));
                }
            }
            List<ExaminationRecordTypeResult> examinationRecordTypeResultList = new ArrayList<>();
            for (String redisRecordType : redisRecordTypeList) {
                //根据记录类型获取对应的购票人订单记录中的座位
                List<OrderTicketUserRecord> dbOrderTicketUserRecordList =
                        orderTicketUserRecordMap.get(RecordType.getCodeByValue(redisRecordType));
                ExaminationRecordTypeResult examinationRecordTypeResult =  executeRedisAndDbExamination(programRecordMap, dbOrderTicketUserRecordList, redisRecordType, identifierId, userId);
                examinationRecordTypeResultList.add(examinationRecordTypeResult);
            }
            //redis和数据对账结果(记录标识维度)
            ExaminationIdentifierResult examinationIdentifierResult = new ExaminationIdentifierResult(
                    identifierId,
                    userId,
                    examinationRecordTypeResultList);
            examinationIdentifierResultList.add(examinationIdentifierResult);
        }
        return examinationIdentifierResultList;
    }
    
    public String[] toSplit(String str) {
        return str.split(GLIDE_LINE);
    }

    public List<ExaminationIdentifierResult> reconciliationDbStandard(Long programId) {
        //redis和数据对账结果(节目维度)
        List<ExaminationIdentifierResult> examinationIdentifierResultList = new ArrayList<>();
        //查询订单
        List<Order> orderList = orderMapper.selectList(Wrappers.lambdaQuery(Order.class)
                .eq(Order::getReconciliationStatus, ReconciliationStatus.RECONCILIATION_NO.getCode())
                .eq(Order::getProgramId, programId));
        //查询redis中的节目记录
        Map<String, String> programRecordMap = redisCache.getAllMapForHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_RECORD, programId), String.class);
        if (CollectionUtil.isEmpty(orderList)) {
            return examinationIdentifierResultList;
        }
        //购票人订单记录中的座位
        List<OrderTicketUserRecord> orderTicketUserRecordList =
                orderTicketUserRecordMapper.selectList(Wrappers.lambdaQuery(OrderTicketUserRecord.class)
                        .in(OrderTicketUserRecord::getOrderNumber, orderList.stream().map(Order::getOrderNumber).collect(Collectors.toList()))
                        .eq(OrderTicketUserRecord::getReconciliationStatus, ReconciliationStatus.RECONCILIATION_NO.getCode()));
        //key：记录类型_记录标识_用户id。例如：orderTicketUserRecordList
        //value：购票人订单记录
        Map<String, List<OrderTicketUserRecord>> orderTicketUserRecordMap = 
                orderTicketUserRecordList.stream().collect(Collectors.groupingBy(record ->
                record.getRecordTypeValue() + GLIDE_LINE + record.getIdentifierId() + GLIDE_LINE + record.getUserId()));
        //key：记录标识_用户id
        //value：记录类型的集合
        Map<String, List<String>> identifierIdAndUserIdMap = regroup(orderTicketUserRecordMap);
        for (Map.Entry<String, List<String>> identifierIdAndUserIdEntry : identifierIdAndUserIdMap.entrySet()) {
            String[] split = toSplit(identifierIdAndUserIdEntry.getKey());
            if (split.length != 2) {
                continue;
            }
            //标识和订单关联
            String identifierId = split[0];
            //用户id
            String userId = split[1];
            //数据库中记录类型集合
            List<String> dbRecordTypeList = identifierIdAndUserIdEntry.getValue();
            List<ExaminationRecordTypeResult> examinationRecordTypeResultList = new ArrayList<>();
            for (String dbRecordType : dbRecordTypeList) {
                //根据记录类型获取对应的购票人订单记录中的座位
                List<OrderTicketUserRecord> dbOrderTicketUserRecordList = orderTicketUserRecordMap.get(dbRecordType + GLIDE_LINE + identifierId + GLIDE_LINE + userId);
                ExaminationRecordTypeResult examinationRecordTypeResult =  executeRedisAndDbExamination(programRecordMap, dbOrderTicketUserRecordList, dbRecordType, identifierId, userId);
                examinationRecordTypeResultList.add(examinationRecordTypeResult);
            }
            //redis和数据对账结果(记录标识维度)
            ExaminationIdentifierResult examinationIdentifierResult = new ExaminationIdentifierResult(
                    identifierId,
                    userId,
                    examinationRecordTypeResultList);
            examinationIdentifierResultList.add(examinationIdentifierResult);
        }
        return examinationIdentifierResultList;
    }
    
    public Map<String, List<String>> regroup(Map<String, ?> programRecordMap) {
        Map<String, List<String>> resultMap = new HashMap<>(64);
        for (String origKey : programRecordMap.keySet()) {
            // 最多分割为 3 段：["changeStatus", "985033500750127104", "927653802827104258"]
            String[] parts = origKey.split(GLIDE_LINE, 3);
            if (parts.length < 3) {
                // 不符合预期格式时跳过或自行处理
                continue;
            }
            // "changeStatus" 或 "reduce" 或 "increase"
            String action = parts[0];
            // "985033500750127104_927653802827104258"
            String newKey = parts[1] + "_" + parts[2];
            // 累加到结果 Map 中
            resultMap.computeIfAbsent(newKey, k -> new ArrayList<>()).add(action);
        }
        return resultMap;
    }
    
    public ExaminationRecordTypeResult executeRedisAndDbExamination(Map<String, String> programRecordMap,List<OrderTicketUserRecord> dbOrderTicketUserRecordList,String dbRecordType,
                                                                   String identifierId, String userId) {
        ProgramRecord programRecord = JSON.parseObject(programRecordMap.get(dbRecordType + GLIDE_LINE + identifierId + GLIDE_LINE + userId), ProgramRecord.class);
        //如果数据库和redis都没有这条记录的话
        if (CollectionUtil.isEmpty(dbOrderTicketUserRecordList) && Objects.isNull(programRecord)) {
            //redis和数据对账结果(记录类型维度)
            return new ExaminationRecordTypeResult(
                    RecordType.getCodeByValue(dbRecordType),
                    dbRecordType,
                    new ExaminationSeatResult());
        }
        //如果数据库没有，redis有这条记录的话
        if (CollectionUtil.isEmpty(dbOrderTicketUserRecordList) && Objects.nonNull(programRecord)) {
            Map<Long, SeatRecord> redisSeatRecordMap = getRedisSeatRecordMap(programRecord);
            //redis记录中的座位和购票人订单记录中的座位对比
            ExaminationSeatResult examinationResult = executeExaminationSeat(redisSeatRecordMap, null);
            //redis和数据对账结果(记录类型维度)
            return new ExaminationRecordTypeResult(
                    RecordType.getCodeByValue(dbRecordType),
                    dbRecordType,
                    examinationResult);
        }
        //购票人订单记录中的座位转成Map，key：座位id，value：OrderTicketUserRecord
        Map<Long, OrderTicketUserRecord> dbOrderTicketUserRecordMap =
                dbOrderTicketUserRecordList.stream().collect(Collectors.toMap(OrderTicketUserRecord::getSeatId, orderTicketUserRecord -> orderTicketUserRecord, (v1, v2) -> v2));
        //如果数据库有，redis没有这条记录的话
        if (CollectionUtil.isNotEmpty(dbOrderTicketUserRecordList) && Objects.isNull(programRecord)) {
            //redis记录中的座位和购票人订单记录中的座位对比
            ExaminationSeatResult examinationResult = executeExaminationSeat(null, dbOrderTicketUserRecordMap);
            //redis和数据对账结果(记录类型维度)
            return new ExaminationRecordTypeResult(
                    RecordType.getCodeByValue(dbRecordType),
                    dbRecordType,
                    examinationResult);
        }
        //如果数据库和redis都有这条记录的话
        Map<Long, SeatRecord> redisSeatRecordMap = getRedisSeatRecordMap(programRecord);
        //redis记录中的座位和购票人订单记录中的座位对比
        ExaminationSeatResult examinationResult = executeExaminationSeat(redisSeatRecordMap, dbOrderTicketUserRecordMap);
        //redis和数据对账结果(记录类型维度)
        return new ExaminationRecordTypeResult(
                RecordType.getCodeByValue(dbRecordType),
                dbRecordType,
                examinationResult);
    }
    
    public Map<Long, SeatRecord> getRedisSeatRecordMap(ProgramRecord programRecord){
        List<TicketCategoryRecord> ticketCategoryRecordList = programRecord.getTicketCategoryRecordList();
        //redis记录中的座位
        List<SeatRecord> seatRecordList = new ArrayList<>();
        for (TicketCategoryRecord ticketCategoryRecord : ticketCategoryRecordList) {
            seatRecordList.addAll(ticketCategoryRecord.getSeatRecordList());
        }
        //redis记录中的座位转成Map，key：座位id，value：SeatRecord
        return seatRecordList.stream().collect(Collectors.toMap(SeatRecord::getSeatId, 
                seatRecord -> seatRecord, (v1, v2) -> v2));
    }
    
    public ExaminationSeatResult executeExaminationSeat(Map<Long, SeatRecord> redisSeatRecordMap,
                                                        Map<Long, OrderTicketUserRecord> dbOrderTicketUserRecordMap){
        //以redis为准的座位记录统计数量
        int redisStandardStatisticCount = 0;
        //需要向数据库中补充的座位
        List<SeatRecord> needToDbSeatRecordList = new ArrayList<>();
        //以数据库为准的座位记录统计数量
        int dbStandardStatisticCount = 0;
        //需要向redis中补充的座位
        List<OrderTicketUserRecord> needToRedisSeatRecordList = new ArrayList<>();
        //redis没有的话，直接构建数据
        if (CollectionUtil.isEmpty(redisSeatRecordMap)) {
            for (Map.Entry<Long, OrderTicketUserRecord> orderTicketUserRecordEntry : dbOrderTicketUserRecordMap.entrySet()) {
                needToRedisSeatRecordList.add(orderTicketUserRecordEntry.getValue());
            }
            //对比结果
            return new ExaminationSeatResult(
                    redisStandardStatisticCount,
                    dbStandardStatisticCount,
                    needToDbSeatRecordList,
                    needToRedisSeatRecordList
            );
        }
        //数据库没有的话，直接构建数据
        if (CollectionUtil.isEmpty(dbOrderTicketUserRecordMap)) {
            for (Map.Entry<Long, SeatRecord> seatRecordEntry : redisSeatRecordMap.entrySet()) {
                needToDbSeatRecordList.add(seatRecordEntry.getValue());
            }
            //对比结果
            return new ExaminationSeatResult(
                    redisStandardStatisticCount,
                    dbStandardStatisticCount,
                    needToDbSeatRecordList,
                    needToRedisSeatRecordList
            );
        }
        //以redis记录为准，对比redis记录中的座位和购票人订单记录中的座位
        for (Map.Entry<Long, SeatRecord> seatRecordEntry : redisSeatRecordMap.entrySet()) {
            Long seatId = seatRecordEntry.getKey();
            OrderTicketUserRecord orderTicketUserRecord = dbOrderTicketUserRecordMap.get(seatId);
            //redis记录有座位，数据库记录没有座位
            if (Objects.isNull(orderTicketUserRecord)) {
                needToDbSeatRecordList.add(seatRecordEntry.getValue());
            }else {
                //匹配到了
                redisStandardStatisticCount++;
            }
        }
        //以数据库记录为准，对比redis记录中的座位和购票人订单记录中的座位
        for (Map.Entry<Long, OrderTicketUserRecord> orderTicketUserRecordEntry : dbOrderTicketUserRecordMap.entrySet()) {
            Long seatId = orderTicketUserRecordEntry.getKey();
            SeatRecord seatRecord = redisSeatRecordMap.get(seatId);
            //数据库记录有座位，redis记录没有座位
            if (Objects.isNull(seatRecord)) {
                needToRedisSeatRecordList.add(orderTicketUserRecordEntry.getValue());
            }else {
                //匹配到了
                dbStandardStatisticCount++;
            }
        }
        //对比结果
        return new ExaminationSeatResult(
                redisStandardStatisticCount,
                dbStandardStatisticCount,
                needToDbSeatRecordList,
                needToRedisSeatRecordList
        );
    }
    
    public ExaminationSimpleResult reconciliationQuerySimple(Long programId){
        //对账结果
        ExaminationTotalResult examinationTotalResult = reconciliationQuery(programId);
        List<ExaminationIdentifierResult> examinationIdentifierResultRedisStandardList = examinationTotalResult.getExaminationIdentifierResultRedisStandardList();
        List<ExaminationIdentifierResult> examinationIdentifierResultDbStandardList = examinationTotalResult.getExaminationIdentifierResultDbStandardList();
        //循环以redis为标准的结果
        List<ExaminationIdentifierResult> simpleExaminationIdentifierResultRedisStandardList = simpleExaminationIdentifierResultList(examinationIdentifierResultRedisStandardList);
        //循环以数据库为标准的结果
        List<ExaminationIdentifierResult> simpleExaminationIdentifierResultDbStandardList = simpleExaminationIdentifierResultList(examinationIdentifierResultDbStandardList);
        //优化精简后的
        List<ExaminationIdentifierResult> examinationIdentifierResultList = new ArrayList<>();
        if (CollectionUtil.isNotEmpty(simpleExaminationIdentifierResultRedisStandardList)) {
            examinationIdentifierResultList.addAll(simpleExaminationIdentifierResultRedisStandardList);
        }
        if (CollectionUtil.isNotEmpty(simpleExaminationIdentifierResultDbStandardList)) {
            examinationIdentifierResultList.addAll(simpleExaminationIdentifierResultDbStandardList);
        }
        //精简后的对比结果
        return new ExaminationSimpleResult(programId, examinationIdentifierResultList);
    }   
    
    public List<ExaminationIdentifierResult> simpleExaminationIdentifierResultList(List<ExaminationIdentifierResult> examinationIdentifierResultList){
        List<ExaminationIdentifierResult> simpleExaminationIdentifierResultList = new ArrayList<>();
        for (ExaminationIdentifierResult examinationIdentifierResult : examinationIdentifierResultList) {
            String identifierId = examinationIdentifierResult.getIdentifierId();
            String userId = examinationIdentifierResult.getUserId();
            List<ExaminationRecordTypeResult> examinationRecordTypeResultList = examinationIdentifierResult.getExaminationRecordTypeResultList();
            if (CollectionUtil.isEmpty(examinationRecordTypeResultList)) {
                continue;
            }
            //redis和数据对账结果(记录类型维度)，精简过的集合
            List<ExaminationRecordTypeResult> examinationRecordTypeResultListV2 = new ArrayList<>();
            for (ExaminationRecordTypeResult examinationRecordTypeResult : examinationRecordTypeResultList) {
                ExaminationSeatResult examinationSeatResult = examinationRecordTypeResult.getExaminationSeatResult();
                //需要向数据库中补充的座位
                List<SeatRecord> needToDbSeatRecordList = examinationSeatResult.getNeedToDbSeatRecordList();
                //需要向redis中补充的座位
                List<OrderTicketUserRecord> needToRedisSeatRecordList = examinationSeatResult.getNeedToRedisSeatRecordList();
                if (CollectionUtil.isNotEmpty(needToDbSeatRecordList) || CollectionUtil.isNotEmpty(needToRedisSeatRecordList)) {
                    examinationRecordTypeResultListV2.add(examinationRecordTypeResult);
                }
            }
            if (CollectionUtil.isNotEmpty(examinationRecordTypeResultListV2)) {
                simpleExaminationIdentifierResultList.add(new ExaminationIdentifierResult(
                        identifierId,
                        userId,
                        examinationRecordTypeResultListV2));
            }
        }
        return simpleExaminationIdentifierResultList;
    }
    
    public boolean reconciliationTask(Long programId){
        ExaminationSimpleResult examinationSimpleResult = reconciliationQuerySimple(programId);
        List<ExaminationIdentifierResult> examinationIdentifierResultList = examinationSimpleResult.getExaminationIdentifierResultList();
        for (ExaminationIdentifierResult examinationIdentifierResult : examinationIdentifierResultList) {
            String identifierId = examinationIdentifierResult.getIdentifierId();
            String userId = examinationIdentifierResult.getUserId();
            List<ExaminationRecordTypeResult> examinationRecordTypeResultList = examinationIdentifierResult.getExaminationRecordTypeResultList();
            if (CollectionUtil.isEmpty(examinationRecordTypeResultList)) {
                continue;
            }
            for (ExaminationRecordTypeResult examinationRecordTypeResult : examinationRecordTypeResultList) {
                Integer recordTypeCode = examinationRecordTypeResult.getRecordTypeCode();
                ExaminationSeatResult examinationSeatResult = examinationRecordTypeResult.getExaminationSeatResult();
                //需要向redis中补充的数据
                List<OrderTicketUserRecord> needToRedisSeatRecordList = examinationSeatResult.getNeedToRedisSeatRecordList();
                //需要向数据库中补充的数据
                List<SeatRecord> needToDbSeatRecordList = examinationSeatResult.getNeedToDbSeatRecordList();
                //TODO
            }
        }
        return false;
    }
}
