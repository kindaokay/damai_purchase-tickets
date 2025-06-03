package com.damai.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baidu.fsg.uid.UidGenerator;
import com.damai.BusinessThreadPool;
import com.damai.client.OrderClient;
import com.damai.common.ApiResponse;
import com.damai.core.RedisKeyManage;
import com.damai.domain.OrderCreateMq;
import com.damai.domain.PurchaseSeat;
import com.damai.dto.DelayOrderCancelDto;
import com.damai.dto.OrderCreateDto;
import com.damai.dto.OrderTicketUserCreateDto;
import com.damai.dto.ProgramOrderCreateDto;
import com.damai.dto.SeatDto;
import com.damai.entity.ProgramRecordTask;
import com.damai.entity.ProgramShowTime;
import com.damai.enums.BaseCode;
import com.damai.enums.OrderStatus;
import com.damai.enums.RecordType;
import com.damai.enums.SellStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.mapper.ProgramRecordTaskMapper;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.delaysend.DelayOrderCancelSend;
import com.damai.service.domain.CreateOrderTemporaryData;
import com.damai.service.kafka.CreateOrderMqDomain;
import com.damai.service.kafka.CreateOrderSend;
import com.damai.service.lua.ProgramCacheCreateOrderData;
import com.damai.service.lua.ProgramCacheCreateOrderResolutionOperate;
import com.damai.service.lua.ProgramCacheResolutionOperate;
import com.damai.service.tool.SeatMatch;
import com.damai.util.DateUtils;
import com.damai.vo.ProgramVo;
import com.damai.vo.SeatVo;
import com.damai.vo.TicketCategoryVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.damai.constant.Constant.GLIDE_LINE;
import static com.damai.service.constant.ProgramOrderConstant.ORDER_TABLE_COUNT;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目订单 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class ProgramOrderService {
    
    @Autowired
    private OrderClient orderClient;
    
    @Autowired
    private UidGenerator uidGenerator;
    
    @Autowired
    private ProgramCacheResolutionOperate programCacheResolutionOperate;
    
    @Autowired
    ProgramCacheCreateOrderResolutionOperate programCacheCreateOrderResolutionOperate;
    
    @Autowired
    private DelayOrderCancelSend delayOrderCancelSend;
    
    @Autowired
    private CreateOrderSend createOrderSend;
    
    @Autowired
    private ProgramService programService;
    
    @Autowired
    private ProgramShowTimeService programShowTimeService;
    
    @Autowired
    private TicketCategoryService ticketCategoryService;
    
    @Autowired
    private SeatService seatService;
    
    @Autowired
    private ProgramRecordTaskMapper programRecordTaskMapper;
    
    public List<TicketCategoryVo> getTicketCategoryList(ProgramOrderCreateDto programOrderCreateDto, Date showTime){
        List<TicketCategoryVo> getTicketCategoryVoList = new ArrayList<>();
        List<TicketCategoryVo> ticketCategoryVoList =
                ticketCategoryService.selectTicketCategoryListByProgramIdMultipleCache(programOrderCreateDto.getProgramId(),
                        showTime);
        Map<Long, TicketCategoryVo> ticketCategoryVoMap =
                ticketCategoryVoList.stream()
                        .collect(Collectors.toMap(TicketCategoryVo::getId, ticketCategoryVo -> ticketCategoryVo));
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            for (SeatDto seatDto : seatDtoList) {
                TicketCategoryVo ticketCategoryVo = ticketCategoryVoMap.get(seatDto.getTicketCategoryId());
                if (Objects.nonNull(ticketCategoryVo)) {
                    getTicketCategoryVoList.add(ticketCategoryVo);
                }else {
                    throw new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2);
                }
            }
        } else {
            TicketCategoryVo ticketCategoryVo = ticketCategoryVoMap.get(programOrderCreateDto.getTicketCategoryId());
            if (Objects.nonNull(ticketCategoryVo)) {
                getTicketCategoryVoList.add(ticketCategoryVo);
            }else {
                throw new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2);
            }
        }
        return getTicketCategoryVoList;
    }
    
    public String create(ProgramOrderCreateDto programOrderCreateDto,Integer orderVersion) {
        ProgramShowTime programShowTime =
                programShowTimeService.selectProgramShowTimeByProgramIdMultipleCache(programOrderCreateDto.getProgramId());
        List<TicketCategoryVo> getTicketCategoryList = 
                getTicketCategoryList(programOrderCreateDto,programShowTime.getShowTime());
        BigDecimal parameterOrderPrice = new BigDecimal("0");
        BigDecimal databaseOrderPrice = new BigDecimal("0");
        List<SeatVo> purchaseSeatList = new ArrayList<>();
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        List<SeatVo> seatVoList = new ArrayList<>();
        Map<String, Long> ticketCategoryRemainNumber = new HashMap<>(16);
        for (TicketCategoryVo ticketCategory : getTicketCategoryList) {
            List<SeatVo> allSeatVoList = 
                    seatService.selectSeatResolution(programOrderCreateDto.getProgramId(), ticketCategory.getId(), 
                            DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime()), TimeUnit.SECONDS);
            seatVoList.addAll(allSeatVoList.stream().
                    filter(seatVo -> seatVo.getSellStatus().equals(SellStatus.NO_SOLD.getCode())).toList());
            ticketCategoryRemainNumber.putAll(ticketCategoryService.getRedisRemainNumberResolution(
                    programOrderCreateDto.getProgramId(),ticketCategory.getId()));
        }
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            Map<Long, Long> seatTicketCategoryDtoCount = seatDtoList.stream()
                    .collect(Collectors.groupingBy(SeatDto::getTicketCategoryId, Collectors.counting()));
            for (Entry<Long, Long> entry : seatTicketCategoryDtoCount.entrySet()) {
                Long ticketCategoryId = entry.getKey();
                Long purchaseCount = entry.getValue();
                Long remainNumber = Optional.ofNullable(ticketCategoryRemainNumber.get(String.valueOf(ticketCategoryId)))
                        .orElseThrow(() -> new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2));
                if (purchaseCount > remainNumber) {
                    throw new DaMaiFrameException(BaseCode.TICKET_REMAIN_NUMBER_NOT_SUFFICIENT);
                }
            }
            Map<String, SeatVo> seatVoMap = seatVoList.stream().collect(Collectors
                    .toMap(seat -> seat.getRowCode() + "-" + seat.getColCode(), seat -> seat, (v1, v2) -> v2));
            for (SeatDto seatDto : seatDtoList) {
                SeatVo seatVo = seatVoMap.get(seatDto.getRowCode() + "-" + seatDto.getColCode());
                if (Objects.isNull(seatVo)) {
                    throw new DaMaiFrameException(BaseCode.SEAT_IS_NOT_NOT_SOLD);
                }
                purchaseSeatList.add(seatVo);
                parameterOrderPrice = parameterOrderPrice.add(seatDto.getPrice());
                databaseOrderPrice = databaseOrderPrice.add(seatVo.getPrice());
            }
            if (parameterOrderPrice.compareTo(databaseOrderPrice) > 0) {
                throw new DaMaiFrameException(BaseCode.PRICE_ERROR);
            }
        }else {
            Long ticketCategoryId = programOrderCreateDto.getTicketCategoryId();
            Integer ticketCount = programOrderCreateDto.getTicketCount();
            Long remainNumber = Optional.ofNullable(ticketCategoryRemainNumber.get(String.valueOf(ticketCategoryId)))
                    .orElseThrow(() -> new DaMaiFrameException(BaseCode.TICKET_CATEGORY_NOT_EXIST_V2));
            if (ticketCount > remainNumber) {
                throw new DaMaiFrameException(BaseCode.TICKET_REMAIN_NUMBER_NOT_SUFFICIENT);
            }
            purchaseSeatList = SeatMatch.findAdjacentSeatVos(seatVoList.stream().filter(seatVo ->
                    Objects.equals(seatVo.getTicketCategoryId(), ticketCategoryId)).collect(Collectors.toList()), ticketCount);
            if (purchaseSeatList.size() < ticketCount) {
                throw new DaMaiFrameException(BaseCode.SEAT_OCCUPY);
            }
        }
        updateProgramCacheDataResolution(programOrderCreateDto.getProgramId(),purchaseSeatList,OrderStatus.NO_PAY);
        return doCreate(programOrderCreateDto,purchaseSeatList,orderVersion);
    }
    
    
    public String createNew(ProgramOrderCreateDto programOrderCreateDto,Integer orderVersion) {
        CreateOrderTemporaryData createOrderTemporaryData = createOrderOperateProgramCacheResolution(programOrderCreateDto);
        List<SeatVo> purchaseSeatList = createOrderTemporaryData.getPurchaseSeatList().stream().map(purchaseSeat -> {
            SeatVo seatVo = new SeatVo();
            BeanUtils.copyProperties(purchaseSeat,seatVo);
            return seatVo;
        }).collect(Collectors.toList());
        return doCreate(programOrderCreateDto,purchaseSeatList,orderVersion);
    }
    
    public String createNewAsync(ProgramOrderCreateDto programOrderCreateDto,Integer orderVersion) {
        //操作redis
        CreateOrderTemporaryData createOrderTemporaryData = createOrderOperateProgramCacheResolution(programOrderCreateDto);
        //发送kafka
        return doCreateV2(programOrderCreateDto,createOrderTemporaryData,orderVersion);
    }
    
    public CreateOrderTemporaryData createOrderOperateProgramCacheResolution(ProgramOrderCreateDto programOrderCreateDto){
        //从多级缓存中查找节目演出时间ProgramShowTime
        ProgramShowTime programShowTime =
                programShowTimeService.selectProgramShowTimeByProgramIdMultipleCache(programOrderCreateDto.getProgramId());
        //查询对应的票档类型
        List<TicketCategoryVo> getTicketCategoryList =
                getTicketCategoryList(programOrderCreateDto,programShowTime.getShowTime());
        //遍历得到的票档
        for (TicketCategoryVo ticketCategory : getTicketCategoryList) {
            //从缓存中查询座位，如果缓存不存在，则从数据库查询后再放入缓存
            seatService.selectSeatResolution(programOrderCreateDto.getProgramId(), ticketCategory.getId(),
                    DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime()), TimeUnit.SECONDS);
            //从缓存中查询余票数量，如果缓存不存在，则从数据库查询后再放入缓存
            ticketCategoryService.getRedisRemainNumberResolution(
                    programOrderCreateDto.getProgramId(),ticketCategory.getId());
        }
        Long programId = programOrderCreateDto.getProgramId();
        List<SeatDto> seatDtoList = programOrderCreateDto.getSeatDtoList();
        List<String> keys = new ArrayList<>();
        String[] data = new String[3];
        //更新票档数据集合
        JSONArray jsonArray = new JSONArray();
        //添加座位数据集合
        JSONArray addSeatDatajsonArray = new JSONArray();
        if (CollectionUtil.isNotEmpty(seatDtoList)) {
            keys.add("1");
            Map<Long, List<SeatDto>> seatTicketCategoryDtoCount = seatDtoList.stream()
                    .collect(Collectors.groupingBy(SeatDto::getTicketCategoryId));
            for (Entry<Long, List<SeatDto>> entry : seatTicketCategoryDtoCount.entrySet()) {
                Long ticketCategoryId = entry.getKey();
                int ticketCount = entry.getValue().size();
                //这里是计算更新票档数据
                JSONObject jsonObject = new JSONObject();
                //票档数量的key
                jsonObject.put("programTicketRemainNumberHashKey",RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, ticketCategoryId).getRelKey());
                //票档id
                jsonObject.put("ticketCategoryId",ticketCategoryId);
                //扣减余票数量
                jsonObject.put("ticketCount",ticketCount);
                jsonArray.add(jsonObject);
                
                JSONObject seatDatajsonObject = new JSONObject();
                //未售卖座位的hash的key
                seatDatajsonObject.put("seatNoSoldHashKey",RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
                //座位数据
                seatDatajsonObject.put("seatDataList",JSON.toJSONString(entry.getValue()));
                addSeatDatajsonArray.add(seatDatajsonObject);
            }
        }else {
            keys.add("2");
            Long ticketCategoryId = programOrderCreateDto.getTicketCategoryId();
            Integer ticketCount = programOrderCreateDto.getTicketCount();
            JSONObject jsonObject = new JSONObject();
            //票档数量的key
            jsonObject.put("programTicketRemainNumberHashKey",RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, ticketCategoryId).getRelKey());
            //票档id
            jsonObject.put("ticketCategoryId",ticketCategoryId);
            //扣减余票数量
            jsonObject.put("ticketCount",ticketCount);
            //未售卖座位的hash的key
            jsonObject.put("seatNoSoldHashKey",RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, ticketCategoryId).getRelKey());
            jsonArray.add(jsonObject);
        }
        //未售卖座位hash的key(占位符形式)
        keys.add(RedisKeyBuild.getRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH));
        //锁定座位hash的key(占位符形式)
        keys.add(RedisKeyBuild.getRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH));
        keys.add(String.valueOf(programOrderCreateDto.getProgramId()));
        //记录的key(占位符形式)
        keys.add(RedisKeyBuild.getRedisKey(RedisKeyManage.PROGRAM_RECORD));
        //记录的标识
        Long identifierId = uidGenerator.getUid();
        //把记录的标识id放进去
        keys.add(RecordType.REDUCE.getValue() + GLIDE_LINE + identifierId + GLIDE_LINE + programOrderCreateDto.getUserId());
        //记录的类型
        keys.add(RecordType.REDUCE.getValue());
        data[0] = JSON.toJSONString(jsonArray);
        data[1] = JSON.toJSONString(addSeatDatajsonArray);
        //购票人id集合
        data[2] = JSON.toJSONString(programOrderCreateDto.getTicketUserIdList());
        //执行lua脚本
        ProgramCacheCreateOrderData programCacheCreateOrderData =
                programCacheCreateOrderResolutionOperate.programCacheOperate(keys, data);
        if (!Objects.equals(programCacheCreateOrderData.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new DaMaiFrameException(Objects.requireNonNull(BaseCode.getRc(programCacheCreateOrderData.getCode())));
        }
        return new CreateOrderTemporaryData(identifierId,programCacheCreateOrderData.getPurchaseSeatList());
    }
    
    private String doCreate(ProgramOrderCreateDto programOrderCreateDto,List<SeatVo> purchaseSeatList,Integer orderVersion){
        OrderCreateDto orderCreateDto = buildCreateOrderParam(programOrderCreateDto, purchaseSeatList, orderVersion);
        
        String orderNumber = createOrderByRpc(orderCreateDto,purchaseSeatList);
        
        DelayOrderCancelDto delayOrderCancelDto = new DelayOrderCancelDto();
        delayOrderCancelDto.setOrderNumber(orderCreateDto.getOrderNumber());
        delayOrderCancelSend.sendMessage(JSON.toJSONString(delayOrderCancelDto));
        
        return orderNumber;
    }
    
    private String doCreateV2(ProgramOrderCreateDto programOrderCreateDto,
                              CreateOrderTemporaryData createOrderTemporaryData,
                              Integer orderVersion){
        OrderCreateDto orderCreateDto = buildCreateOrderParamV2(programOrderCreateDto.getProgramId(),
                programOrderCreateDto.getUserId(), createOrderTemporaryData.getPurchaseSeatList(),orderVersion);
        OrderCreateMq orderCreateMq = new OrderCreateMq();
        BeanUtils.copyProperties(orderCreateDto,orderCreateMq);
        orderCreateMq.setIdentifierId(createOrderTemporaryData.getIdentifierId());
        //插入节目记录任务
        BusinessThreadPool.execute(() -> createProgramRecordTask(orderCreateMq.getProgramId()));
        //创建订单
        String orderNumber = createOrderByMq(orderCreateMq,createOrderTemporaryData.getPurchaseSeatList());
        DelayOrderCancelDto delayOrderCancelDto = new DelayOrderCancelDto();
        delayOrderCancelDto.setOrderNumber(orderCreateDto.getOrderNumber());
        delayOrderCancelSend.sendMessage(JSON.toJSONString(delayOrderCancelDto));
        
        return orderNumber;
    }
    
    public void createProgramRecordTask(Long programId){
        ProgramRecordTask programRecordTask = new ProgramRecordTask();
        programRecordTask.setId(uidGenerator.getUid());
        programRecordTask.setProgramId(programId);
        programRecordTask.setCreateTime(DateUtils.now());
        programRecordTask.setEditTime(DateUtils.now());
        programRecordTaskMapper.insert(programRecordTask);
    }
    
    private OrderCreateDto buildCreateOrderParam(ProgramOrderCreateDto programOrderCreateDto,
                                                 List<SeatVo> purchaseSeatList,
                                                 Integer orderVersion){
        ProgramVo programVo = programService.simpleGetProgramAndShowMultipleCache(programOrderCreateDto.getProgramId());
        OrderCreateDto orderCreateDto = new OrderCreateDto();
        orderCreateDto.setOrderNumber(uidGenerator.getOrderNumber(programOrderCreateDto.getUserId(),ORDER_TABLE_COUNT));
        orderCreateDto.setProgramId(programOrderCreateDto.getProgramId());
        orderCreateDto.setProgramItemPicture(programVo.getItemPicture());
        orderCreateDto.setUserId(programOrderCreateDto.getUserId());
        orderCreateDto.setProgramTitle(programVo.getTitle());
        orderCreateDto.setProgramPlace(programVo.getPlace());
        orderCreateDto.setProgramShowTime(programVo.getShowTime());
        orderCreateDto.setProgramPermitChooseSeat(programVo.getPermitChooseSeat());
        BigDecimal databaseOrderPrice =
                purchaseSeatList.stream().map(SeatVo::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        orderCreateDto.setOrderPrice(databaseOrderPrice);
        orderCreateDto.setCreateOrderTime(DateUtils.now());
        orderCreateDto.setOrderVersion(orderVersion);
        
        List<Long> ticketUserIdList = programOrderCreateDto.getTicketUserIdList();
        List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = new ArrayList<>();
        for (int i = 0; i < ticketUserIdList.size(); i++) {
            Long ticketUserId = ticketUserIdList.get(i);
            OrderTicketUserCreateDto orderTicketUserCreateDto = new OrderTicketUserCreateDto();
            orderTicketUserCreateDto.setOrderNumber(orderCreateDto.getOrderNumber());
            orderTicketUserCreateDto.setProgramId(programOrderCreateDto.getProgramId());
            orderTicketUserCreateDto.setUserId(programOrderCreateDto.getUserId());
            orderTicketUserCreateDto.setTicketUserId(ticketUserId);
            SeatVo seatVo =
                    Optional.ofNullable(purchaseSeatList.get(i))
                            .orElseThrow(() -> new DaMaiFrameException(BaseCode.SEAT_NOT_EXIST));
            orderTicketUserCreateDto.setSeatId(seatVo.getId());
            orderTicketUserCreateDto.setSeatInfo(seatVo.getRowCode()+"排"+seatVo.getColCode()+"列");
            orderTicketUserCreateDto.setTicketCategoryId(seatVo.getTicketCategoryId());
            orderTicketUserCreateDto.setOrderPrice(seatVo.getPrice());
            orderTicketUserCreateDto.setCreateOrderTime(DateUtils.now());
            orderTicketUserCreateDtoList.add(orderTicketUserCreateDto);
        }
        
        orderCreateDto.setOrderTicketUserCreateDtoList(orderTicketUserCreateDtoList);
        
        return orderCreateDto;
    }

    private OrderCreateDto buildCreateOrderParamV2(Long programId,Long userId,List<PurchaseSeat> purchaseSeatList,Integer orderVersion){
        ProgramVo programVo = programService.simpleGetProgramAndShowMultipleCache(programId);
        OrderCreateDto orderCreateDto = new OrderCreateDto();
        orderCreateDto.setOrderNumber(uidGenerator.getOrderNumber(userId,ORDER_TABLE_COUNT));
        orderCreateDto.setProgramId(programId);
        orderCreateDto.setProgramItemPicture(programVo.getItemPicture());
        orderCreateDto.setUserId(userId);
        orderCreateDto.setProgramTitle(programVo.getTitle());
        orderCreateDto.setProgramPlace(programVo.getPlace());
        orderCreateDto.setProgramShowTime(programVo.getShowTime());
        orderCreateDto.setProgramPermitChooseSeat(programVo.getPermitChooseSeat());
        BigDecimal databaseOrderPrice =
                purchaseSeatList.stream().map(PurchaseSeat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        orderCreateDto.setOrderPrice(databaseOrderPrice);
        orderCreateDto.setCreateOrderTime(DateUtils.now());
        orderCreateDto.setOrderVersion(orderVersion);
        
        List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = new ArrayList<>();
        for (PurchaseSeat purchaseSeat : purchaseSeatList) {
            OrderTicketUserCreateDto orderTicketUserCreateDto = new OrderTicketUserCreateDto();
            orderTicketUserCreateDto.setOrderNumber(orderCreateDto.getOrderNumber());
            orderTicketUserCreateDto.setProgramId(programId);
            orderTicketUserCreateDto.setUserId(userId);
            orderTicketUserCreateDto.setTicketUserId(purchaseSeat.getTicketUserId());
            orderTicketUserCreateDto.setSeatId(purchaseSeat.getId());
            orderTicketUserCreateDto.setSeatInfo(purchaseSeat.getRowCode()+"排"+purchaseSeat.getColCode()+"列");
            orderTicketUserCreateDto.setTicketCategoryId(purchaseSeat.getTicketCategoryId());
            orderTicketUserCreateDto.setOrderPrice(purchaseSeat.getPrice());
            orderTicketUserCreateDto.setCreateOrderTime(DateUtils.now());
            orderTicketUserCreateDtoList.add(orderTicketUserCreateDto);
        }
        orderCreateDto.setOrderTicketUserCreateDtoList(orderTicketUserCreateDtoList);
        return orderCreateDto;
    }
    
    private String createOrderByRpc(OrderCreateDto orderCreateDto,List<SeatVo> purchaseSeatList){
        ApiResponse<String> createOrderResponse = orderClient.create(orderCreateDto);
        if (!Objects.equals(createOrderResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            log.error("创建订单失败 需人工处理 orderCreateDto : {}",JSON.toJSONString(orderCreateDto));
            updateProgramCacheDataResolution(orderCreateDto.getProgramId(),purchaseSeatList,OrderStatus.CANCEL);
            throw new DaMaiFrameException(createOrderResponse);
        }
        return createOrderResponse.getData();
    }
    
    private String createOrderByMq(OrderCreateMq orderCreateMq,List<PurchaseSeat> purchaseSeatList){
        CreateOrderMqDomain createOrderMqDomain = new CreateOrderMqDomain();
        CountDownLatch latch = new CountDownLatch(1);
        createOrderSend.sendMessage(JSON.toJSONString(orderCreateMq),sendResult -> {
            createOrderMqDomain.orderNumber = String.valueOf(orderCreateMq.getOrderNumber());
            assert sendResult != null;
            log.info("创建订单kafka发送消息成功 topic : {}",sendResult.getRecordMetadata().topic());
            latch.countDown();
        },ex -> {
            log.error("创建订单kafka发送消息失败 error",ex);
            List<SeatVo> purchaseSeatVoList = purchaseSeatList.stream().map(purchaseSeat -> {
                SeatVo seatVo = new SeatVo();
                BeanUtils.copyProperties(purchaseSeat,seatVo);
                return seatVo;
            }).collect(Collectors.toList());
            updateProgramCacheDataResolution(orderCreateMq.getProgramId(),purchaseSeatVoList,OrderStatus.CANCEL);
            createOrderMqDomain.daMaiFrameException = new DaMaiFrameException(ex);
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("createOrderByMq InterruptedException",e);
            throw new DaMaiFrameException(e);
        }
        if (Objects.nonNull(createOrderMqDomain.daMaiFrameException)) {
            throw createOrderMqDomain.daMaiFrameException;
        }
        return createOrderMqDomain.orderNumber;
    }
    
    private void updateProgramCacheDataResolution(Long programId,List<SeatVo> seatVoList,OrderStatus orderStatus){
        if (!(Objects.equals(orderStatus.getCode(), OrderStatus.NO_PAY.getCode()) ||
                Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode()))) {
            throw new DaMaiFrameException(BaseCode.OPERATE_ORDER_STATUS_NOT_PERMIT);
        }
        List<String> keys = new ArrayList<>();
        keys.add("#");
        
        String[] data = new String[3];
        Map<Long, Long> ticketCategoryCountMap =
                seatVoList.stream().collect(Collectors.groupingBy(SeatVo::getTicketCategoryId, Collectors.counting()));
        JSONArray jsonArray = new JSONArray();
        ticketCategoryCountMap.forEach((k,v) -> {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("programTicketRemainNumberHashKey",RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, k).getRelKey());
            jsonObject.put("ticketCategoryId",String.valueOf(k));
            if (Objects.equals(orderStatus.getCode(), OrderStatus.NO_PAY.getCode())) {
                jsonObject.put("count","-" + v);
            } else if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
                jsonObject.put("count",v);
            }
            jsonArray.add(jsonObject);
        });
        Map<Long, List<SeatVo>> seatVoMap = 
                seatVoList.stream().collect(Collectors.groupingBy(SeatVo::getTicketCategoryId));
        JSONArray delSeatIdjsonArray = new JSONArray();
        JSONArray addSeatDatajsonArray = new JSONArray();
        seatVoMap.forEach((k,v) -> {
            JSONObject delSeatIdjsonObject = new JSONObject();
            JSONObject seatDatajsonObject = new JSONObject();
            String seatHashKeyDel = "";
            String seatHashKeyAdd = "";
            if (Objects.equals(orderStatus.getCode(), OrderStatus.NO_PAY.getCode())) {
                seatHashKeyDel = (RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, k).getRelKey());
                seatHashKeyAdd = (RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, k).getRelKey());
                for (SeatVo seatVo : v) {
                    seatVo.setSellStatus(SellStatus.LOCK.getCode());
                }
            } else if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
                seatHashKeyDel = (RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, k).getRelKey());
                seatHashKeyAdd = (RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, k).getRelKey());
                for (SeatVo seatVo : v) {
                    seatVo.setSellStatus(SellStatus.NO_SOLD.getCode());
                }
            }
            delSeatIdjsonObject.put("seatHashKeyDel",seatHashKeyDel);
            delSeatIdjsonObject.put("seatIdList",v.stream().map(SeatVo::getId).map(String::valueOf).collect(Collectors.toList()));
            delSeatIdjsonArray.add(delSeatIdjsonObject);
            seatDatajsonObject.put("seatHashKeyAdd",seatHashKeyAdd);
            List<String> seatDataList = new ArrayList<>();
            for (SeatVo seatVo : v) {
                seatDataList.add(String.valueOf(seatVo.getId()));
                seatDataList.add(JSON.toJSONString(seatVo));
            }
            seatDatajsonObject.put("seatDataList",seatDataList);
            addSeatDatajsonArray.add(seatDatajsonObject);
        });
        
        data[0] = JSON.toJSONString(jsonArray);
        data[1] = JSON.toJSONString(delSeatIdjsonArray);
        data[2] = JSON.toJSONString(addSeatDatajsonArray);
        programCacheResolutionOperate.programCacheOperate(keys,data);
    }
}
