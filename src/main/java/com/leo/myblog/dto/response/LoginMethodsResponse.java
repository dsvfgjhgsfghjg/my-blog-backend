package com.leo.myblog.dto.response;
import lombok.Data;
import java.util.List;
@Data
public class LoginMethodsResponse {
    private String defaultMethod;
    private List<String> availableMethods;
    private boolean hasBoundDevices;
    private List<DeviceInfo> devices;
    @Data
    public static class DeviceInfo {
        private String deviceId;
        private String deviceName;
        private boolean online;
    }
}
