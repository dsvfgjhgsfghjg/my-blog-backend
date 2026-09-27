package com.leo.myblog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类新建/修改请求
 * <p>
 * 长度上限直接对齐建表语句（{@code V4__create_post_tables.sql}）：
 * {@code name VARCHAR(50) NOT NULL UNIQUE}、{@code description VARCHAR(255)}。
 * 这里是超长文本进库前的第一道闸，写宽了会在 insert 时炸出 SQL 异常，写窄了会莫名其妙截断用户输入。
 */
@Data
public class CategoryRequest {

    /**
     * 分类名称（必填，库里有唯一约束 t_category_name_key）
     */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50")
    private String name;

    /**
     * 分类描述（可选）
     */
    @Size(max = 255, message = "分类描述长度不能超过255")
    private String description;

    /**
     * 排序值（可空，用于列表升序排列）
     * <p>
     * 为空时的语义<b>按操作分两种</b>，见 {@code CategoryService}：
     * 新建落 0（对齐 {@code t_category.sort_order} 的 DEFAULT 0）；
     * 修改则保留原值 —— 前端编辑表单若漏传这个字段，不该把排序悄悄清零。
     * <p>
     * 拒绝负数：列表按 sort_order 升序渲染，负值等价于一个没被产品定义过的「置顶」能力，
     * 且与库里既有的「未分类=0」约定冲突。
     */
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sortOrder;
}
