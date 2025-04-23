package com.damai.service;


import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.dto.ProgramRecordTaskAddDto;
import com.damai.dto.ProgramRecordTaskListDto;
import com.damai.dto.ProgramRecordTaskUpdateDto;
import com.damai.entity.ProgramRecordTask;
import com.damai.mapper.ProgramRecordTaskMapper;
import com.damai.util.DateUtils;
import com.damai.vo.ProgramRecordTaskVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目演出时间 service
 * @author: 阿星不是程序员
 **/
@Service
public class ProgramRecordTaskService extends ServiceImpl<ProgramRecordTaskMapper, ProgramRecordTask> {
    
    @Autowired
    private UidGenerator uidGenerator;
    
    @Autowired
    private ProgramRecordTaskMapper programRecordTaskMapper;
    
    
    public List<ProgramRecordTaskVo> select(ProgramRecordTaskListDto programRecordTaskListDto){
        List<ProgramRecordTask> programRecordTaskList = 
                programRecordTaskMapper.selectList(Wrappers.lambdaQuery(ProgramRecordTask.class)
                        .eq(ProgramRecordTask::getHandleStatus, programRecordTaskListDto.getHandleStatus())
                        .le(ProgramRecordTask::getCreateTime, programRecordTaskListDto.getCreateTime()));
        return programRecordTaskList.stream().map(programRecordTask -> {
            ProgramRecordTaskVo programRecordTaskVo = new ProgramRecordTaskVo();
            BeanUtils.copyProperties(programRecordTask, programRecordTaskVo);
            return programRecordTaskVo;
        }).toList();
    }
    
    @Transactional(rollbackFor = Exception.class)
    public Integer updateByCreateTime(ProgramRecordTaskUpdateDto programRecordTaskUpdateDto){
        ProgramRecordTask updateProgramRecordTask = new ProgramRecordTask();
        updateProgramRecordTask.setHandleStatus(programRecordTaskUpdateDto.getAfterHandleStatus());
        return programRecordTaskMapper.update(updateProgramRecordTask,Wrappers.lambdaUpdate(ProgramRecordTask.class)
                        .eq(ProgramRecordTask::getHandleStatus, programRecordTaskUpdateDto.getBeforeHandleStatus())
                        .in(ProgramRecordTask::getCreateTime, programRecordTaskUpdateDto.getCreateTimeSet()));
        
    }
    
    @Transactional(rollbackFor = Exception.class)
    public Integer add(ProgramRecordTaskAddDto orderTicketUserRecordAddDto){
        ProgramRecordTask programRecordTask = new ProgramRecordTask();
        programRecordTask.setId(uidGenerator.getUid());
        programRecordTask.setProgramId(orderTicketUserRecordAddDto.getProgramId());
        programRecordTask.setCreateTime(DateUtils.now());
        programRecordTask.setEditTime(DateUtils.now());
        return programRecordTaskMapper.insert(programRecordTask);
    }
}
