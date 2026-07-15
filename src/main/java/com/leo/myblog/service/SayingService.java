package com.leo.myblog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.dto.request.AddSayingRequest;
import com.leo.myblog.dto.response.SayingResponse;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 名言名句服务
 * 优先从 MinIO 对象存储读取，不存在时回退到 Jar 内置资源
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SayingService {

    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * 模块名映射：API 路径名 → 文件名前缀
     */
    private static final Map<String, String> MODULE_FILE_MAP = Map.of(
            "cs", "cs_saying",
            "design", "design_saying",
            "study", "study_saying",
            "technology", "Technology_saying",
            "thought", "thought_saying"
    );

    /**
     * 模块名映射：API 路径名 → 中文名
     */
    private static final Map<String, String> MODULE_NAME_MAP = Map.of(
            "cs", "计算机",
            "design", "设计",
            "study", "学习",
            "technology", "科技",
            "thought", "思想"
    );

    /**
     * 验证模块名是否有效
     */
    public boolean isValidModule(String module) {
        return MODULE_FILE_MAP.containsKey(module);
    }

    /**
     * 获取模块中文名
     */
    public String getModuleName(String module) {
        return MODULE_NAME_MAP.getOrDefault(module, module);
    }

    /**
     * 获取指定模块的所有名言
     * 优先从 MinIO 读取，不存在时从 Jar 内置资源加载
     */
    public List<SayingResponse> getSayings(String module) {
        // 1. 尝试从 MinIO 读取
        try {
            String minioPath = "sayings/" + module + ".json";
            try {
                // 检查 MinIO 中是否存在
                minioClient.statObject(StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(minioPath)
                        .build());

                // 存在则读取
                GetObjectArgs args = GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(minioPath)
                        .build();
                try (InputStream inputStream = minioClient.getObject(args)) {
                    byte[] bytes = inputStream.readAllBytes();
                    List<Map<String, Object>> rawList = objectMapper.readValue(bytes,
                            new TypeReference<List<Map<String, Object>>>() {});
                    return convertToResponse(rawList, module);
                }
            } catch (ErrorResponseException e) {
                // MinIO 中不存在，忽略，继续回退
                log.debug("MinIO 中不存在名言数据: module={}, path={}, 将使用内置资源", module, minioPath);
            }
        } catch (Exception e) {
            log.warn("从 MinIO 读取名言失败，将回退到内置资源: module={}", module, e);
        }

        // 2. 从 Jar 内置资源读取
        return loadFromClasspath(module);
    }

    /**
     * 从指定模块随机获取一条名言
     */
    public SayingResponse getRandomSaying(String module) {
        List<SayingResponse> sayings = getSayings(module);
        if (sayings.isEmpty()) {
            return null;
        }
        Random random = new Random();
        return sayings.get(random.nextInt(sayings.size()));
    }

    /**
     * 添加名言到指定模块
     */
    public SayingResponse addSaying(String module, AddSayingRequest request) {
        // 1. 读取当前数据
        List<SayingResponse> sayings = getSayings(module);

        // 2. 计算新 ID
        int newId = sayings.stream()
                .mapToInt(SayingResponse::getId)
                .max()
                .orElse(0) + 1;

        // 3. 构建新条目
        SayingResponse newSaying = SayingResponse.builder()
                .id(newId)
                .content(request.getContent())
                .author(request.getAuthor())
                .authorEn(request.getAuthorEn() != null ? request.getAuthorEn() : "")
                .tags(request.getTags() != null ? request.getTags() : new ArrayList<>())
                .module(module)
                .build();

        // 4. 追加并排序（按 id 排序）
        sayings.add(newSaying);
        sayings.sort(Comparator.comparingInt(SayingResponse::getId));

        // 5. 写回 MinIO
        saveToMinio(module, sayings);

        return newSaying;
    }

    /**
     * 保存名言列表到 MinIO
     */
    private void saveToMinio(String module, List<SayingResponse> sayings) {
        try {
            // 转换为原始格式（去掉 module 字段）
            List<Map<String, Object>> rawList = sayings.stream()
                    .map(s -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("id", s.getId());
                        map.put("content", s.getContent());
                        map.put("author", s.getAuthor());
                        map.put("author_en", s.getAuthorEn());
                        map.put("tags", s.getTags());
                        return map;
                    })
                    .collect(Collectors.toList());

            String jsonContent = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(rawList);

            String minioPath = "sayings/" + module + ".json";
            byte[] contentBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
            InputStream inputStream = new ByteArrayInputStream(contentBytes);

            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(minioPath)
                    .stream(inputStream, contentBytes.length, -1)
                    .contentType("application/json; charset=utf-8")
                    .build();

            minioClient.putObject(args);
            log.info("名言数据已保存到 MinIO: module={}, path={}, count={}", module, minioPath, sayings.size());
        } catch (Exception e) {
            log.error("保存名言到 MinIO 失败: module={}", module, e);
            throw new RuntimeException("保存名言失败", e);
        }
    }

    /**
     * 从 Jar 内置资源加载名言
     */
    private List<SayingResponse> loadFromClasspath(String module) {
        String fileName = MODULE_FILE_MAP.get(module);
        if (fileName == null) {
            return Collections.emptyList();
        }

        try {
            ClassPathResource resource = new ClassPathResource("static/sayings/" + fileName + ".json");
            if (!resource.exists()) {
                log.warn("内置名言资源不存在: {}", fileName);
                return Collections.emptyList();
            }

            try (InputStream inputStream = resource.getInputStream()) {
                byte[] bytes = inputStream.readAllBytes();
                List<Map<String, Object>> rawList = objectMapper.readValue(bytes,
                        new TypeReference<List<Map<String, Object>>>() {});
                return convertToResponse(rawList, module);
            }
        } catch (Exception e) {
            log.error("加载内置名言资源失败: module={}", module, e);
            return Collections.emptyList();
        }
    }

    /**
     * 将原始数据转换为响应 DTO
     */
    @SuppressWarnings("unchecked")
    private List<SayingResponse> convertToResponse(List<Map<String, Object>> rawList, String module) {
        return rawList.stream()
                .map(item -> SayingResponse.builder()
                        .id((Integer) item.get("id"))
                        .content((String) item.get("content"))
                        .author((String) item.get("author"))
                        .authorEn((String) item.getOrDefault("author_en", ""))
                        .tags((List<String>) item.getOrDefault("tags", new ArrayList<>()))
                        .module(module)
                        .build())
                .collect(Collectors.toList());
    }
}