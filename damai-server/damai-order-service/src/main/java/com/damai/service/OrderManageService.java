package com.damai.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.damai.core.RedisKeyManage;
import com.damai.domain.ProgramRecord;
import com.damai.domain.SeatRecord;
import com.damai.domain.TicketCategoryRecord;
import com.damai.dto.RecordManageDto;
import com.damai.entity.Order;
import com.damai.entity.OrderProgram;
import com.damai.entity.OrderTicketUserRecord;
import com.damai.enums.ReconciliationStatus;
import com.damai.enums.RecordType;
import com.damai.enums.SellStatus;
import com.damai.mapper.OrderMapper;
import com.damai.mapper.OrderProgramMapper;
import com.damai.mapper.OrderTicketUserRecordMapper;
import com.damai.page.PageUtil;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.util.DateUtils;
import com.damai.util.StringUtil;
import com.damai.vo.RecordOrderManageVo;
import com.damai.vo.RecordOrderTickerUserManageVo;
import com.damai.vo.TicketCategoryVo;
import groovy.util.logging.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.damai.constant.Constant.GLIDE_LINE;

@Slf4j
@Service
public class OrderManageService {
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private OrderProgramMapper orderProgramMapper;
    
    @Autowired
    private OrderTicketUserRecordMapper orderTicketUserRecordMapper;
    
    @Autowired
    private RedisCache redisCache;
    
    @Autowired
    private OrderTaskService orderTaskService;
    
    
    public IPage<RecordOrderManageVo> reconciliationList(RecordManageDto recordManageDto) {
        IPage<RecordOrderManageVo> recordOrderManageVoPage = new Page<>(recordManageDto.getPageNum(), recordManageDto.getPageSize());
        //查询前5分钟订单节目
        List<OrderProgram> orderProgramList =
                orderProgramMapper.selectList(Wrappers.lambdaQuery(OrderProgram.class)
                        .eq(OrderProgram::getProgramId, recordManageDto.getProgramId())
                        .le(OrderProgram::getCreateTime, DateUtils.addMinute(DateUtils.now(), -5)));
        if (CollectionUtil.isEmpty(orderProgramList)) {
            return recordOrderManageVoPage;
        }
        //获取订单编号集合
        List<Long> orderNumberList = orderProgramList.stream().map(OrderProgram::getOrderNumber).toList();
        
        //根据订单编号查询订单
        IPage<Order> orderPage = orderMapper.selectPage(PageUtil.getPageParams(recordManageDto.getPageNum(),
                recordManageDto.getPageSize()), Wrappers.lambdaQuery(Order.class)
                .in(Order::getOrderNumber, orderNumberList));
        if (CollectionUtil.isEmpty(orderPage.getRecords())) {
            return recordOrderManageVoPage;
        }
        //根据查询出的订单获取唯一标识集合
        List<Long> identifierIdList = orderPage.getRecords().stream().map(Order::getIdentifierId).toList();
        
        //根据唯一标识集合查询购票人订单记录
        List<OrderTicketUserRecord> allOrderTicketUserRecordList =
                orderTicketUserRecordMapper.selectList(Wrappers.lambdaQuery(OrderTicketUserRecord.class)
                        .in(OrderTicketUserRecord::getIdentifierId, identifierIdList));
        //根据唯一标识分组 key:identifierId value:OrderTicketUserRecord集合
        Map<Long, List<OrderTicketUserRecord>> allOrderTicketUserRecordMap =
                allOrderTicketUserRecordList.stream().collect(Collectors.groupingBy(OrderTicketUserRecord::getIdentifierId));
        
        //在redis查询节目票档信息
        List<TicketCategoryVo> ticketCategoryVoList =
                redisCache.getValueIsList(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_TICKET_CATEGORY_LIST,
                        recordManageDto.getProgramId()), TicketCategoryVo.class);
        Map<Long, String> ticketCategoryVoMap = ticketCategoryVoList.stream().collect(Collectors.toMap(TicketCategoryVo::getId, TicketCategoryVo::getIntroduce));
        
