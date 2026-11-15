package com.cloudmirror.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmirror.entity.Project;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}
