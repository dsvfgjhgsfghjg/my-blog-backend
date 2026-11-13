package com.leo.myblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_invite_code")
public class InviteCode {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String codeHash;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private Long usedBy;
    private LocalDateTime revokedAt;
}
