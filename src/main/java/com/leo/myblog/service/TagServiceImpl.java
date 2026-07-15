package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.dto.request.CreateTagRequest;
import com.leo.myblog.dto.request.UpdateTagRequest;
import com.leo.myblog.dto.response.TagResponse;
import com.leo.myblog.entity.PostTag;
import com.leo.myblog.entity.Tag;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.PostTagMapper;
import com.leo.myblog.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 标签服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {
    
    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;
    
    /**
     * 创建标签
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTag(CreateTagRequest request) {
        // 1. 检查标签名称是否已存在
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getName, request.getName());
        if (tagMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("标签名称已存在");
        }
        
        // 2. 创建标签
        Tag tag = new Tag();
        BeanUtils.copyProperties(request, tag);
        tag.setCreateTime(LocalDateTime.now());
        
        tagMapper.insert(tag);
        
        log.info("标签创建成功: tagId={}, name={}", tag.getId(), request.getName());
        
        return tag.getId();
    }
    
    /**
     * 更新标签
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTag(Long tagId, UpdateTagRequest request) {
        // 1. 查询标签
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException("标签不存在");
        }
        
        // 2. 如果修改了名称，检查新名称是否已被其他标签使用
        if (!request.getName().equals(tag.getName())) {
            LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Tag::getName, request.getName())
                   .ne(Tag::getId, tagId);
            if (tagMapper.selectCount(wrapper) > 0) {
                throw new BusinessException("标签名称已存在");
            }
        }
        
        // 3. 更新标签
        tag.setName(request.getName());
        tag.setColor(request.getColor());
        
        tagMapper.updateById(tag);
        
        log.info("标签更新成功: tagId={}, name={}", tagId, request.getName());
    }
    
    /**
     * 删除标签
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long tagId) {
        // 1. 查询标签
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException("标签不存在");
        }
        
        // 2. 检查是否有文章使用该标签
        LambdaQueryWrapper<PostTag> postTagWrapper = new LambdaQueryWrapper<>();
        postTagWrapper.eq(PostTag::getTagId, tagId);
        Long usageCount = postTagMapper.selectCount(postTagWrapper);
        
        if (usageCount > 0) {
            log.warn("标签被 {} 篇文章使用，无法删除: tagId={}, name={}", usageCount, tagId, tag.getName());
            throw new BusinessException(String.format(
                "该标签已被 %d 篇文章使用，无法删除。请先从相关文章中移除此标签。", 
                usageCount
            ));
        }
        
        // 3. 删除标签（物理删除）
        tagMapper.deleteById(tagId);
        
        log.info("标签删除成功: tagId={}, name={}", tagId, tag.getName());
    }
    
    /**
     * 获取标签详情
     */
    @Override
    public TagResponse getTagById(Long tagId) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException("标签不存在");
        }
        
        return convertToResponse(tag);
    }
    
    /**
     * 获取所有标签列表
     */
    @Override
    public List<TagResponse> getAllTags() {
        List<Tag> tags = tagMapper.selectList(null);
        
        return tags.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * 转换为响应对象
     */
    private TagResponse convertToResponse(Tag tag) {
        TagResponse response = new TagResponse();
        BeanUtils.copyProperties(tag, response);
        return response;
    }
}
