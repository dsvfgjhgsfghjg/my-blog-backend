package com.leo.myblog.dto.request;
import lombok.Data;
@Data
public class VerifyTotpRequest {
    private String operation;
    private String code;
}
