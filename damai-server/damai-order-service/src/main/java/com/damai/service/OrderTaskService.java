package com.damai.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.damai.client.ProgramClient;
import com.damai.common.ApiResponse;
import com.damai.core.RedisKeyManage;
import com.damai.domain.ExaminationIdentifierResult;
import com.damai.domain.ExaminationRecordTypeResult;
import com.damai.domain.ExaminationSeatResult;
import com.damai.domain.ExaminationSimpleResult;
import com.damai.domain.ExaminationTotalResult;
import com.damai.domain.ProgramRecord;
import com.damai.domain.ReconciliationTaskData;
import com.damai.domain.SeatRecord;
import com.damai.domain.TicketCategoryRecord;
import com.damai.dto.TicketCategoryListDto;
import com.damai.entity.Order;
import com.damai.entity.OrderProgram;
import com.damai.entity.OrderTicketUserRecord;
import com.damai.enums.BaseCode;
import com.damai.enums.HandleStatus;
import com.damai.enums.ReconciliationStatus;
import com.damai.enums.RecordType;
import com.damai.enums.SellStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.mapper.OrderMapper;
import com.damai.mapper.OrderProgramMapper;
import com.damai.mapper.OrderTicketUserMapper;
import com.damai.mapper.OrderTicketUserRecordMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.handler.ProgramRecordHandler;
import com.damai.service.handler.SeatHandler;
import com.damai.service.handler.TicketRemainNumberHandler;
import com.damai.util.SplitUtil;
import com.damai.vo.TicketCategoryDetailVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private OrderTicketUserMapper orderTicketUserMapper;
    
    @Autowired
    private OrderTicketUserRecordMapper orderTicketUserRecordMapper;
    
    @Autowired
    private OrderTicketUserRecordService orderTicketUserRecordService;
    
    @Autowired
    private ProgramClient programClient;
    
    @Autowired
    private ProgramRecordHandler programRecordHandler;
    
    @Autowired
    private SeatHandler seatHandler;
    
    @Autowired
    private TicketRemainNumberHandler ticketRemainNumberHandler;
    
    @Autowired
    private OrderProgramMapper orderProgramMapper;
    
    /**
     * 对账查询，得到的redis为主和数据库为主的对比结果还并没有优化合并
     * 
     * @param programId 节目id
     * @param programRecordMap redis中的节目记录
     */
    public ExaminationTotalResult reconciliationQuery(Long programId, Map<String, String> programRecordMap) {
        //以redis为标准的对账
        List<ExaminationIdentifierResult> examinationIdentifierResultRedisStandardList = reconciliationRedisStandard(programId, programRecordMap);
        //以数据库为标准的对账
        List<ExaminationIdentifierResult> examinationIdentifierResultDbStandardList = reconciliationDbStandard(programId, programRecordMap);
        return new ExaminationTotalResult(programId, examinationIdentifierResultRedisStandardList, examinationIdentifierResultDbStandardList);
    }
    /**
     * @param programId 节目id
     * @param programRecordMap redis中的节目记录
     * */
    public List<ExaminationIdentifierResult> reconciliationRedisStandard(Long programId, Map<String, String> programRecordMap) {
        //redis和数据对账结果(节目维度)
        List<ExaminationIdentifierResult> examinationIdentifierResultList = new ArrayList<>();
        //以redis记录为基准的话，redis记录不存在就直接返回
        if (CollectionUtil.isEmpty(programRecordMap)) {
            return examinationIdentifierResultList;
        }
        //key：记录标识_用户id  value：记录类型的集合
        Map<String, List<String>> identifierIdAndUserIdMap = regroup(programRecordMap);
        for (Map.Entry<String, List<String>> identifierIdAndUserIdEntry : identifierIdAndUserIdMap.entrySet()) {
            String[] split = SplitUtil.toSplit(identifierIdAndUserIdEntry.getKey());
            if (split.length != 2) {
                continue;
            }
            //标识和订单关联
            String identifierId = split[0];
            //用户id
            String userId = split[1];
            //redis中记录类型集合
            List<String> redisRecordTypeList = identifierIdAndUserIdEntry.getValue();
            //购票人订单记录中的座位 key:记录类型 value：此记录类型下的购票人订单记录集合
            Map<Integer, List<OrderTicketUserRecord>> orderTicketUserRecordMap = new HashMap<>(64);
            //查询订单
            Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class)
                    .eq(Order::getReconciliationStatus, ReconciliationStatus.RECONCILIATION_NO.getCode())
                    .eq(Order::getProgramId, programId).eq(Order::getUserId, Long.parseLong(userId))
                    .eq(Order::getIdentifierId, Long.parseLong(identifierId)));
            if (Objects.nonNull(order)) {
                //根据订单编号查询购票人订单记录
                List<OrderTicketUserRecord> orderTicketUserRecordList = 
                        orderTicketUserRecordService.list(Wrappers.lambdaQuery(OrderTicketUserRecord.class)
                                .eq(OrderTicketUserRecord::getOrderNumber, order.getOrderNumber()));
                if (CollectionUtil.isNotEmpty(orderTicketUserRecordList)) {
                    //购票人订单记录中的座位
                    orderTicketUserRecordMap = orderTicketUserRecordList.stream()
                            .collect(Collectors.groupingBy(OrderTicketUserRecord::getRecordTypeCode));
                }
            }
            List<ExaminationRecordTypeResult> examinationRecordTypeResultList = new ArrayList<>();
            for (String redisRecordType : redisRecordTypeList) {
                //根据记录类型获取对应的购票人订单记录中的座位
                List<OrderTicketUserRecord> dbOrderTicketUserRecordList = orderTicketUserRecordMap.get(RecordType.getCodeByValue(redisRecordType));
                ExaminationRecordTypeResult examinationRecordTypeResult = 
                        executeRedisAndDbExamination(programRecordMap, dbOrderTicketUserRecordList, redisRecordType, identifierId, userId);
                examinationRecordTypeResultList.add(examinationRecordTypeResult);
            }
            //redis和数据对账结果(记录标识维度)
            ExaminationIdentifierResult examinationIdentifierResult = 
                    new ExaminationIdentifierResult(identifierId, userId, examinationRecordTypeResultList);
            examinationIdentifierResultList.add(examinationIdentifierResult);
        }
        return examinationIdentifierResultList;
    }
    /**
     * @param programId 节目id
     * @param programRecordMap redis中的节目记录
     * */
    public List<ExaminationIdentifierResult> reconciliationDbStandard(Long programId, Map<String, String> programRecordMap) {
        //redis和数据对账结果(节目维度)
        List<ExaminationIdentifierResult> examinationIdentifierResultList = new ArrayList<>();
        //查询订单节目
        List<OrderProgram> orderProgramList = 
                orderProgramMapper.selectList(Wrappers.lambdaQuery(OrderProgram.class)
                        .eq(OrderProgram::getHandleStatus, HandleStatus.NO_HANDLE.getCode())
                        .eq(OrderProgram::getProgramId, programId));
        if (CollectionUtil.isEmpty(orderProgramList)) {
            return examinationIdentifierResultList;
        }
        //购票人订单记录中的座位
        List<OrderTicketUserRecord> orderTicketUserRecordList = 
                orderTicketUserRecordMapper.selectList(Wrappers.lambdaQuery(OrderTicketUserRecord.class)
                        .in(OrderTicketUserRecord::getOrderNumber, orderProgramList.stream().map(OrderProgram::getOrderNumber)
                                .collect(Collectors.toList())).eq(OrderTicketUserRecord::getReconciliationStatus, ReconciliationStatus.RECONCILIATION_NO.getCode()));
        //key：记录类型_记录标识_用户id  value：购票人订单记录
        Map<String, List<OrderTicketUserRecord>> orderTicketUserRecordMap = orderTicketUserRecordList.stream().collect(Collectors.groupingBy(record -> record.getRecordTypeValue() + GLIDE_LINE + record.getIdentifierId() + GLIDE_LINE + record.getUserId()));
        //key：记录标识_用户id  value：记录类型的集合
        Map<String, List<String>> identifierIdAndUserIdMap = regroup(orderTicketUserRecordMap);
        for (Map.Entry<String, List<String>> identifierIdAndUserIdEntry : identifierIdAndUserIdMap.entrySet()) {
            String[] split = SplitUtil.toSplit(identifierIdAndUserIdEntry.getKey());
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
                ExaminationRecordTypeResult examinationRecordTypeResult = executeRedisAndDbExamination(programRecordMap, dbOrderTicketUserRecordList, dbRecordType, identifierId, userId);
                examinationRecordTypeResultList.add(examinationRecordTypeResult);
            }
            //redis和数据对账结果(记录标识维度)
            ExaminationIdentifierResult examinationIdentifierResult = new ExaminationIdentifierResult(identifierId, userId, examinationRecordTypeResultList);
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
    
    /**
     * @param programRecordMap redis中的节目记录
     * @param dbOrderTicketUserRecordList 购票人订单记录中的座位
     * @param dbRecordType 记录类型
     * @param identifierId 标识id
     * @param userId 用户id
     * */
    public ExaminationRecordTypeResult executeRedisAndDbExamination(Map<String, String> programRecordMap, 
                                                                    List<OrderTicketUserRecord> dbOrderTicketUserRecordList, 
                                                                    String dbRecordType, String identifierId, String userId) {
        ProgramRecord programRecord = JSON.parseObject(programRecordMap.get(dbRecordType + GLIDE_LINE + identifierId + GLIDE_LINE + userId), ProgramRecord.class);
        //如果数据库和redis都没有这条记录的话
        if (CollectionUtil.isEmpty(dbOrderTicketUserRecordList) && Objects.isNull(programRecord)) {
            //redis和数据对账结果(记录类型维度)
            return new ExaminationRecordTypeResult(RecordType.getCodeByValue(dbRecordType), dbRecordType, new ExaminationSeatResult());
        }
        //如果数据库没有，redis有这条记录的话
        if (CollectionUtil.isEmpty(dbOrderTicketUserRecordList) && Objects.nonNull(programRecord)) {
            Map<Long, SeatRecord> redisSeatRecordMap = getRedisSeatRecordMap(programRecord);
            //redis记录中的座位和购票人订单记录中的座位对比
            ExaminationSeatResult examinationResult = executeExaminationSeat(redisSeatRecordMap, null);
            //redis和数据对账结果(记录类型维度)
            return new ExaminationRecordTypeResult(RecordType.getCodeByValue(dbRecordType), dbRecordType, examinationResult);
        }
        //购票人订单记录中的座位转成Map，key：座位id，value：OrderTicketUserRecord
        Map<Long, OrderTicketUserRecord> dbOrderTicketUserRecordMap = dbOrderTicketUserRecordList.stream().collect(Collectors.toMap(OrderTicketUserRecord::getSeatId, orderTicketUserRecord -> orderTicketUserRecord, (v1, v2) -> v2));
        //如果数据库有，redis没有这条记录的话
        if (CollectionUtil.isNotEmpty(dbOrderTicketUserRecordList) && Objects.isNull(programRecord)) {
            //redis记录中的座位和购票人订单记录中的座位对比
            ExaminationSeatResult examinationResult = executeExaminationSeat(null, dbOrderTicketUserRecordMap);
            //redis和数据对账结果(记录类型维度)
            return new ExaminationRecordTypeResult(RecordType.getCodeByValue(dbRecordType), dbRecordType, examinationResult);
        }
        //如果数据库和redis都有这条记录的话
        Map<Long, SeatRecord> redisSeatRecordMap = getRedisSeatRecordMap(programRecord);
        //redis记录中的座位和购票人订单记录中的座位对比
        ExaminationSeatResult examinationResult = executeExaminationSeat(redisSeatRecordMap, dbOrderTicketUserRecordMap);
        //redis和数据对账结果(记录类型维度)
        return new ExaminationRecordTypeResult(RecordType.getCodeByValue(dbRecordType), dbRecordType, examinationResult);
    }
    
    public Map<Long, SeatRecord> getRedisSeatRecordMap(ProgramRecord programRecord) {
        List<TicketCategoryRecord> ticketCategoryRecordList = programRecord.getTicketCategoryRecordList();
        //redis记录中的座位
        List<SeatRecord> seatRecordList = new ArrayList<>();
        for (TicketCategoryRecord ticketCategoryRecord : ticketCategoryRecordList) {
            seatRecordList.addAll(ticketCategoryRecord.getSeatRecordList());
        }
        //redis记录中的座位转成Map，key：座位id，value：SeatRecord
        return seatRecordList.stream().collect(Collectors.toMap(SeatRecord::getSeatId, seatRecord -> seatRecord, (v1, v2) -> v2));
    }
    /**
     * @param redisSeatRecordMap redis记录中的座位转成Map，key：座位id，value：SeatRecord
     * @param dbOrderTicketUserRecordMap 数据库中购票人订单记录中的座位转成Map，key：座位id，value：OrderTicketUserRecord
     * */
    public ExaminationSeatResult executeExaminationSeat(Map<Long, SeatRecord> redisSeatRecordMap, Map<Long, OrderTicketUserRecord> dbOrderTicketUserRecordMap) {
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
            return new ExaminationSeatResult(redisStandardStatisticCount, dbStandardStatisticCount, needToDbSeatRecordList, needToRedisSeatRecordList);
        }
        //数据库没有的话，直接构建数据
        if (CollectionUtil.isEmpty(dbOrderTicketUserRecordMap)) {
            for (Map.Entry<Long, SeatRecord> seatRecordEntry : redisSeatRecordMap.entrySet()) {
                needToDbSeatRecordList.add(seatRecordEntry.getValue());
            }
            //对比结果
            return new ExaminationSeatResult(redisStandardStatisticCount, dbStandardStatisticCount, needToDbSeatRecordList, needToRedisSeatRecordList);
        }
        //以redis记录为准，对比redis记录中的座位和购票人订单记录中的座位
        for (Map.Entry<Long, SeatRecord> seatRecordEntry : redisSeatRecordMap.entrySet()) {
            Long seatId = seatRecordEntry.getKey();
            OrderTicketUserRecord orderTicketUserRecord = dbOrderTicketUserRecordMap.get(seatId);
            //redis记录有座位，数据库记录没有座位
            if (Objects.isNull(orderTicketUserRecord)) {
                needToDbSeatRecordList.add(seatRecordEntry.getValue());
            } else {
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
            } else {
                //匹配到了
                dbStandardStatisticCount++;
            }
        }
        //对比结果
        return new ExaminationSeatResult(redisStandardStatisticCount, dbStandardStatisticCount, needToDbSeatRecordList, needToRedisSeatRecordList);
    }
    
    /**
     * 获得对比的结果
     */
    public ExaminationSimpleResult reconciliationQuerySimple(Long programId, Map<String, String> programRecordMap) {
        //对账结果
        ExaminationTotalResult examinationTotalResult = reconciliationQuery(programId, programRecordMap);
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
    
    public List<ExaminationIdentifierResult> simpleExaminationIdentifierResultList(List<ExaminationIdentifierResult> examinationIdentifierResultList) {
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
                //需要向redis中补充的座位
                List<OrderTicketUserRecord> needToRedisSeatRecordList = examinationSeatResult.getNeedToRedisSeatRecordList();
                if (CollectionUtil.isNotEmpty(needToRedisSeatRecordList)) {
                    examinationRecordTypeResultListV2.add(examinationRecordTypeResult);
                }
            }
            simpleExaminationIdentifierResultList.add(new ExaminationIdentifierResult(identifierId, userId, examinationRecordTypeResultListV2));
        }
        return simpleExaminationIdentifierResultList;
    }
    
    public ReconciliationTaskData reconciliationTask(Long programId) {
        //查询redis中的节目记录
        Map<String, String> programRecordMap = redisCache.getAllMapForHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_RECORD, programId), String.class);
        //key：记录标识_用户id   value：有顺序的ProgramRecord集合，顺序为reduce、changeStatus、increase
        Map<String, List<ProgramRecord>> needToRedisRecordMap = new HashMap<>(64);
        //对比出来的结果
        ExaminationSimpleResult examinationSimpleResult = reconciliationQuerySimple(programId, programRecordMap);
        List<ExaminationIdentifierResult> examinationIdentifierResultList = examinationSimpleResult.getExaminationIdentifierResultList();
        int reconciliationSuccessCount = 1;
        for (ExaminationIdentifierResult examinationIdentifierResult : examinationIdentifierResultList) {
            String identifierId = examinationIdentifierResult.getIdentifierId();
            String userId = examinationIdentifierResult.getUserId();
            List<ExaminationRecordTypeResult> examinationRecordTypeResultList = examinationIdentifierResult.getExaminationRecordTypeResultList();
            if (CollectionUtil.isEmpty(examinationRecordTypeResultList)) {
                if (reconciliationSuccessCount == 1) {
                    //如果没有记录类型的话，说明流水都是正常记录的，redis和数据库对账结果一致
                    programRecordHandler.add(0, programId, null, programRecordMap);
                }
                reconciliationSuccessCount++;
                continue;
            }
            List<ProgramRecord> programRecordList = new ArrayList<>();
            for (ExaminationRecordTypeResult examinationRecordTypeResult : examinationRecordTypeResultList) {
                ProgramRecord programRecord = new ProgramRecord();
                Integer recordTypeCode = examinationRecordTypeResult.getRecordTypeCode();
                String recordTypeValue = examinationRecordTypeResult.getRecordTypeValue();
                ExaminationSeatResult examinationSeatResult = examinationRecordTypeResult.getExaminationSeatResult();
                List<SeatRecord> seatRecordAllList = new ArrayList<>();
                //需要向redis中补充的数据
                List<OrderTicketUserRecord> needToRedisSeatRecordList = examinationSeatResult.getNeedToRedisSeatRecordList();
                for (OrderTicketUserRecord orderTicketUserRecord : needToRedisSeatRecordList) {
                    //构建redis的操作记录(座位层)
                    SeatRecord seatRecord = new SeatRecord();
                    seatRecord.setSeatId(orderTicketUserRecord.getSeatId());
                    seatRecord.setTicketCategoryId(orderTicketUserRecord.getTicketCategoryId());
                    seatRecord.setTicketUserId(orderTicketUserRecord.getTicketUserId());
                    if (Objects.equals(recordTypeCode, RecordType.REDUCE.getCode())) {
                        seatRecord.setBeforeStatus(SellStatus.NO_SOLD.getCode());
                        seatRecord.setAfterStatus(SellStatus.LOCK.getCode());
                    } else if (Objects.equals(recordTypeCode, RecordType.CHANGE_STATUS.getCode())) {
                        seatRecord.setBeforeStatus(SellStatus.LOCK.getCode());
                        seatRecord.setAfterStatus(SellStatus.SOLD.getCode());
                    } else if (Objects.equals(recordTypeCode, RecordType.INCREASE.getCode())) {
                        seatRecord.setBeforeStatus(SellStatus.LOCK.getCode());
                        seatRecord.setAfterStatus(SellStatus.NO_SOLD.getCode());
                    }
                    seatRecordAllList.add(seatRecord);
                }
                List<TicketCategoryRecord> ticketCategoryRecordList = new ArrayList<>();
                //key：票档id，value：seatRecord集合
                Map<Long, List<SeatRecord>> seatRecordMap = seatRecordAllList.stream().collect(Collectors.groupingBy(SeatRecord::getTicketCategoryId));
                for (Map.Entry<Long, List<SeatRecord>> seatRecordEntry : seatRecordMap.entrySet()) {
                    TicketCategoryRecord ticketCategoryRecord = new TicketCategoryRecord();
                    Long ticketCategoryId = seatRecordEntry.getKey();
                    List<SeatRecord> seatRecordList = seatRecordEntry.getValue();
                    ticketCategoryRecord.setTicketCategoryId(ticketCategoryId);
                    ticketCategoryRecord.setSeatRecordList(seatRecordList);
                    ticketCategoryRecordList.add(ticketCategoryRecord);
                }
                //构建redis的操作记录
                programRecord.setRecordType(recordTypeValue);
                programRecord.setTimestamp(System.currentTimeMillis());
                programRecord.setTicketCategoryRecordList(ticketCategoryRecordList);
                programRecordList.add(programRecord);
            }
            //排序，顺序为reduce、changeStatus、increase
            programRecordList.sort(Comparator.comparingInt(pr -> RecordType.getCodeByValue(pr.getRecordType())));
            needToRedisRecordMap.put(identifierId + GLIDE_LINE + userId, programRecordList);
            //向redis添加数据
            Map<String, ProgramRecord> addRedisRecordData = compensateRedisRecord(programId, needToRedisRecordMap, programRecordMap);
            ReconciliationTaskData reconciliationTaskData = new ReconciliationTaskData();
            reconciliationTaskData.setProgramId(programId);
            reconciliationTaskData.setAddRedisRecordData(addRedisRecordData);
            return reconciliationTaskData;
        } 
        return null;
    }
    
    /**
     * @param programId 节目id
     * @param needToRedisRecordMap value:记录标识_用户id   value：有顺序的ProgramRecord集合，顺序为reduce、changeStatus、increase
     * @param programRecordMap 查询redis中的节目记录
     * */
    public Map<String, ProgramRecord> compensateRedisRecord(Long programId, 
                                                            Map<String, List<ProgramRecord>> needToRedisRecordMap, 
                                                            Map<String, String> programRecordMap) {
        //需要补充的票档id集合
        Set<Long> ticketCategoryIdSet = getTicketCategoryIdSet(needToRedisRecordMap);
        //获取节目票档集合
        TicketCategoryListDto ticketCategoryListDto = new TicketCategoryListDto();
        ticketCategoryListDto.setProgramId(programId);
        ticketCategoryListDto.setTicketCategoryIdList(ticketCategoryIdSet);
        ApiResponse<List<TicketCategoryDetailVo>> programApiResponse = programClient.selectList(ticketCategoryListDto);
        if (!Objects.equals(programApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new DaMaiFrameException(programApiResponse);
        }
        List<TicketCategoryDetailVo> ticketCategoryDetailVoList = programApiResponse.getData();
        
        //节目票档集合转成map，key：节目票档id，value：余票数量
        Map<Long, Long> ticketCategoryRemainNumberMap = ticketCategoryDetailVoList.stream().collect(Collectors.toMap(TicketCategoryDetailVo::getId, TicketCategoryDetailVo::getRemainNumber, (v1, v2) -> v2));
        //key：记录标识_用户id value：有顺序的ProgramRecord集合，顺序为reduce、changeStatus、increase
        for (Map.Entry<String, List<ProgramRecord>> programRecordEntry : needToRedisRecordMap.entrySet()) {
            //redis中记录类型集合，顺序为reduce、changeStatus、increase
            List<ProgramRecord> programRecordList = programRecordEntry.getValue();
            //逆向还原出扣减/恢复余票数量的记录
            restoreSingleOrder(programRecordList, ticketCategoryRemainNumberMap);
        }
        return addRedisRecord(programId, needToRedisRecordMap, programRecordMap);
    }
    
    /**
     * 逆向还原单笔订单的所有 ProgramRecord
     *
     * @param programRecords 按时间正序（最早到最新）排列的记录列表，顺序为reduce、changeStatus、increase
     * @param ticketCategoryRemainNumberMap key：ticketCategoryId，value：当前（最新）剩余票数；会被本方法原地回退
     */
    public void restoreSingleOrder(List<ProgramRecord> programRecords, Map<Long, Long> ticketCategoryRemainNumberMap) {
        
        //1. 把“从最早到最新”的列表倒过来，increase、changeStatus、reduce 最新地记录先还原
        Collections.reverse(programRecords);
        
        //2. 逐条记录做逆向“撤销”
        for (ProgramRecord programRecord : programRecords) {
            //reduce、changeStatus、increase
            String recordType = programRecord.getRecordType();
            //2.1 统计本条记录中，每个票档的 changeAmt（long）
            //所有对应 TicketCategoryRecord.seatRecordList.size() 之和
            //key：票档id，value：扣减或者恢复数量  
            Map<Long, Long> changeAmtMap = programRecord.getTicketCategoryRecordList().stream().collect(Collectors.groupingBy(TicketCategoryRecord::getTicketCategoryId, Collectors.summingLong(tcr -> tcr.getSeatRecordList().size())));
            //2.2 对本条记录每个票档做逆向计算
            for (Map.Entry<Long, Long> entry : changeAmtMap.entrySet()) {
                Long categoryId = entry.getKey();
                long changeAmt = entry.getValue();
                
                //当前（最新）剩余
                long current = ticketCategoryRemainNumberMap.getOrDefault(categoryId, 0L);
                
                long beforeAmount;
                long afterAmount;
                
                if (Objects.equals(recordType, RecordType.REDUCE.getValue())) {
                    // 原操作扣减 -> 逆向要加回
                    afterAmount = current;
                    beforeAmount = current + changeAmt;
                    ticketCategoryRemainNumberMap.put(categoryId, beforeAmount);
                    
                } else if (Objects.equals(recordType, RecordType.INCREASE.getValue())) {
                    // 原操作恢复 -> 逆向要再扣减
                    afterAmount = current;
                    beforeAmount = current - changeAmt;
                    ticketCategoryRemainNumberMap.put(categoryId, beforeAmount);
                    
                } else {
                    // 状态变更，不改票数
                    beforeAmount = current;
                    afterAmount = current;
                    //ticketCategoryRemainNumberMap 保持不变
                }
                
                //2.3 回填所有对应 TicketCategoryRecord 的字段
                for (TicketCategoryRecord tcr : programRecord.getTicketCategoryRecordList()) {
                    if (tcr.getTicketCategoryId().equals(categoryId)) {
                        tcr.setBeforeAmount(beforeAmount);
                        tcr.setAfterAmount(afterAmount);
                        tcr.setChangeAmount(Objects.equals(recordType, RecordType.CHANGE_STATUS.getValue()) ? 0L : changeAmt);
                    }
                }
            }
        }
        //3. 还原完毕后，如果后续需要再按正序使用 programRecords，可再反转回来
        Collections.reverse(programRecords);
    }
    
    public Map<String, ProgramRecord> addRedisRecord(Long programId, Map<String, List<ProgramRecord>> needToRedisRecordMap, Map<String, String> programRecordMap) {
        Set<Long> ticketCategoryIdSet = getTicketCategoryIdSet(needToRedisRecordMap);
        //构建出要向redis添加记录的结构
        //key：记录类型_记录标识_用户id value：ProgramRecord
        Map<String, ProgramRecord> completeRedisCordMap = new HashMap<>(64);
        //key：记录标识_用户id value：有顺序的ProgramRecord集合，顺序为reduce、changeStatus、increase
        for (Map.Entry<String, List<ProgramRecord>> redisRecordEntry : needToRedisRecordMap.entrySet()) {
            String key = redisRecordEntry.getKey();
            List<ProgramRecord> programRecordList = redisRecordEntry.getValue();
            for (ProgramRecord programRecord : programRecordList) {
                completeRedisCordMap.put(programRecord.getRecordType() + GLIDE_LINE + key, programRecord);
            }
        }
        for (Long ticketCategoryId : ticketCategoryIdSet) {
            //删除redis中的座位
            seatHandler.delRedisSeatData(programId, ticketCategoryId);
            //删除redis中的余票数量
            ticketRemainNumberHandler.delRedisSeatData(programId, ticketCategoryId);
        }
        //向redis中添加记录
        programRecordHandler.add(0, programId, completeRedisCordMap, programRecordMap);
        return completeRedisCordMap;
    }
    
    public Set<Long> getTicketCategoryIdSet(Map<String, List<ProgramRecord>> needToRedisRecordMap) {
        Set<Long> ticketCategoryIdSet = new HashSet<>();
        for (Map.Entry<String, List<ProgramRecord>> programRecordEntry : needToRedisRecordMap.entrySet()) {
            List<ProgramRecord> programRecordList = programRecordEntry.getValue();
            for (ProgramRecord programRecord : programRecordList) {
                List<TicketCategoryRecord> ticketCategoryRecordList = programRecord.getTicketCategoryRecordList();
                for (TicketCategoryRecord ticketCategoryRecord : ticketCategoryRecordList) {
                    ticketCategoryIdSet.add(ticketCategoryRecord.getTicketCategoryId());
                }
            }
        }
        return ticketCategoryIdSet;
    }
}
