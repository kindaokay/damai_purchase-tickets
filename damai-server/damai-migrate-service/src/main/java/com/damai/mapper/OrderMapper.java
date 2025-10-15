package com.damai.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.entity.Order;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 订单 mapper
 * @author: 阿星不是程序员
 **/
public interface OrderMapper extends BaseMapper<Order> {
    
    /**
     * 物理删除订单 
     * @param ids 订单 id 列表
     * @return Integer 结果
     * */
    Integer physicalDeleteByIds(@Param("ids") List<Long> ids);
}
