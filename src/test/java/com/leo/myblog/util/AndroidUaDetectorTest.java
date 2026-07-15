package com.leo.myblog.util;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Android UA检测器测试类
 */
@SpringBootTest
public class AndroidUaDetectorTest {
    
    @Autowired
    private AndroidUaDetector androidUaDetector;
    
    @Autowired
    private UserAgentParser userAgentParser;
    
    @Test
    public void testXiaomiWechat() {
        String ua = "Mozilla/5.0 (Linux; Android 10; MI 9 Build/QKQ1.190825.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/67.0.3396.87 XWEB/1171 MMWEBSDK/200201 Mobile Safari/537.36 MMWEBID/2568 MicroMessenger/7.0.12.1620(0x27000C37) Process/tools NetType/4G Language/zh_CN ABI/arm64";
        
        AndroidUaDetector.AndroidDeviceInfo info = androidUaDetector.detect(ua);
        assertNotNull(info);
        assertEquals("小米", info.getBrand());
        assertEquals("MI 9", info.getModel());
        assertEquals("微信", info.getBrowserName());
        assertEquals("小米手机 微信", info.getRemark());
        
        System.out.println("测试结果: " + info.getDescription());
    }
    
    @Test
    public void testHuaweiBaidu() {
        String ua = "Mozilla/5.0 (Linux; Android 10; EVR-AL00 Build/HUAWEIEVR-AL00; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/74.0.3729.186 Mobile Safari/537.36 baiduboxapp/11.0.5.12 (Baidu; P1 10)";
        
        AndroidUaDetector.AndroidDeviceInfo info = androidUaDetector.detect(ua);
        assertNotNull(info);
        assertEquals("华为", info.getBrand());
        assertEquals("EVR-AL00", info.getModel());
        assertEquals("手机百度", info.getBrowserName());
        assertEquals("华为手机 手机百度", info.getRemark());
        
        System.out.println("测试结果: " + info.getDescription());
    }
    
    @Test
    public void testHonorWechat() {
        String ua = "Mozilla/5.0 (Linux; Android 9; COL-AL10 Build/HUAWEICOL-AL10; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 MQQBrowser/6.2 TBS/045130 Mobile Safari/537.36 MMWEBID/9048 MicroMessenger/7.0.10.1580(0x27000AFE) Process/tools NetType/4G Language/zh_CN ABI/arm64";
        
        AndroidUaDetector.AndroidDeviceInfo info = androidUaDetector.detect(ua);
        assertNotNull(info);
        assertEquals("荣耀", info.getBrand());
        assertEquals("COL-AL10", info.getModel());
        assertEquals("微信", info.getBrowserName());
        assertEquals("荣耀手机 微信", info.getRemark());
        
        System.out.println("测试结果: " + info.getDescription());
    }
    
    @Test
    public void testVivoBaidu() {
        String ua = "Mozilla/5.0 (Linux; Android 9; V1813BT Build/PKQ1.181030.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/63.0.3239.83 Mobile Safari/537.36 T7/11.16 lite baiduboxapp/4.11.0.10 (Baidu; P1 9)";
        
        AndroidUaDetector.AndroidDeviceInfo info = androidUaDetector.detect(ua);
        assertNotNull(info);
        assertEquals("vivo", info.getBrand());
        assertEquals("V1813BT", info.getModel());
        assertEquals("手机百度", info.getBrowserName());
        assertEquals("vivo手机 手机百度", info.getRemark());
        
        System.out.println("测试结果: " + info.getDescription());
    }
    
    @Test
    public void testOppoQQBrowser() {
        String ua = "Mozilla/5.0 (Linux; U; Android 9; zh-cn; PACT00 Build/PPR1.180610.011) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 MQQBrowser/10.1 Mobile Safari/537.36";
        
        AndroidUaDetector.AndroidDeviceInfo info = androidUaDetector.detect(ua);
        assertNotNull(info);
        assertEquals("OPPO", info.getBrand());
        assertEquals("PACT00", info.getModel());
        assertEquals("QQ浏览器", info.getBrowserName());
        assertEquals("OPPO手机 QQ浏览器", info.getRemark());
        
        System.out.println("测试结果: " + info.getDescription());
    }
    
    @Test
    public void testRedmiMiuiBrowser() {
        String ua = "Mozilla/5.0 (Linux; U; Android 9; zh-cn; Redmi Note 5 Build/PKQ1.180904.001) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/71.0.3578.141 Mobile Safari/537.36 XiaoMi/MiuiBrowser/11.10.8";
        
        AndroidUaDetector.AndroidDeviceInfo info = androidUaDetector.detect(ua);
        assertNotNull(info);
        assertEquals("小米", info.getBrand());
        assertTrue(info.getModel().contains("Redmi Note 5"));
        assertEquals("MIUI浏览器", info.getBrowserName());
        assertEquals("小米手机 MIUI浏览器", info.getRemark());
        
        System.out.println("测试结果: " + info.getDescription());
    }
    
    @Test
    public void testSamsungBaidu() {
        String ua = "Mozilla/5.0 (Linux; Android 10; SM-G9750 Build/QP1A.190711.020; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.19 SP-engine/2.15.0 baiduboxapp/11.19.5.10 (Baidu; P1 10)";
        
        AndroidUaDetector.AndroidDeviceInfo info = androidUaDetector.detect(ua);
        assertNotNull(info);
        assertEquals("三星", info.getBrand());
        assertEquals("SM-G9750", info.getModel());
        assertEquals("手机百度", info.getBrowserName());
        assertEquals("三星手机 手机百度", info.getRemark());
        
        System.out.println("测试结果: " + info.getDescription());
    }
    
    @Test
    public void testOneplusBaidu() {
        String ua = "Mozilla/5.0 (Linux; Android 9; ONEPLUS A6000 Build/PKQ1.180716.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 9) NABar/2.0";
        
        AndroidUaDetector.AndroidDeviceInfo info = androidUaDetector.detect(ua);
        assertNotNull(info);
        assertEquals("一加", info.getBrand());
        assertEquals("ONEPLUS A6000", info.getModel());
        assertEquals("手机百度", info.getBrowserName());
        assertEquals("一加手机 手机百度", info.getRemark());
        
        System.out.println("测试结果: " + info.getDescription());
    }
    
    @Test
    public void testUserAgentParserIntegration() {
        String ua = "Mozilla/5.0 (Linux; Android 10; MI 9 Build/QKQ1.190825.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/67.0.3396.87 XWEB/1171 MMWEBSDK/200201 Mobile Safari/537.36 MMWEBID/2568 MicroMessenger/7.0.12.1620(0x27000C37) Process/tools NetType/4G Language/zh_CN ABI/arm64";
        
        UserAgentParser.DeviceInfo info = userAgentParser.parseUserAgent(ua);
        
        assertNotNull(info);
        assertEquals("MOBILE", info.getDeviceType());
        assertEquals("Android", info.getOsName());
        assertEquals("微信", info.getBrowserName());
        assertEquals("小米", info.getAndroidBrand());
        assertEquals("MI 9", info.getAndroidModel());
        assertEquals("小米手机 微信", info.getAndroidRemark());
        
        System.out.println("集成测试结果: " + info.getDescription());
    }
    
    @Test
    public void testNonAndroidUa() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        
        AndroidUaDetector.AndroidDeviceInfo info = androidUaDetector.detect(ua);
        assertNull(info, "非Android UA应该返回null");
    }
}
