package com.leo.myblog.dto.response;

import lombok.Data;

/**
 * 图片上传响应
 */
@Data
public class ImageUploadResponse {

    /** 可直接使用的图片 URL（用于 <img> 标签） */
    private String url;

    /** 存储文件名（UUID 文件名） */
    private String filename;

    /** 原始文件名 */
    private String originalName;

    /** 文件大小（字节） */
    private Long size;

    /** 图片宽度（像素），SVG 可能为 null */
    private Integer width;

    /** 图片高度（像素），SVG 可能为 null */
    private Integer height;

    /** MIME 类型 */
    private String mimeType;
}
