package com.damai.csv;

import cn.hutool.core.io.FileUtil;
import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: jmeter csv文件生成工具类
 * @author: 阿星不是程序员
 **/
public class CsvCreate {
    
    private static final String CSV_FILE_LOCATION = Paths.get("").toAbsolutePath() + "/csv";
    
    private static final String CSV_FILE_NAME = CSV_FILE_LOCATION + "/damai购买节目需要的压测数据.csv";
    
    static {
        if (!FileUtil.exist(CSV_FILE_LOCATION)) {
            FileUtil.mkdir(CSV_FILE_LOCATION);
        }
    }
    
    public static void main(String[] args) {
        createCsvFile();
    }
    
    public static void createCsvFile(){
        List<String[]> csvCompleteData = createCsvCompleteData();
        try (CSVWriter writer = new CSVWriter(new FileWriter(CSV_FILE_NAME, StandardCharsets.UTF_8))) {
            writer.writeAll(csvCompleteData);
        } catch (IOException e) {
            System.out.println("生成失败:"+e.getMessage());
        }
    }
    
    public static List<String[]> createCsvCompleteData() {
        List<String[]> csvData = new ArrayList<>();
        csvData.add(new String[]{"programId", "ticketCategoryId"});
        //节目id
        String programId = "34";
        Map<String, Integer> data = createData();
        for (final Entry<String, Integer> entry : data.entrySet()) {
            //节目票档id
            String ticketCategoryId = entry.getKey();
            //购买数量
            Integer purchaseQuantity = entry.getValue();
            for (int i = 1; i <= purchaseQuantity; i++) {
                csvData.add(new String[]{programId, ticketCategoryId});
            }
        }
        Collections.shuffle(csvData.subList(1, csvData.size()));
        return csvData;
    }
    
    /**
     * 生成数据
     * key: 节目票档id
     * value: 购买数量
     * */
    public static Map<String,Integer> createData(){
        Map<String,Integer> map = new HashMap<>(10);
        map.put("46",400);
        map.put("45",400);
        map.put("44",700);
        map.put("43",700);
        map.put("42",700);
        map.put("41",700);
        map.put("40",400);
        return map;
    }
}
