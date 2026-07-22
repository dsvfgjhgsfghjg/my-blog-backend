package com.leo.myblog.dto.response;
import lombok.Data;
import java.util.List;
@Data
public class VerifyStatusResponse {
    private boolean needVerify;
    private String lastMethod;
    private List<String> availableMethods;
    private boolean totpEnabled;
    private boolean hasBoundDevices;
    private List<String> deviceNames;
}
