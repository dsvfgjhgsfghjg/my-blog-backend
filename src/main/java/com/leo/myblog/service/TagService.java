package com.leo.myblog.service;

import com.leo.myblog.dto.request.CreateTagRequest;
import com.leo.myblog.dto.request.UpdateTagRequest;
import com.leo.myblog.dto.response.TagResponse;

import java.util.List;

/**
 * 标签服务接口
 */
public interface TagService {
    
    /**
     * 创建标签
     */
    Long createTag(CreateTagRequest request);
    
    /**
     * 更新标签
     */
    void updateTag(Long tagId, UpdateTagRequest request);
    
    /**
     * 删除标签
     */
    void deleteTag(Long tagId);
    
    /**
     * 获取标签详情
     */
    TagResponse getTagById(Long tagId);
    
    /**
     * 获取所有标签列表
     */
    List<TagResponse> getAllTags();
}
