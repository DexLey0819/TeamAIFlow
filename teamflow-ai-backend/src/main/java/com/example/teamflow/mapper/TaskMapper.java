package com.example.teamflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.teamflow.entity.Task;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
