package com.leo.myblog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 添加名言请求
 */
@Data
public class AddSayingRequest {

    /**
     * 名言内容
     */
    @NotBlank(message = "名言内容不能为空")
    private String content;

    /**
     * 作者
     */
    @NotBlank(message = "作者不能为空")
    private String author;

    /**
     * 作者英文名（可选）
     */
    private String authorEn;

    /**
     * 标签（可选）
     */
    private List<String> tags;
}