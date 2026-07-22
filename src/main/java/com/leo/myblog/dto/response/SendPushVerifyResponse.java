package com.leo.myblog.dto.response;
import lombok.Data;
import java.util.List;
@Data
public class SendPushVerifyResponse {
    private String challengeId;
    private String status;
    private List<String> deviceNames;
    private long expiresAt;
}
