package com.leo.myblog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 名言名句响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SayingResponse {

    private Integer id;
    private String content;
    private String author;
    private String authorEn;
    private List<String> tags;
    private String module;
}