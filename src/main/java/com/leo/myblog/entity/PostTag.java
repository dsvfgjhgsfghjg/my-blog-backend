package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章-标签关联实体（多对多）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_post_tag")
public class PostTag {
    
    /**
     * 文章ID
     */
    private Long postId;
    
    /**
     * 标签ID
     */
    private Long tagId;
}
