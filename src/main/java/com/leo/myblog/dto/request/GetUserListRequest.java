package com.leo.myblog.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询用户请求
 */
@Data
public class GetUserListRequest {
    
    @Min(value = 1, message = "页码必须大于等于1")
    private Integer pageNum = 1;
    
    @Min(value = 1, message = "每页数量必须大于等于1")
    @Max(value = 50, message = "每页最多只能获取50个用户")
    private Integer pageSize = 10;
    
    // 按用户名模糊查询（可选）
    private String username;
    
    // 按状态筛选（可选）
    private Boolean enabled;
}
