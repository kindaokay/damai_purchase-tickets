package com.damai.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.client.PayClient;
import com.damai.client.ProgramClient;
import com.damai.client.UserClient;
import com.damai.common.ApiResponse;
import com.damai.core.RedisKeyManage;
import com.damai.domain.OrderCreateDomain;
import com.damai.domain.OrderCreateMq;
import com.damai.domain.ProgramRecord;
import com.damai.dto.*;
import com.damai.entity.Order;
import com.damai.entity.OrderTicketUser;
import com.damai.entity.OrderTicketUserAggregate;
import com.damai.entity.OrderTicketUserRecord;
import com.damai.enums.*;
import com.damai.exception.DaMaiFrameException;
import com.damai.mapper.OrderMapper;
import com.damai.mapper.OrderTicketUserMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.damai.request.CustomizeRequestWrapper;
import com.damai.service.delaysend.DelayOperateProgramDataSend;
import com.damai.service.properties.OrderProperties;
import com.damai.servicelock.LockType;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.util.DateUtils;
import com.damai.util.ServiceLockTool;
import com.damai.util.StringUtil;
import com.damai.vo.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.damai.constant.Constant.ALIPAY_NOTIFY_SUCCESS_RESULT;
import static com.damai.constant.Constant.GLIDE_LINE;
import static com.damai.core.DistributedLockConstants.*;
import static com.damai.core.RepeatExecuteLimitConstants.CANCEL_PROGRAM_ORDER;
import static com.damai.core.RepeatExecuteLimitConstants.CREATE_PROGRAM_ORDER_MQ;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 订单 service
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class OrderService extends ServiceImpl<OrderMapper, Order> {

    private final Pattern PATTERN = Pattern.compile("_(\\d+)$");
    
    @Autowired
    private UidGenerator uidGenerator;
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private OrderTicketUserMapper orderTicketUserMapper;
    
    @Autowired
    private OrderTicketUserService orderTicketUserService;
    
    @Autowired
    private OrderTicketUserRecordService orderTicketUserRecordService;
    
    @Autowired
    private OrderProgramCacheResolutionOperate orderProgramCacheResolutionOperate;
    
    @Autowired
    private RedisCache redisCache;
    
    @Autowired
    private PayClient payClient;
    
    @Autowired
    private UserClient userClient;
    
    @Autowired
    private OrderProperties orderProperties;
    
    @Lazy
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private DelayOperateProgramDataSend delayOperateProgramDataSend;
    
    @Autowired
    private ServiceLockTool serviceLockTool;
    
    @Autowired
    private ProgramClient programClient;

    @Transactional(rollbackFor = Exception.class)
    public String create(OrderCreateDto orderCreateDto) {
        OrderCreateDomain orderCreateDomain = new OrderCreateDomain();
        BeanUtils.copyProperties(orderCreateDto, orderCreateDomain);
        return doCreate(orderCreateDomain);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public String createByMq(OrderCreateMq orderCreateMq) {
        OrderCreateDomain orderCreateDomain = new OrderCreateDomain();
        BeanUtils.copyProperties(orderCreateMq, orderCreateDomain);
        return doCreate(orderCreateDomain);
    }

    @Transactional(rollbackFor = Exception.class)
    public String doCreate(OrderCreateDomain orderCreateDomain) {
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderCreateDomain.getOrderNumber());
        //如果订单存在了，那么直接拒绝
        Order oldOrder = orderMapper.selectOne(orderLambdaQueryWrapper);
        if (Objects.nonNull(oldOrder)) {
            throw new DaMaiFrameException(BaseCode.ORDER_EXIST);
        }
        Order order = new Order();
        BeanUtil.copyProperties(orderCreateDomain,order);
        order.setId(uidGenerator.getUid());
        order.setDistributionMode("电子票");
        order.setTakeTicketMode("请使用购票人身份证直接入场");
        //购票人订单对象
        List<OrderTicketUser> orderTicketUserList = new ArrayList<>();
        //购票人订单记录对象
        List<OrderTicketUserRecord> orderTicketUserRecordList = new ArrayList<>();
        for (OrderTicketUserCreateDto orderTicketUserCreateDto : orderCreateDomain.getOrderTicketUserCreateDtoList()) {
            OrderTicketUser orderTicketUser = new OrderTicketUser();
            BeanUtil.copyProperties(orderTicketUserCreateDto,orderTicketUser);
            orderTicketUser.setId(uidGenerator.getUid());
            orderTicketUserList.add(orderTicketUser);

            OrderTicketUserRecord orderTicketUserRecord = new OrderTicketUserRecord();
            BeanUtil.copyProperties(orderTicketUserCreateDto,orderTicketUserRecord);
            orderTicketUserRecord.setTicketUserOrderId(orderTicketUser.getId());
            orderTicketUserRecord.setRecordTypeCode(RecordType.REDUCE.getCode());
            orderTicketUserRecord.setRecordTypeValue(RecordType.REDUCE.getValue());
            orderTicketUserRecordList.add(orderTicketUserRecord);
        }
        //插入主订单
        orderMapper.insert(order);
        //插入购票人订单
        orderTicketUserService.saveBatch(orderTicketUserList);
        //插入购票人订单记录
        orderTicketUserRecordService.saveBatch(orderTicketUserRecordList);
        //用户下此节目的订单数量加1操作
        redisCache.incrBy(RedisKeyBuild.createRedisKey(
                RedisKeyManage.ACCOUNT_ORDER_COUNT,orderCreateDomain.getUserId(),
                orderCreateDomain.getProgramId()),orderCreateDomain.getOrderTicketUserCreateDtoList().size());
        return String.valueOf(order.getOrderNumber());
    }
    
    /**
     * 订单取消，以订单编号加锁
     * */
    @RepeatExecuteLimit(name = CANCEL_PROGRAM_ORDER,keys = {"#orderCancelDto.orderNumber"})
    @ServiceLock(name = ORDER_CANCEL_LOCK,keys = {"#orderCancelDto.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(OrderCancelDto orderCancelDto){
        updateOrderRelatedData(orderCancelDto.getOrderNumber(),OrderStatus.CANCEL);
        return true;
    }
    
    public String pay(OrderPayDto orderPayDto) {
        Long orderNumber = orderPayDto.getOrderNumber();
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderNumber);
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        if (Objects.isNull(order)) {
            throw new DaMaiFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
            throw new DaMaiFrameException(BaseCode.ORDER_CANCEL);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.PAY.getCode())) {
            throw new DaMaiFrameException(BaseCode.ORDER_PAY);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.REFUND.getCode())) {
            throw new DaMaiFrameException(BaseCode.ORDER_REFUND);
        }
        if (orderPayDto.getPrice().compareTo(order.getOrderPrice()) != 0) {
            throw new DaMaiFrameException(BaseCode.PAY_PRICE_NOT_EQUAL_ORDER_PRICE);
        }
        PayDto payDto = getPayDto(orderPayDto, orderNumber);
        ApiResponse<String> payResponse = payClient.commonPay(payDto);
        if (!Objects.equals(payResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new DaMaiFrameException(payResponse);
        }
        return payResponse.getData();
    }
    
    private PayDto getPayDto(OrderPayDto orderPayDto, Long orderNumber) {
        PayDto payDto = new PayDto();
        payDto.setOrderNumber(String.valueOf(orderNumber));
        payDto.setPayBillType(orderPayDto.getPayBillType());
        payDto.setSubject(orderPayDto.getSubject());
        payDto.setChannel(orderPayDto.getChannel());
        payDto.setPlatform(orderPayDto.getPlatform());
        payDto.setPrice(orderPayDto.getPrice());
        payDto.setNotifyUrl(orderProperties.getOrderPayNotifyUrl());
        payDto.setReturnUrl(orderProperties.getOrderPayReturnUrl());
        return payDto;
    }
    
    /**
     * 支付后订单检查，以订单编号加锁，防止多次更新
     * */
    @ServiceLock(name = ORDER_PAY_CHECK,keys = {"#orderPayCheckDto.orderNumber"})
    public OrderPayCheckVo payCheck(OrderPayCheckDto orderPayCheckDto){
        OrderPayCheckVo orderPayCheckVo = new OrderPayCheckVo();
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderPayCheckDto.getOrderNumber());
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        if (Objects.isNull(order)) {
            throw new DaMaiFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        BeanUtil.copyProperties(order,orderPayCheckVo);
        if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
            RefundDto refundDto = new RefundDto();
            refundDto.setOrderNumber(String.valueOf(order.getOrderNumber()));
            refundDto.setAmount(order.getOrderPrice());
            refundDto.setChannel("alipay");
            refundDto.setReason("延迟订单关闭");
            ApiResponse<String> response = payClient.refund(refundDto);
            if (response.getCode().equals(BaseCode.SUCCESS.getCode())) {
                Order updateOrder = new Order();
                updateOrder.setEditTime(DateUtils.now());
                updateOrder.setOrderStatus(OrderStatus.REFUND.getCode());
                orderMapper.update(updateOrder,Wrappers.lambdaUpdate(Order.class).eq(Order::getOrderNumber, order.getOrderNumber()));
            }else {
                log.error("pay服务退款失败 dto : {} response : {}",JSON.toJSONString(refundDto),JSON.toJSONString(response));
            }
            orderPayCheckVo.setOrderStatus(OrderStatus.REFUND.getCode());
            orderPayCheckVo.setCancelOrderTime(DateUtils.now());
            return orderPayCheckVo;
        }
        
        TradeCheckDto tradeCheckDto = new TradeCheckDto();
        tradeCheckDto.setOutTradeNo(String.valueOf(orderPayCheckDto.getOrderNumber()));
        tradeCheckDto.setChannel(Optional.ofNullable(PayChannel.getRc(orderPayCheckDto.getPayChannelType()))
                .map(PayChannel::getValue).orElseThrow(() -> new DaMaiFrameException(BaseCode.PAY_CHANNEL_NOT_EXIST)));
        ApiResponse<TradeCheckVo> tradeCheckVoApiResponse = payClient.tradeCheck(tradeCheckDto);
        if (!Objects.equals(tradeCheckVoApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new DaMaiFrameException(tradeCheckVoApiResponse);
        }
        TradeCheckVo tradeCheckVo = Optional.ofNullable(tradeCheckVoApiResponse.getData())
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.PAY_BILL_NOT_EXIST));
        if (tradeCheckVo.isSuccess()) {
            Integer payBillStatus = tradeCheckVo.getPayBillStatus();
            Integer orderStatus = order.getOrderStatus();
            if (!Objects.equals(orderStatus, payBillStatus)) {
                orderPayCheckVo.setOrderStatus(payBillStatus);
                try {
                    if (Objects.equals(payBillStatus, PayBillStatus.PAY.getCode())) {
                        orderPayCheckVo.setPayOrderTime(DateUtils.now());
                        orderService.updateOrderRelatedData(order.getOrderNumber(),OrderStatus.PAY);
                    }else if (Objects.equals(payBillStatus, PayBillStatus.CANCEL.getCode())) {
                        orderPayCheckVo.setCancelOrderTime(DateUtils.now());
                        orderService.updateOrderRelatedData(order.getOrderNumber(),OrderStatus.CANCEL);
                    }
                }catch (Exception e) {
                    log.warn("updateOrderRelatedData warn message",e);
                }
            }
        }else {
            throw new DaMaiFrameException(BaseCode.PAY_TRADE_CHECK_ERROR);
        }
        return orderPayCheckVo;
    }
    
    
    public String alipayNotify(HttpServletRequest request){
        
        Map<String, String> params = new HashMap<>(256);
        if (request instanceof final CustomizeRequestWrapper customizeRequestWrapper) {
            String requestBody = customizeRequestWrapper.getRequestBody();
            params = StringUtil.convertQueryStringToMap(requestBody);
        }
        log.info("收到支付宝回调通知 params : {}",JSON.toJSONString(params));
        String outTradeNo = params.get("out_trade_no");
        if (StringUtil.isEmpty(outTradeNo)) {
            return "failure";
        }
        
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, ORDER_PAY_NOTIFY_CHECK,
                new String[]{outTradeNo});
        lock.lock();
        try {
            Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, Long.parseLong(outTradeNo)));
            if (Objects.isNull(order)) {
                throw new DaMaiFrameException(BaseCode.ORDER_NOT_EXIST);
            }
            if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
                RefundDto refundDto = new RefundDto();
                refundDto.setOrderNumber(outTradeNo);
                refundDto.setAmount(order.getOrderPrice());
                refundDto.setChannel("alipay");
                refundDto.setReason("延迟订单关闭");
                ApiResponse<String> response = payClient.refund(refundDto);
                if (response.getCode().equals(BaseCode.SUCCESS.getCode())) {
                    Order updateOrder = new Order();
                    updateOrder.setEditTime(DateUtils.now());
                    updateOrder.setOrderStatus(OrderStatus.REFUND.getCode());
                    orderMapper.update(updateOrder,Wrappers.lambdaUpdate(Order.class).eq(Order::getOrderNumber, outTradeNo));
                }else {
                    log.error("pay服务退款失败 dto : {} response : {}",JSON.toJSONString(refundDto),JSON.toJSONString(response));
                }
                return ALIPAY_NOTIFY_SUCCESS_RESULT;
            }
          
            
            NotifyDto notifyDto = new NotifyDto();
            notifyDto.setChannel(PayChannel.ALIPAY.getValue());
            notifyDto.setParams(params);
            ApiResponse<NotifyVo> notifyResponse = payClient.notify(notifyDto);
            if (!Objects.equals(notifyResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                throw new DaMaiFrameException(notifyResponse);
            }
            if (ALIPAY_NOTIFY_SUCCESS_RESULT.equals(notifyResponse.getData().getPayResult())) {
                try {
                    orderService.updateOrderRelatedData(Long.parseLong(notifyResponse.getData().getOutTradeNo())
                            ,OrderStatus.PAY);
                }catch (Exception e) {
                    log.warn("updateOrderRelatedData warn message",e);
                }
            }
            return notifyResponse.getData().getPayResult();
        }finally {
            lock.unlock();
        }
        
    }
    
    /**
     * 更新订单和购票人订单状态以及操作缓存数据
     * */
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderRelatedData(Long orderNumber,OrderStatus orderStatus){
        //如果不是取消或者支付操作，则直接抛出异常提示
        if (!(Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode()) ||
                Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode()))) {
            throw new DaMaiFrameException(BaseCode.OPERATE_ORDER_STATUS_NOT_PERMIT);
        }
        //查询订单
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderNumber);
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        //检查订单的状态 已取消、已支付、已退单的状态不再执行
        checkOrderStatus(order);
        //查询该订单下的购票人订单列表
        LambdaQueryWrapper<OrderTicketUser> orderTicketUserLambdaQueryWrapper =
                Wrappers.lambdaQuery(OrderTicketUser.class).eq(OrderTicketUser::getOrderNumber, order.getOrderNumber());
        List<OrderTicketUser> orderTicketUserList = orderTicketUserMapper.selectList(orderTicketUserLambdaQueryWrapper);
        if (CollectionUtil.isEmpty(orderTicketUserList)) {
            throw new DaMaiFrameException(BaseCode.TICKET_USER_ORDER_NOT_EXIST);
        }
        //将订单更新为取消或者支付状态
        Order updateOrder = new Order();
        updateOrder.setId(order.getId());
        updateOrder.setOrderStatus(orderStatus.getCode());
        //将购票人订单更新为取消或者支付状态
        OrderTicketUser updateOrderTicketUser = new OrderTicketUser();
        updateOrderTicketUser.setOrderStatus(orderStatus.getCode());

        //如果是支付的话，那么记录的类型就是改变状态
        //记录类型code
        Integer recordTypeCode = RecordType.CHANGE_STATUS.getCode();
        //记录类型值
        String recordTypeValue = RecordType.CHANGE_STATUS.getValue();
        //支付状态的操作
        if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode())) {
            updateOrder.setPayOrderTime(DateUtils.now());
            updateOrderTicketUser.setPayOrderTime(DateUtils.now());
        } else if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
            //取消状态的操作
            updateOrder.setCancelOrderTime(DateUtils.now());
            updateOrderTicketUser.setCancelOrderTime(DateUtils.now());
            //如果是取消的话，那么记录的类型就是增加余票
            recordTypeCode = RecordType.INCREASE.getCode();
            recordTypeValue = RecordType.INCREASE.getValue();
        }
        //更新订单
        LambdaUpdateWrapper<Order> orderLambdaUpdateWrapper =
                Wrappers.lambdaUpdate(Order.class).eq(Order::getOrderNumber, order.getOrderNumber());
        int updateOrderResult = orderMapper.update(updateOrder,orderLambdaUpdateWrapper);
        //更新购票人订单
        LambdaUpdateWrapper<OrderTicketUser> orderTicketUserLambdaUpdateWrapper =
                Wrappers.lambdaUpdate(OrderTicketUser.class).eq(OrderTicketUser::getOrderNumber, order.getOrderNumber());
        int updateTicketUserOrderResult =
                orderTicketUserMapper.update(updateOrderTicketUser,orderTicketUserLambdaUpdateWrapper);
        if (updateOrderResult <= 0 || updateTicketUserOrderResult <= 0) {
            throw new DaMaiFrameException(BaseCode.ORDER_CANAL_ERROR);
        }
        List<OrderTicketUserRecord> orderTicketUserRecordList = new ArrayList<>();
        for (OrderTicketUser orderTicketUser : orderTicketUserList) {
            //购票人订单记录
            OrderTicketUserRecord orderTicketUserRecord = new OrderTicketUserRecord();
            BeanUtils.copyProperties(orderTicketUser,orderTicketUserRecord);
            orderTicketUserRecord.setId(uidGenerator.getUid());
            orderTicketUserRecord.setTicketUserOrderId(orderTicketUser.getId());
            orderTicketUserRecord.setRecordTypeCode(recordTypeCode);
            orderTicketUserRecord.setRecordTypeValue(recordTypeValue);
            orderTicketUserRecordList.add(orderTicketUserRecord);
        }
        //添加购票人订单记录流水
        orderTicketUserRecordService.saveBatch(orderTicketUserRecordList);
        
        //如果是取消操作，那么把用户下该节目的订单数量要-1
        if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
            redisCache.incrBy(RedisKeyBuild.createRedisKey(
                    RedisKeyManage.ACCOUNT_ORDER_COUNT,order.getUserId(),order.getProgramId()),-updateTicketUserOrderResult);
        }
        Long programId = order.getProgramId();
        //将购票人订单集合转换成map结构，key：票档id value：购票人订单
        Map<Long, List<OrderTicketUser>> orderTicketUserSeatList = 
                orderTicketUserList.stream().collect(Collectors.groupingBy(OrderTicketUser::getTicketCategoryId));
        Map<Long,List<Long>> seatMap = new HashMap<>(orderTicketUserSeatList.size());
        //根据orderTicketUserSeatList得到seatMap
        //seatMap结构 key：票档id  value：座位id集合
        orderTicketUserSeatList.forEach((k,v) -> {
            seatMap.put(k,v.stream().map(OrderTicketUser::getSeatId).collect(Collectors.toList()));
        });
        //更新缓存相关数据
        updateProgramRelatedDataResolution(programId,seatMap,orderStatus,order.getIdentifierId(),order.getUserId());
    }
    
    public void checkOrderStatus(Order order){
        if (Objects.isNull(order)) {
            throw new DaMaiFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCEL.getCode())) {
            throw new DaMaiFrameException(BaseCode.ORDER_CANCEL);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.PAY.getCode())) {
            throw new DaMaiFrameException(BaseCode.ORDER_PAY);
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.REFUND.getCode())) {
            throw new DaMaiFrameException(BaseCode.ORDER_REFUND);
        }
    }
    
    public void updateProgramRelatedDataResolution(Long programId,Map<Long,List<Long>> seatMap,
                                                   OrderStatus orderStatus,Long identifierId,
                                                   Long userId){
        Map<Long, List<SeatVo>> seatVoMap = new HashMap<>(seatMap.size());
        //从redis中查询锁定中的座位
        seatMap.forEach((k,v) -> {
            seatVoMap.put(k,redisCache.multiGetForHash(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, k),
                    v.stream().map(String::valueOf).collect(Collectors.toList()), SeatVo.class));
            
        });
        if (CollectionUtil.isEmpty(seatVoMap)) {
            throw new DaMaiFrameException(BaseCode.LOCK_SEAT_LIST_EMPTY);
        }
        //票档相关数据
        JSONArray jsonArray = new JSONArray();
        //要添加的座位相关数据
        JSONArray addSeatDatajsonArray = new JSONArray();
        List<TicketCategoryCountDto> ticketCategoryCountDtoList = new ArrayList<>(seatVoMap.size());
        //锁定的座位相关数据
        JSONArray unLockSeatIdjsonArray = new JSONArray();
        //锁定的座位id集合(用于发送给节目服务使用)
        List<Long> unLockSeatIdList = new ArrayList<>();
        seatVoMap.forEach((k,v) -> {
            JSONObject unLockSeatIdjsonObject = new JSONObject();
            //锁定的座位hash的key
            unLockSeatIdjsonObject.put("programSeatLockHashKey", RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, k).getRelKey());
            //扣除锁定的座位数据
            unLockSeatIdjsonObject.put("unLockSeatIdList",v.stream()
                    .map(SeatVo::getId).map(String::valueOf).collect(Collectors.toList()));
            unLockSeatIdjsonArray.add(unLockSeatIdjsonObject);
            
            JSONObject seatDatajsonObject = new JSONObject();
            //要添加的座位hash的key
            String seatHashKeyAdd = "";
            //如果是订单取消操作
            if (Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
                //要添加的座位hash的key就是未售卖座位
                seatHashKeyAdd = RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, k).getRelKey();
                for (SeatVo seatVo : v) {
                    //座位状态要改成未售卖
                    seatVo.setSellStatus(SellStatus.NO_SOLD.getCode());
                }
                //如果是订单支付操作
            }else if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode())) {
                //要添加的座位hash的key就是已售卖座位
                seatHashKeyAdd = RedisKeyBuild.createRedisKey(
                        RedisKeyManage.PROGRAM_SEAT_SOLD_RESOLUTION_HASH, programId, k).getRelKey();
                for (SeatVo seatVo : v) {
                    //座位状态要改成已售卖
                    seatVo.setSellStatus(SellStatus.SOLD.getCode());
                }
            }
            seatDatajsonObject.put("seatHashKeyAdd",seatHashKeyAdd);
            List<String> seatDataList = new ArrayList<>();
            for (SeatVo seatVo : v) {
                seatDataList.add(String.valueOf(seatVo.getId()));
                seatDataList.add(JSON.toJSONString(seatVo));
            }
            //如果是订单取消的操作，那么添加到未售卖的座位数据
            //如果是订单支付的操作，那么添加到已售卖的座位数据
            seatDatajsonObject.put("seatDataList",seatDataList);
            addSeatDatajsonArray.add(seatDatajsonObject);
            //票档相关数据（只在订单取消操作有用）
            JSONObject jsonObject = new JSONObject();
            //票档的hash的key
            jsonObject.put("programTicketRemainNumberHashKey",RedisKeyBuild.createRedisKey(
                    RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, k).getRelKey());
            //票档id
            jsonObject.put("ticketCategoryId",String.valueOf(k));
            //票档恢复的余票数量
            jsonObject.put("count",v.size());
            jsonArray.add(jsonObject);
            
            //组装发送给节目服务的数据
            TicketCategoryCountDto ticketCategoryCountDto = new TicketCategoryCountDto();
            ticketCategoryCountDto.setTicketCategoryId(k);
            ticketCategoryCountDto.setCount((long) v.size());
            ticketCategoryCountDtoList.add(ticketCategoryCountDto);
            
            unLockSeatIdList.addAll(v.stream().map(SeatVo::getId).toList());
        });
        List<String> keys = new ArrayList<>();
        //操作类型
        keys.add(String.valueOf(orderStatus.getCode()));
        //节目id
        keys.add(String.valueOf(programId));
        //记录的key(占位符形式)
        keys.add(RedisKeyBuild.getRedisKey(RedisKeyManage.PROGRAM_RECORD));
        
        String recordTye = Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode()) ?
                RecordType.INCREASE.getValue() : RecordType.CHANGE_STATUS.getValue();
        //把记录的标识id放进去
        keys.add(recordTye + GLIDE_LINE + identifierId + GLIDE_LINE + userId);
        //记录的类型
        keys.add(recordTye);
        Object[] data = new String[3];
        //扣除锁定的座位数据
        data[0] = JSON.toJSONString(unLockSeatIdjsonArray);
        //如果是订单取消的操作，那么添加到未售卖的座位数据
        //如果是订单支付的操作，那么添加到已售卖的座位数据
        data[1] = JSON.toJSONString(addSeatDatajsonArray);
        //恢复库存数据
        data[2] = JSON.toJSONString(jsonArray);
        //执行lua脚本
        orderProgramCacheResolutionOperate.programCacheReverseOperate(keys,data);
        //更新节目服务的相关数据（通过延迟队列）
        if (Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode()) || 
                Objects.equals(orderStatus.getCode(), OrderStatus.CANCEL.getCode())) {
            ProgramOperateDataDto programOperateDataDto = new ProgramOperateDataDto();
            programOperateDataDto.setProgramId(programId);
            programOperateDataDto.setSeatIdList(unLockSeatIdList);
            programOperateDataDto.setTicketCategoryCountDtoList(ticketCategoryCountDtoList);
            //如果是支付操作，那么座位状态就改为已售卖，如果是取消操作，那么座位状态就改为未售卖
            programOperateDataDto.setSellStatus(Objects.equals(orderStatus.getCode(), OrderStatus.PAY.getCode()) 
                    ? SellStatus.SOLD.getCode() : SellStatus.NO_SOLD.getCode());
            delayOperateProgramDataSend.sendMessage(JSON.toJSONString(programOperateDataDto));
        }
    }
    
    public List<OrderListVo> selectList(OrderListDto orderListDto) {
        List<OrderListVo> orderListVos = new ArrayList<>();
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper = 
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getUserId, orderListDto.getUserId())
                        .orderByDesc(Order::getCreateOrderTime);
        List<Order> orderList = orderMapper.selectList(orderLambdaQueryWrapper);
        if (CollectionUtil.isEmpty(orderList)) {
            return orderListVos;
        }
        orderListVos = BeanUtil.copyToList(orderList, OrderListVo.class);
        List<OrderTicketUserAggregate> orderTicketUserAggregateList = 
                orderTicketUserMapper.selectOrderTicketUserAggregate(orderList.stream().map(Order::getOrderNumber).
                        collect(Collectors.toList()));
        Map<Long, Integer> orderTicketUserAggregateMap = orderTicketUserAggregateList.stream()
                .collect(Collectors.toMap(OrderTicketUserAggregate::getOrderNumber, 
                        OrderTicketUserAggregate::getOrderTicketUserCount, (v1, v2) -> v2));
        for (OrderListVo orderListVo : orderListVos) {
            orderListVo.setTicketCount(orderTicketUserAggregateMap.get(orderListVo.getOrderNumber()));
        }
        return orderListVos;
    }
    
    public OrderGetVo get(OrderGetDto orderGetDto) {
        LambdaQueryWrapper<Order> orderLambdaQueryWrapper =
                Wrappers.lambdaQuery(Order.class).eq(Order::getOrderNumber, orderGetDto.getOrderNumber());
        Order order = orderMapper.selectOne(orderLambdaQueryWrapper);
        if (Objects.isNull(order)) {
            throw new DaMaiFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        LambdaQueryWrapper<OrderTicketUser> orderTicketUserLambdaQueryWrapper = 
                Wrappers.lambdaQuery(OrderTicketUser.class).eq(OrderTicketUser::getOrderNumber, order.getOrderNumber());
        List<OrderTicketUser> orderTicketUserList = orderTicketUserMapper.selectList(orderTicketUserLambdaQueryWrapper);
        if (CollectionUtil.isEmpty(orderTicketUserList)) {
            throw new DaMaiFrameException(BaseCode.TICKET_USER_ORDER_NOT_EXIST);   
        }
        
        OrderGetVo orderGetVo = new OrderGetVo();
        BeanUtil.copyProperties(order,orderGetVo);
        
        List<OrderTicketInfoVo> orderTicketInfoVoList = new ArrayList<>();
        Map<BigDecimal, List<OrderTicketUser>> orderTicketUserMap = 
                orderTicketUserList.stream().collect(Collectors.groupingBy(OrderTicketUser::getOrderPrice));
        orderTicketUserMap.forEach((k,v) -> {
            OrderTicketInfoVo orderTicketInfoVo = new OrderTicketInfoVo();
            String seatInfo = "暂无座位信息";
            if (order.getProgramPermitChooseSeat().equals(BusinessStatus.YES.getCode())) {
                seatInfo = v.stream().map(OrderTicketUser::getSeatInfo).collect(Collectors.joining(","));
            }
            orderTicketInfoVo.setSeatInfo(seatInfo);
            orderTicketInfoVo.setPrice(v.get(0).getOrderPrice());
            orderTicketInfoVo.setQuantity(v.size());
            orderTicketInfoVo.setRelPrice(v.stream().map(OrderTicketUser::getOrderPrice)
                    .reduce(BigDecimal.ZERO,BigDecimal::add));
            orderTicketInfoVoList.add(orderTicketInfoVo);
        });
        
        orderGetVo.setOrderTicketInfoVoList(orderTicketInfoVoList);
        
        UserGetAndTicketUserListDto userGetAndTicketUserListDto = new UserGetAndTicketUserListDto();
        userGetAndTicketUserListDto.setUserId(order.getUserId());
        ApiResponse<UserGetAndTicketUserListVo> userGetAndTicketUserApiResponse = 
                userClient.getUserAndTicketUserList(userGetAndTicketUserListDto);
        
        if (!Objects.equals(userGetAndTicketUserApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new DaMaiFrameException(userGetAndTicketUserApiResponse);
            
        }
        UserGetAndTicketUserListVo userAndTicketUserListVo =
                Optional.ofNullable(userGetAndTicketUserApiResponse.getData())
                        .orElseThrow(() -> new DaMaiFrameException(BaseCode.RPC_RESULT_DATA_EMPTY));
        if (Objects.isNull(userAndTicketUserListVo.getUserVo())) {
            throw new DaMaiFrameException(BaseCode.USER_EMPTY);
        }
        if (CollectionUtil.isEmpty(userAndTicketUserListVo.getTicketUserVoList())) {
            throw new DaMaiFrameException(BaseCode.TICKET_USER_EMPTY);
        }
        List<TicketUserVo> filterTicketUserVoList = new ArrayList<>();
        Map<Long, TicketUserVo> ticketUserVoMap = userAndTicketUserListVo.getTicketUserVoList()
                .stream().collect(Collectors.toMap(TicketUserVo::getId, ticketUserVo -> ticketUserVo, (v1, v2) -> v2));
        for (OrderTicketUser orderTicketUser : orderTicketUserList) {
            filterTicketUserVoList.add(ticketUserVoMap.get(orderTicketUser.getTicketUserId()));
        }
        UserInfoVo userInfoVo = new UserInfoVo();
        BeanUtil.copyProperties(userAndTicketUserListVo.getUserVo(),userInfoVo);
        UserAndTicketUserInfoVo userAndTicketUserInfoVo = new UserAndTicketUserInfoVo();
        userAndTicketUserInfoVo.setUserInfoVo(userInfoVo);
        userAndTicketUserInfoVo.setTicketUserInfoVoList(BeanUtil.copyToList(filterTicketUserVoList, TicketUserInfoVo.class));
        orderGetVo.setUserAndTicketUserInfoVo(userAndTicketUserInfoVo);
        
        return orderGetVo;
    }
    
    public AccountOrderCountVo accountOrderCount(AccountOrderCountDto accountOrderCountDto) {
        AccountOrderCountVo accountOrderCountVo = new AccountOrderCountVo();
        accountOrderCountVo.setCount(orderMapper.accountOrderCount(accountOrderCountDto.getUserId(),
                accountOrderCountDto.getProgramId()));
        return accountOrderCountVo;
    }
    
    
    @RepeatExecuteLimit(name = CREATE_PROGRAM_ORDER_MQ,keys = {"#orderCreateMq.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public String createMq(OrderCreateMq orderCreateMq){
        List<OrderTicketUserCreateDto> orderTicketUserCreateDtoList = orderCreateMq.getOrderTicketUserCreateDtoList();
        
        //使用 Stream API 按 ticketCategoryId 分组并计数
        Map<Long, Long> countMap = orderTicketUserCreateDtoList.stream()
                .collect(Collectors.groupingBy(OrderTicketUserCreateDto::getTicketCategoryId, Collectors.counting()));
        
        //将统计结果转换为列表，存入 TicketCountDto 对象中
        List<TicketCategoryCountDto> ticketCountList = countMap.entrySet().stream()
                .map(entry -> new TicketCategoryCountDto(entry.getKey(), entry.getValue()))
                .toList();
        //修改座位状态和扣减库存
        ReduceRemainNumberDto reduceRemainNumberDto = new ReduceRemainNumberDto();
        reduceRemainNumberDto.setProgramId(orderCreateMq.getProgramId());
        reduceRemainNumberDto.setSellStatus(SellStatus.LOCK.getCode());
        reduceRemainNumberDto.setSeatIdList(orderTicketUserCreateDtoList.stream().map(OrderTicketUserCreateDto::getSeatId).collect(Collectors.toList()));
        reduceRemainNumberDto.setTicketCategoryCountDtoList(ticketCountList);
        ApiResponse<Boolean> programApiResponse = programClient.operateSeatLockAndTicketCategoryRemainNumber(reduceRemainNumberDto);
        if (!Objects.equals(programApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            throw new DaMaiFrameException(programApiResponse);
        }
        String orderNumber = createByMq(orderCreateMq);
        redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.ORDER_MQ,orderNumber),orderNumber,1, TimeUnit.MINUTES);
        return orderNumber;
    }
    
//    @RepeatExecuteLimit(name = PROGRAM_CACHE_REVERSE_MQ,keys = {"#programId"})
//    public void updateProgramRelatedDataMq(Long programId,Map<Long,List<Long>> seatMap,OrderStatus orderStatus){
//        updateProgramRelatedDataResolution(programId,seatMap,orderStatus);
//    }
    
    public String getCache(OrderGetDto orderGetDto) {
        return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.ORDER_MQ,orderGetDto.getOrderNumber()),String.class);
    }
    
    @RepeatExecuteLimit(name = CANCEL_PROGRAM_ORDER,keys = {"#orderCancelDto.orderNumber"})
    @ServiceLock(name = ORDER_CANCEL_LOCK,keys = {"#orderCancelDto.orderNumber"})
    @Transactional(rollbackFor = Exception.class)
    public boolean initiateCancel(OrderCancelDto orderCancelDto){
        Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class)
                .eq(Order::getOrderNumber, orderCancelDto.getOrderNumber()));
        if (Objects.isNull(order)) {
            throw new DaMaiFrameException(BaseCode.ORDER_NOT_EXIST);
        }
        if (!Objects.equals(order.getOrderStatus(), OrderStatus.NO_PAY.getCode())) {
            throw new DaMaiFrameException(BaseCode.CAN_NOT_CANCEL);
        }
        return cancel(orderCancelDto);
    }
    
    
    public void delOrderAndOrderTicketUser(){
        orderMapper.relDelOrder();
        orderTicketUserMapper.relDelOrderTicketUser();
    }
    
    public void reconciliationTask() {
        Set<String> keys = redisCache.keys(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_RECORD, "*").getRelKey());
        if (CollectionUtil.isEmpty(keys)) {
            return;
        }
        for (String key : keys) {
            Map<String, String> programRecordMap = redisCache.getAllMapForHash(key, String.class);
            if (CollectionUtil.isEmpty(programRecordMap)) {
                continue;
            }
            Matcher matcher = PATTERN.matcher(key);
            if (!matcher.find()) {
                continue;
            }
            Long programId = Long.parseLong(matcher.group(1));
            for (Map.Entry<String, String> entry : programRecordMap.entrySet()) {
                String programRecordKey = entry.getKey();
                String programRecordValue = entry.getValue();
                String[] split = programRecordKey.split(GLIDE_LINE);
                if (split.length != 3) {
                    continue;
                }
                String recordTypeValue = split[0];
                String identifierId = split[1];
                String userId = split[2];
                ProgramRecord programRecord = JSON.parseObject(programRecordValue, ProgramRecord.class);
                Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getReconciliationStatus, ReconciliationStatus.RECONCILIATION_NO.getCode())
                        .eq(Order::getProgramId, programId)
                        .eq(Order::getUserId, Long.parseLong(userId))
                        .eq(Order::getIdentifierId, Long.parseLong(identifierId)));
                if (Objects.isNull(order)) {
                    //TODO
                } else {
                    List<OrderTicketUserRecord> orderTicketUserRecordList =
                            orderTicketUserRecordService.list(Wrappers.lambdaQuery(OrderTicketUserRecord.class)
                                    .eq(OrderTicketUserRecord::getOrderNumber, order.getOrderNumber()));
                    if (CollectionUtil.isEmpty(orderTicketUserRecordList)) {
                        continue;
                    }
                    for (OrderTicketUserRecord orderTicketUserRecord : orderTicketUserRecordList) {
                        System.out.println("orderTicketUserRecord = " + orderTicketUserRecord);
                    }
                }
            }
        }
    }
}
