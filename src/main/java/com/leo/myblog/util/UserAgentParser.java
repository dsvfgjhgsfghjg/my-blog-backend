package com.leo.myblog.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * User-Agent 解析工具类
 * 用于解析客户端设备信息（操作系统、浏览器、设备类型）
 * 支持Android设备细化识别（品牌、型号、浏览器）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAgentParser {
    
    private final AndroidUaDetector androidUaDetector;
    
    /**
     * 解析User-Agent字符串，提取设备信息
     */
    public DeviceInfo parseUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return createUnknownDevice();
        }
        
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setUserAgent(userAgent);
        
        // 解析设备类型
        deviceInfo.setDeviceType(detectDeviceType(userAgent));
        
        // 解析操作系统
        parseOperatingSystem(userAgent, deviceInfo);
        
        // 解析浏览器
        parseBrowser(userAgent, deviceInfo);
        
        // 如果是Android设备，进行细化识别
        if ("Android".equals(deviceInfo.getOsName())) {
            enhanceAndroidDeviceInfo(userAgent, deviceInfo);
        }
        
        log.debug("解析User-Agent: {} -> {}, {}, {}", 
                userAgent, deviceInfo.getDeviceType(), 
                deviceInfo.getOsName(), deviceInfo.getBrowserName());
        
        return deviceInfo;
    }
    
    /**
     * 检测设备类型
     */
    private String detectDeviceType(String userAgent) {
        String ua = userAgent.toLowerCase();
        
        // 检测移动设备
        if (ua.contains("mobile") || ua.contains("android") && ua.contains("mobile")) {
            return "MOBILE";
        }
        
        // 检测平板设备
        if (ua.contains("tablet") || ua.contains("ipad") || 
            (ua.contains("android") && !ua.contains("mobile"))) {
            return "TABLET";
        }
        
        // 默认为桌面设备
        return "DESKTOP";
    }
    
    /**
     * 解析操作系统
     */
    private void parseOperatingSystem(String userAgent, DeviceInfo deviceInfo) {
        String ua = userAgent.toLowerCase();
        
        // Windows
        if (ua.contains("windows nt")) {
            deviceInfo.setOsName("Windows");
            if (ua.contains("windows nt 10.0") || ua.contains("windows 10")) {
                deviceInfo.setOsVersion("10/11");
            } else if (ua.contains("windows nt 6.3")) {
                deviceInfo.setOsVersion("8.1");
            } else if (ua.contains("windows nt 6.2")) {
                deviceInfo.setOsVersion("8");
            } else if (ua.contains("windows nt 6.1")) {
                deviceInfo.setOsVersion("7");
            } else {
                deviceInfo.setOsVersion("Unknown");
            }
        }
        // macOS
        else if (ua.contains("mac os x")) {
            deviceInfo.setOsName("macOS");
            // 提取版本号，例如: Mac OS X 10_15_7
            int start = ua.indexOf("mac os x");
            if (start != -1) {
                String version = extractVersion(userAgent.substring(start));
                deviceInfo.setOsVersion(version.replace("_", "."));
            }
        }
        // Linux (需要在Android之前检测)
        else if (ua.contains("linux")) {
            // Android 也是基于 Linux，但单独处理
            if (ua.contains("android")) {
                deviceInfo.setOsName("Android");
                int start = ua.indexOf("android");
                if (start != -1) {
                    String version = extractVersion(userAgent.substring(start));
                    deviceInfo.setOsVersion(version);
                }
            }
            // Linux 桌面系统
            else {
                deviceInfo.setOsName("Linux");
                // 尝试提取更详细的发行版信息
                String distro = detectLinuxDistribution(userAgent);
                if (distro != null && !distro.isEmpty()) {
                    deviceInfo.setOsVersion(distro);
                } else if (ua.contains("x11")) {
                    // 提取架构信息
                    String arch = extractLinuxArch(userAgent);
                    deviceInfo.setOsVersion("X11" + (arch != null ? " " + arch : ""));
                } else {
                    deviceInfo.setOsVersion("Unknown");
                }
            }
        }
        // iOS
        else if (ua.contains("iphone os") || ua.contains("ipad os")) {
            deviceInfo.setOsName("iOS");
            int start = ua.contains("iphone os") ? ua.indexOf("iphone os") : ua.indexOf("ipad os");
            if (start != -1) {
                String version = extractVersion(userAgent.substring(start));
                deviceInfo.setOsVersion(version);
            }
        }
        // Unknown
        else {
            deviceInfo.setOsName("Unknown");
            deviceInfo.setOsVersion("Unknown");
        }
    }
    
    /**
     * 解析浏览器
     */
    private void parseBrowser(String userAgent, DeviceInfo deviceInfo) {
        String ua = userAgent.toLowerCase();
        
        // Edge (必须在Chrome之前检测)
        if (ua.contains("edg/") || ua.contains("edge/")) {
            deviceInfo.setBrowserName("Edge");
            String version = extractBrowserVersion(ua, "edg/", "edge/");
            deviceInfo.setBrowserVersion(version);
        }
        // Chrome (必须在Safari之前检测)
        else if (ua.contains("chrome/") && !ua.contains("chromium")) {
            deviceInfo.setBrowserName("Chrome");
            int start = ua.indexOf("chrome/");
            String version = extractVersion(userAgent.substring(start));
            deviceInfo.setBrowserVersion(version);
        }
        // Firefox
        else if (ua.contains("firefox/")) {
            deviceInfo.setBrowserName("Firefox");
            int start = ua.indexOf("firefox/");
            String version = extractVersion(userAgent.substring(start));
            deviceInfo.setBrowserVersion(version);
        }
        // Safari (必须在最后检测)
        else if (ua.contains("safari/") && !ua.contains("chrome/")) {
            deviceInfo.setBrowserName("Safari");
            int start = ua.indexOf("version/");
            if (start != -1) {
                String version = extractVersion(userAgent.substring(start));
                deviceInfo.setBrowserVersion(version);
            } else {
                deviceInfo.setBrowserVersion("Unknown");
            }
        }
        // Opera
        else if (ua.contains("opr/") || ua.contains("opera/")) {
            deviceInfo.setBrowserName("Opera");
            String version = extractBrowserVersion(ua, "opr/", "opera/");
            deviceInfo.setBrowserVersion(version);
        }
        // Unknown
        else {
            deviceInfo.setBrowserName("Unknown");
            deviceInfo.setBrowserVersion("Unknown");
        }
    }
    
    /**
     * 提取版本号
     */
    private String extractVersion(String text) {
        // 匹配数字和点号组成的版本号
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[0-9]+([._][0-9]+)*");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group().replace("_", ".");
        }
        return "Unknown";
    }
    
    /**
     * 提取浏览器版本
     */
    private String extractBrowserVersion(String ua, String... prefixes) {
        for (String prefix : prefixes) {
            int start = ua.indexOf(prefix);
            if (start != -1) {
                return extractVersion(ua.substring(start + prefix.length()));
            }
        }
        return "Unknown";
    }
    
    /**
     * 增强Android设备信息（品牌、型号、备注）
     */
    private void enhanceAndroidDeviceInfo(String userAgent, DeviceInfo deviceInfo) {
        try {
            AndroidUaDetector.AndroidDeviceInfo androidInfo = androidUaDetector.detect(userAgent);
            if (androidInfo != null) {
                deviceInfo.setAndroidBrand(androidInfo.getBrand());
                deviceInfo.setAndroidModel(androidInfo.getModel());
                deviceInfo.setAndroidRemark(androidInfo.getRemark());
                deviceInfo.setAndroidDescription(androidInfo.getDescription());
                
                // 如果Android检测到的浏览器更准确，则覆盖
                if (!"未知浏览器".equals(androidInfo.getBrowserName())) {
                    deviceInfo.setBrowserName(androidInfo.getBrowserName());
                }
            }
        } catch (Exception e) {
            log.warn("Android设备信息增强失败: {}", e.getMessage());
        }
    }
    
    /**
     * 创建未知设备信息
     */
    private DeviceInfo createUnknownDevice() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceType("UNKNOWN");
        deviceInfo.setOsName("Unknown");
        deviceInfo.setOsVersion("Unknown");
        deviceInfo.setBrowserName("Unknown");
        deviceInfo.setBrowserVersion("Unknown");
        deviceInfo.setUserAgent("Unknown");
        return deviceInfo;
    }
    
    /**
     * 检测Linux发行版
     */
    private String detectLinuxDistribution(String userAgent) {
        String ua = userAgent.toLowerCase();
            
        // Ubuntu
        if (ua.contains("ubuntu")) {
            int index = ua.indexOf("ubuntu");
            String version = extractVersion(userAgent.substring(index));
            return "Ubuntu" + (version != null && !"Unknown".equals(version) ? " " + version : "");
        }
            
        // Fedora
        if (ua.contains("fedora")) {
            int index = ua.indexOf("fedora");
            String version = extractVersion(userAgent.substring(index));
            return "Fedora" + (version != null && !"Unknown".equals(version) ? " " + version : "");
        }
            
        // CentOS
        if (ua.contains("centos")) {
            int index = ua.indexOf("centos");
            String version = extractVersion(userAgent.substring(index));
            return "CentOS" + (version != null && !"Unknown".equals(version) ? " " + version : "");
        }
            
        // Debian
        if (ua.contains("debian")) {
            int index = ua.indexOf("debian");
            String version = extractVersion(userAgent.substring(index));
            return "Debian" + (version != null && !"Unknown".equals(version) ? " " + version : "");
        }
            
        // Arch Linux
        if (ua.contains("arch linux") || ua.contains("archlinux")) {
            return "Arch Linux";
        }
            
        // openSUSE
        if (ua.contains("opensuse") || ua.contains("suse")) {
            return "openSUSE";
        }
            
        // Gentoo
        if (ua.contains("gentoo")) {
            return "Gentoo";
        }
            
        // Deepin（深度）
        if (ua.contains("deepin")) {
            return "Deepin";
        }
            
        // UOS（统信）
        if (ua.contains("uos") || ua.contains("uniontech")) {
            return "UOS";
        }
            
        // Kylin（麒麟）
        if (ua.contains("kylin")) {
            return "Kylin";
        }
            
        return null;
    }
        
    /**
     * 提取Linux架构信息
     */
    private String extractLinuxArch(String userAgent) {
        String ua = userAgent.toLowerCase();
            
        // x86_64 / amd64
        if (ua.contains("x86_64") || ua.contains("amd64")) {
            return "x86_64";
        }
            
        // aarch64 / arm64
        if (ua.contains("aarch64") || ua.contains("arm64")) {
            return "ARM64";
        }
            
        // i686 / i386
        if (ua.contains("i686") || ua.contains("i386")) {
            return "x86";
        }
            
        // ARM
        if (ua.contains("armv7") || ua.contains("armhf")) {
            return "ARM";
        }
            
        // RISC-V
        if (ua.contains("riscv") || ua.contains("risc-v")) {
            return "RISC-V";
        }
            
        return null;
    }
    
    /**
     * 设备信息内部类
     */
    public static class DeviceInfo {
        private String deviceType;
        private String osName;
        private String osVersion;
        private String browserName;
        private String browserVersion;
        private String userAgent;
        
        // Android设备特有字段
        private String androidBrand;      // Android品牌：小米、华为、荣耀等
        private String androidModel;      // Android型号：Redmi Note 5、Mate 20等
        private String androidRemark;     // Android备注：如"小米手机 微信"
        private String androidDescription;// Android完整描述
        
        // Getters and Setters
        public String getDeviceType() { return deviceType; }
        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
        
        public String getOsName() { return osName; }
        public void setOsName(String osName) { this.osName = osName; }
        
        public String getOsVersion() { return osVersion; }
        public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
        
        public String getBrowserName() { return browserName; }
        public void setBrowserName(String browserName) { this.browserName = browserName; }
        
        public String getBrowserVersion() { return browserVersion; }
        public void setBrowserVersion(String browserVersion) { this.browserVersion = browserVersion; }
        
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
        
        public String getAndroidBrand() { return androidBrand; }
        public void setAndroidBrand(String androidBrand) { this.androidBrand = androidBrand; }
        
        public String getAndroidModel() { return androidModel; }
        public void setAndroidModel(String androidModel) { this.androidModel = androidModel; }
        
        public String getAndroidRemark() { return androidRemark; }
        public void setAndroidRemark(String androidRemark) { this.androidRemark = androidRemark; }
        
        public String getAndroidDescription() { return androidDescription; }
        public void setAndroidDescription(String androidDescription) { this.androidDescription = androidDescription; }
        
        /**
         * 生成可读的设备描述
         */
        public String getDescription() {
            // 如果有Android详细信息，优先使用
            if (androidDescription != null && !androidDescription.isEmpty()) {
                return androidDescription;
            }
                
            // Linux桌面系统特殊处理 - 显示更详细的信息
            if ("Linux".equals(osName)) {
                String osDetail = osVersion != null && !"Unknown".equals(osVersion) ? osVersion : "";
                    
                // 如果包含X11和架构信息
                if (osDetail.contains("X11")) {
                    return String.format("%s %s on %s (%s)", browserName, browserVersion, osDetail, deviceType);
                }
                // 如果检测到发行版
                else if (!osDetail.isEmpty()) {
                    return String.format("%s %s on %s (%s)", browserName, browserVersion, osDetail, deviceType);
                }
                // 默认Linux
                else {
                    return String.format("%s %s on Linux (%s)", browserName, browserVersion, deviceType);
                }
            }
                
            // 其他系统
            return String.format("%s %s on %s %s (%s)", 
                    browserName, browserVersion, osName, osVersion, deviceType);
        }
    }
}
