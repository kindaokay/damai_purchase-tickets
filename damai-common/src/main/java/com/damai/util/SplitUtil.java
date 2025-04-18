package com.damai.util;

import static com.damai.constant.Constant.GLIDE_LINE;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 分割工具
 * @author: 阿星不是程序员
 **/
public class SplitUtil {
    
    public static String[] toSplit(String str) {
        return str.split(GLIDE_LINE);
    }
}