        //查询redis中的节目记录,包括未对账和已对账的
        Map<String, String> redisProgramRecordMap = redisCache.getAllMapForHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_RECORD, recordManageDto.getProgramId()), String.class);
        redisProgramRecordMap.putAll(redisCache.getAllMapForHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_RECORD_FINISH, recordManageDto.getProgramId()), String.class));
        
        List<RecordOrderManageVo> recordOrderManageVoList = new ArrayList<>();
        //循环订单
        for (Order order : orderPage.getRecords()) {
            RecordOrderManageVo recordOrderManageVo = new RecordOrderManageVo();
            recordOrderManageVo.setOrderNumber(order.getOrderNumber());
            recordOrderManageVo.setProgramId(order.getProgramId());
            //设置对账状态
            recordOrderManageVo.setReconciliationStatus(order.getReconciliationStatus());
            recordOrderManageVo.setReconciliationStatusName(ReconciliationStatus.getMsg(order.getReconciliationStatus()));
            //从数据库中获取每个订单下对应的购票人订单记录
            List<OrderTicketUserRecord> orderTicketUserRecordList = allOrderTicketUserRecordMap.get(order.getIdentifierId());
            if (CollectionUtil.isEmpty(orderTicketUserRecordList)) {
                continue;
            }
            List<RecordOrderTickerUserManageVo> recordOrderTickerUserManageVoList = new ArrayList<>();
            //循环购票人订单
            for (OrderTicketUserRecord orderTicketUserRecord : orderTicketUserRecordList) {
                RecordOrderTickerUserManageVo recordOrderTickerUserManageVo = new RecordOrderTickerUserManageVo();
                BeanUtils.copyProperties(orderTicketUserRecord, recordOrderTickerUserManageVo);
                recordOrderTickerUserManageVo.setReconciliationStatusName(ReconciliationStatus.getMsg(order.getReconciliationStatus()));
                recordOrderTickerUserManageVo.setDbRecordTypeCode(orderTicketUserRecord.getRecordTypeCode());
                recordOrderTickerUserManageVo.setDbRecordTypeValue(orderTicketUserRecord.getRecordTypeValue());
                recordOrderTickerUserManageVo.setDbRecordTypeName(RecordType.getMsg(orderTicketUserRecord.getRecordTypeCode()));
                recordOrderTickerUserManageVo.setTicketCategoryName(ticketCategoryVoMap.get(orderTicketUserRecord.getTicketCategoryId()));
                boolean redisRecordFlag = true;
                //从redis获取节目记录
                String redisProgramRecordStr = redisProgramRecordMap.get(orderTicketUserRecord.getRecordTypeValue() + GLIDE_LINE + orderTicketUserRecord.getIdentifierId() + GLIDE_LINE + orderTicketUserRecord.getUserId());
                if (StringUtil.isNotEmpty(redisProgramRecordStr)) {
                    ProgramRecord redisProgramRecord = JSON.parseObject(redisProgramRecordStr, ProgramRecord.class);
                    SeatRecord redisSeatRecord = getSeatRecord(redisProgramRecord, orderTicketUserRecord);
                    if (Objects.nonNull(redisSeatRecord)) {
                        //设置redis的记录类型
                        recordOrderTickerUserManageVo.setRedisRecordTypeName(RecordType.getMsgByValue(redisProgramRecord.getRecordType()));
                        //设置redis的记录座位操作之前状态
                        recordOrderTickerUserManageVo.setRedisBeforeSeatStatusName(SellStatus.getMsg(redisSeatRecord.getBeforeStatus()));
                        //设置redis的记录座位操作之后状态
                        recordOrderTickerUserManageVo.setRedisAfterSeatStatusName(SellStatus.getMsg(redisSeatRecord.getAfterStatus()));
                    }
                }
                recordOrderTickerUserManageVoList.add(recordOrderTickerUserManageVo);
            }
            recordOrderManageVo.setRecordOrderTickerUserManageVoList(recordOrderTickerUserManageVoList);
            recordOrderManageVoList.add(recordOrderManageVo);
        }
        BeanUtils.copyProperties(orderPage, recordOrderManageVoPage);
        recordOrderManageVoPage.setRecords(recordOrderManageVoList);
        return recordOrderManageVoPage;
    }
    
    
    public SeatRecord getSeatRecord(ProgramRecord programRecord,OrderTicketUserRecord orderTicketUserRecord){
        //获取redis的票档维度的记录
        Map<Long, TicketCategoryRecord> redisTicketCategoryRecordMap = programRecord.getTicketCategoryRecordList().stream().collect(Collectors.toMap(TicketCategoryRecord::getTicketCategoryId, v -> v, (v1, v2) -> v2));
        //根据数据库中的购票人订单记录的票档id获取redis中的票档维度的记录
        TicketCategoryRecord redisTicketCategoryRecord = redisTicketCategoryRecordMap.get(orderTicketUserRecord.getTicketCategoryId());
        if (Objects.isNull(redisTicketCategoryRecord)) {
            return null;
        }
        //再从redis中的票档维度的记录获取座位维度的记录
        Map<Long, SeatRecord> redisSeatRecordMap = redisTicketCategoryRecord.getSeatRecordList().stream().collect(Collectors.toMap(SeatRecord::getSeatId, v -> v, (v1, v2) -> v2));
        SeatRecord redisSeatRecord = redisSeatRecordMap.get(orderTicketUserRecord.getSeatId());
        if (Objects.isNull(redisSeatRecord)) {
            return null;
        }
        return redisSeatRecord;
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
            // "985033500750127104"
            String newKey = parts[1];
            // 累加到结果 Map 中
            resultMap.computeIfAbsent(newKey, k -> new ArrayList<>()).add(action);
        }
        return resultMap;
    }
}
