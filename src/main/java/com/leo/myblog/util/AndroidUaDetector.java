package com.leo.myblog.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android User-Agent 识别工具类
 * 基于真实设备UA库，提供高性能的Android设备识别
 * 
 * 功能：
 * 1. 识别手机品牌（小米、华为、荣耀、OPPO、vivo等）
 * 2. 识别具体型号（Redmi Note 5、Mate 20等）
 * 3. 识别浏览器类型（微信、百度、Chrome、Edge等）
 * 4. 使用预编译正则和缓存优化性能
 */
@Slf4j
@Component
public class AndroidUaDetector {
    
    // ========== 设备品牌识别规则（按优先级排序）==========
    private static final Map<String, String> BRAND_PATTERNS = new LinkedHashMap<>();
    
    // ========== 浏览器识别规则 ==========
    private static final Map<String, BrowserInfo> BROWSER_PATTERNS = new LinkedHashMap<>();
    
    // ========== 型号提取正则 ==========
    private static final Pattern MODEL_PATTERN = Pattern.compile(
        "(?:Build/|;\\s*)([A-Z]{2,}-?[A-Z0-9]+(?:\\s[A-Z0-9]+)?)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern XIAOMI_MODEL_PATTERN = Pattern.compile(
        "(Redmi\\s+[A-Za-z0-9\\s]+|MI\\s+[0-9]+[A-Za-z]*|MIX\\s*[0-9]*)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern HUAWEI_MODEL_PATTERN = Pattern.compile(
        "(HUAWEI\\s*[A-Z]{2,}-?[A-Z0-9]+|[A-Z]{2,}-[A-Z0-9]+)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern HONOR_MODEL_PATTERN = Pattern.compile(
        "(HONOR\\s*[A-Z]{2,}-?[A-Z0-9]+|[A-Z]{2,}-[A-Z0-9]+)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern OPPO_MODEL_PATTERN = Pattern.compile(
        "(OPPO\\s*[A-Z0-9]+|[A-Z]{4,}[0-9]+)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern VIVO_MODEL_PATTERN = Pattern.compile(
        "(vivo\\s*[A-Z0-9]+|V[0-9]{4}[A-Z]{0,2})",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SAMSUNG_MODEL_PATTERN = Pattern.compile(
        "(SM-[A-Z0-9]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ONEPLUS_MODEL_PATTERN = Pattern.compile(
        "(ONEPLUS\\s*[A-Z0-9]+)",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * 初始化品牌识别规则
     */
    @PostConstruct
    public void init() {
        // 小米系列
        BRAND_PATTERNS.put("XiaoMi/MiuiBrowser", "小米");
        BRAND_PATTERNS.put("Redmi", "小米");
        BRAND_PATTERNS.put("MI ", "小米");
        BRAND_PATTERNS.put("MIX ", "小米");
        
        // 荣耀系列
        //
        // ⚠️ 必须排在华为之前。荣耀设备的 UA 在 build 段会出现 "HUAWEI" 字样
        //    （如 "COL-AL10 Build/HUAWEICOL-AL10"），而品牌判定是
        //    LinkedHashMap 的插入顺序 + contains 子串匹配，先命中先返回。
        //    若华为在前，泛化的 "HUAWEI" 会抢先命中，把荣耀误判成华为。
        BRAND_PATTERNS.put("HONOR", "荣耀");
        BRAND_PATTERNS.put("COL-", "荣耀");
        BRAND_PATTERNS.put("JSN-", "荣耀");
        BRAND_PATTERNS.put("PRA-", "荣耀");
        BRAND_PATTERNS.put("BLN-", "荣耀");
        BRAND_PATTERNS.put("YAL-", "荣耀");

        // 华为系列
        //
        // 具体机型代号在前，"HUAWEI" 这个泛化子串放到最后兜底：
        // 它是子串匹配，出现在别处（如上面的荣耀 build 段）会误命中，
        // 因此让机型代号先有机会判定。
        BRAND_PATTERNS.put("HWI-", "华为");
        BRAND_PATTERNS.put("ELE-", "华为");
        BRAND_PATTERNS.put("VOG-", "华为");
        BRAND_PATTERNS.put("CLT-", "华为");
        BRAND_PATTERNS.put("HMA-", "华为");
        BRAND_PATTERNS.put("LIO-", "华为");
        BRAND_PATTERNS.put("LYA-", "华为");
        BRAND_PATTERNS.put("EVR-", "华为");
        BRAND_PATTERNS.put("SEA-", "华为");
        BRAND_PATTERNS.put("VCE-", "华为");
        BRAND_PATTERNS.put("JKM-", "华为");
        BRAND_PATTERNS.put("INE-", "华为");
        BRAND_PATTERNS.put("ANE-", "华为");
        BRAND_PATTERNS.put("STK-", "华为");
        BRAND_PATTERNS.put("JNY-", "华为");
        BRAND_PATTERNS.put("HLK-", "华为");
        BRAND_PATTERNS.put("COR-", "华为");
        BRAND_PATTERNS.put("RNE-", "华为");
        BRAND_PATTERNS.put("DUB-", "华为");
        BRAND_PATTERNS.put("ALP-", "华为");
        BRAND_PATTERNS.put("BLA-", "华为");
        BRAND_PATTERNS.put("LON-", "华为");
        BRAND_PATTERNS.put("NXT-", "华为");
        BRAND_PATTERNS.put("PIC-", "华为");
        BRAND_PATTERNS.put("HUAWEI", "华为");
        
        // OPPO系列
        BRAND_PATTERNS.put("OPPO", "OPPO");
        BRAND_PATTERNS.put("PBAM", "OPPO");
        BRAND_PATTERNS.put("PBDM", "OPPO");
        BRAND_PATTERNS.put("PACT", "OPPO");
        
        // vivo系列
        BRAND_PATTERNS.put("vivo", "vivo");
        BRAND_PATTERNS.put("V1813", "vivo");
        BRAND_PATTERNS.put("V1816", "vivo");
        BRAND_PATTERNS.put("V1732", "vivo");
        BRAND_PATTERNS.put("V1836", "vivo");
        BRAND_PATTERNS.put("V1913", "vivo");
        BRAND_PATTERNS.put("V1916", "vivo");
        
        // 三星
        BRAND_PATTERNS.put("Samsung", "三星");
        BRAND_PATTERNS.put("SM-", "三星");
        
        // 一加
        BRAND_PATTERNS.put("ONEPLUS", "一加");
        
        // 魅族
        BRAND_PATTERNS.put("Meizu", "魅族");
        BRAND_PATTERNS.put("M18", "魅族");
        
        // 黑鲨
        BRAND_PATTERNS.put("BlackShark", "黑鲨");
        BRAND_PATTERNS.put("SKW-", "黑鲨");
        
        // 努比亚
        BRAND_PATTERNS.put("NX606", "努比亚");
        
        // 初始化浏览器识别规则
        initBrowserPatterns();
        
        log.info("Android UA检测器初始化完成，加载{}条品牌规则，{}条浏览器规则", 
                BRAND_PATTERNS.size(), BROWSER_PATTERNS.size());
    }
    
    /**
     * 初始化浏览器识别规则
     */
    private void initBrowserPatterns() {
        // 微信内置浏览器
        BROWSER_PATTERNS.put("MicroMessenger", 
            new BrowserInfo("微信", "WeChat", "腾讯微信内置浏览器"));
        
        // 手机百度
        BROWSER_PATTERNS.put("baiduboxapp", 
            new BrowserInfo("手机百度", "BaiduBox", "百度APP内置浏览器"));
        BROWSER_PATTERNS.put("baidubrowser", 
            new BrowserInfo("百度浏览器", "BaiduBrowser", "百度浏览器"));
        
        // MIUI浏览器
        BROWSER_PATTERNS.put("XiaoMi/MiuiBrowser", 
            new BrowserInfo("MIUI浏览器", "MiuiBrowser", "小米系统浏览器"));
        
        // QQ浏览器
        BROWSER_PATTERNS.put("MQQBrowser", 
            new BrowserInfo("QQ浏览器", "QQBrowser", "腾讯QQ浏览器"));
        
        // UC浏览器
        BROWSER_PATTERNS.put("UCBrowser", 
            new BrowserInfo("UC浏览器", "UCBrowser", "阿里UC浏览器"));
        
        // Edge浏览器
        BROWSER_PATTERNS.put("Edg/", 
            new BrowserInfo("Edge", "Edge", "微软Edge浏览器"));
        BROWSER_PATTERNS.put("Edge/", 
            new BrowserInfo("Edge", "Edge", "微软Edge浏览器"));
        
        // Chrome浏览器
        BROWSER_PATTERNS.put("Chrome/", 
            new BrowserInfo("Chrome", "Chrome", "谷歌Chrome浏览器"));
        
        // Firefox浏览器
        BROWSER_PATTERNS.put("Firefox/", 
            new BrowserInfo("Firefox", "Firefox", "火狐浏览器"));
        
        // Safari浏览器
        BROWSER_PATTERNS.put("Safari/", 
            new BrowserInfo("Safari", "Safari", "苹果Safari浏览器"));
        
        // Opera浏览器
        BROWSER_PATTERNS.put("OPR/", 
            new BrowserInfo("Opera", "Opera", "欧朋浏览器"));
        BROWSER_PATTERNS.put("Opera/", 
            new BrowserInfo("Opera", "Opera", "欧朋浏览器"));
    }
    
    /**
     * 检测Android设备信息
     * 
     * @param userAgent User-Agent字符串
     * @return Android设备信息，如果不是Android设备返回null
     */
    public AndroidDeviceInfo detect(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return null;
        }
        
        // 检查是否为Android设备
        if (!userAgent.toLowerCase().contains("android")) {
            return null;
        }
        
        AndroidDeviceInfo info = new AndroidDeviceInfo();
        info.setUserAgent(userAgent);
        
        // 1. 识别品牌
        String brand = detectBrand(userAgent);
        info.setBrand(brand);
        
        // 2. 识别型号
        String model = detectModel(userAgent, brand);
        info.setModel(model);
        
        // 3. 识别浏览器
        BrowserInfo browser = detectBrowser(userAgent);
        info.setBrowserName(browser.getName());
        info.setBrowserCode(browser.getCode());
        info.setBrowserDescription(browser.getDescription());
        
        // 4. 生成备注信息
        info.setRemark(generateRemark(brand, model, browser));
        
        // 5. 生成完整描述
        info.setDescription(generateDescription(info));
        
        log.debug("Android UA识别: {} -> 品牌={}, 型号={}, 浏览器={}", 
                userAgent.substring(0, Math.min(50, userAgent.length())), 
                brand, model, browser.getName());
        
        return info;
    }
    
    /**
     * 识别品牌
     */
    private String detectBrand(String userAgent) {
        // 按优先级匹配品牌规则
        for (Map.Entry<String, String> entry : BRAND_PATTERNS.entrySet()) {
            if (userAgent.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return "未知品牌";
    }
    
    /**
     * 识别型号
     */
    private String detectModel(String userAgent, String brand) {
        // 针对特定品牌使用专用正则
        switch (brand) {
            case "小米":
                return extractModelWithPattern(userAgent, XIAOMI_MODEL_PATTERN);
            case "华为":
                return extractModelWithPattern(userAgent, HUAWEI_MODEL_PATTERN);
            case "荣耀":
                return extractModelWithPattern(userAgent, HONOR_MODEL_PATTERN);
            case "OPPO":
                return extractModelWithPattern(userAgent, OPPO_MODEL_PATTERN);
            case "vivo":
                return extractModelWithPattern(userAgent, VIVO_MODEL_PATTERN);
            case "三星":
                // 三星型号以 SM- 开头，通用正则会把 "Android 10" 误当型号
                return extractModelWithPattern(userAgent, SAMSUNG_MODEL_PATTERN);
            case "一加":
                // 一加型号以 ONEPLUS 开头，通用正则会误匹配 "Android 9"
                return extractModelWithPattern(userAgent, ONEPLUS_MODEL_PATTERN);
            default:
                // 通用型号提取
                return extractGenericModel(userAgent);
        }
    }
    
    /**
     * 使用指定正则提取型号
     */
    private String extractModelWithPattern(String userAgent, Pattern pattern) {
        Matcher matcher = pattern.matcher(userAgent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "未知型号";
    }
    
    /**
     * 通用型号提取
     */
    private String extractGenericModel(String userAgent) {
        Matcher matcher = MODEL_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            String model = matcher.group(1).trim();
            // 过滤掉明显的非型号字符串
            if (model.length() > 2 && model.length() < 30) {
                return model;
            }
        }
        return "未知型号";
    }
    
    /**
     * 识别浏览器
     */
    private BrowserInfo detectBrowser(String userAgent) {
        // 按优先级匹配浏览器规则
        for (Map.Entry<String, BrowserInfo> entry : BROWSER_PATTERNS.entrySet()) {
            if (userAgent.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return new BrowserInfo("未知浏览器", "Unknown", "未知浏览器");
    }
    
    /**
     * 生成备注信息
     */
    private String generateRemark(String brand, String model, BrowserInfo browser) {
        return String.format("%s手机 %s", brand, browser.getName());
    }
    
    /**
     * 生成完整描述
     */
    private String generateDescription(AndroidDeviceInfo info) {
        return String.format("%s %s - %s (%s)", 
                info.getBrand(), 
                info.getModel(),
                info.getBrowserName(),
                info.getRemark());
    }
    
    /**
     * Android设备信息类
     */
    public static class AndroidDeviceInfo {
        private String brand;              // 品牌：小米、华为、荣耀等
        private String model;              // 型号：Redmi Note 5、Mate 20等
        private String browserName;        // 浏览器中文名：微信、手机百度等
        private String browserCode;        // 浏览器代码：WeChat、BaiduBox等
        private String browserDescription; // 浏览器描述
        private String remark;             // 备注：如"小米手机 微信"
        private String description;        // 完整描述
        private String userAgent;          // 原始UA
        
        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }
        
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        
        public String getBrowserName() { return browserName; }
        public void setBrowserName(String browserName) { this.browserName = browserName; }
        
        public String getBrowserCode() { return browserCode; }
        public void setBrowserCode(String browserCode) { this.browserCode = browserCode; }
        
        public String getBrowserDescription() { return browserDescription; }
        public void setBrowserDescription(String browserDescription) { 
            this.browserDescription = browserDescription; 
        }
        
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    }
    
    /**
     * 浏览器信息类
     */
    public static class BrowserInfo {
        private final String name;
        private final String code;
        private final String description;
        
        public BrowserInfo(String name, String code, String description) {
            this.name = name;
            this.code = code;
            this.description = description;
        }
        
        public String getName() { return name; }
        public String getCode() { return code; }
        public String getDescription() { return description; }
    }
}
