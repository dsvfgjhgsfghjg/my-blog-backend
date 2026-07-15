package com.leo.myblog.controller;

import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.annotation.RequireTotp;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.CreateTagRequest;
import com.leo.myblog.dto.request.UpdateTagRequest;
import com.leo.myblog.dto.response.TagResponse;
import com.leo.myblog.service.TagService;
import com.leo.myblog.util.AbilityValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {
    
    private final TagService tagService;
    private final AbilityValidator abilityValidator;
    
    /**
     * 创建标签（需要管理员权限、edit_tags能力 + TOTP双因素认证）
     */
    @RequireTotp(operation = "创建标签", module = "security.totp.tag_management")
    @RequireAdmin
    @PostMapping
    public Result<Long> createTag(@RequestAttribute Long userId,
                                   @Valid @RequestBody CreateTagRequest request) {
        log.info("创建标签: userId={}, name={}", userId, request.getName());
        // 验证能力：需要 edit_tags 能力
        abilityValidator.validateAbility(userId, "edit_tags");
        Long tagId = tagService.createTag(request);
        return Result.success("标签创建成功", tagId);
    }
    
    /**
     * 更新标签（需要管理员权限、edit_tags能力 + TOTP双因素认证）
     */
    @RequireTotp(operation = "更新标签", module = "security.totp.tag_management")
    @RequireAdmin
    @PutMapping("/{tagId}")
    public Result<Void> updateTag(@RequestAttribute Long userId,
                                   @PathVariable Long tagId,
                                   @Valid @RequestBody UpdateTagRequest request) {
        log.info("更新标签: userId={}, tagId={}, name={}", userId, tagId, request.getName());
        // 验证能力：需要 edit_tags 能力
        abilityValidator.validateAbility(userId, "edit_tags");
        tagService.updateTag(tagId, request);
        return Result.success("标签更新成功", null);
    }
    
    /**
     * 删除标签（需要管理员权限、edit_tags能力 + TOTP双因素认证）
     */
    @RequireTotp(operation = "删除标签", module = "security.totp.tag_management")
    @RequireAdmin
    @DeleteMapping("/{tagId}")
    public Result<Void> deleteTag(@RequestAttribute Long userId,
                                   @PathVariable Long tagId) {
        log.info("删除标签: userId={}, tagId={}", userId, tagId);
        // 验证能力：需要 edit_tags 能力
        abilityValidator.validateAbility(userId, "edit_tags");
        tagService.deleteTag(tagId);
        return Result.success("标签删除成功", null);
    }
    
    /**
     * 获取标签详情（公开接口）
     */
    @GetMapping("/{tagId}")
    public Result<TagResponse> getTagById(@PathVariable Long tagId) {
        log.debug("获取标签详情: tagId={}", tagId);
        TagResponse tag = tagService.getTagById(tagId);
        return Result.success(tag);
    }
    
    /**
     * 获取所有标签列表（公开接口）
     */
    @GetMapping
    public Result<List<TagResponse>> getAllTags() {
        log.debug("获取所有标签列表");
        List<TagResponse> tags = tagService.getAllTags();
        return Result.success(tags);
    }
}
