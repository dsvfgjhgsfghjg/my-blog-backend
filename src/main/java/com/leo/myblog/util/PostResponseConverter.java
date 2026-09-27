package com.leo.myblog.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.dto.response.PostDetailResponse;
import com.leo.myblog.dto.response.PostListItemResponse;
import com.leo.myblog.entity.Category;
import com.leo.myblog.entity.Post;
import com.leo.myblog.entity.PostTag;
import com.leo.myblog.entity.Tag;
import com.leo.myblog.entity.User;
import com.leo.myblog.mapper.CategoryMapper;
import com.leo.myblog.mapper.PostTagMapper;
import com.leo.myblog.mapper.TagMapper;
import com.leo.myblog.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章响应转换器
 * <p>
 * 把 {@code Post} 转成列表项响应，包含作者、**分类名**与**标签**。
 * <p>
 * ⚠️ 此处曾经只做 {@code BeanUtils.copyProperties}，不回填 {@code categoryName} 与
 * {@code tags}，于是用它的接口（{@code PostAuditService.getPendingPosts}）返回的这两个字段
 * 恒为 null —— 前端待审核页那两列因此一直是空白，且不报错、不告警，看起来就像「这些文章
 * 本来就没分类」。{@link com.leo.myblog.service.PostServiceImpl} 里另有一份同名方法，
 * 那份是回填的，两份行为不一致正是坑的来源。
 * <p>
 * 列表请一律用 {@link #convertAll(List)}：它会**批量**取分类与标签，避免每篇文章一次查询。
 * 单篇的 {@link #convertToPostListItemResponse(Post)} 只是为了兼容单个使用场景。
 */
@Component
@RequiredArgsConstructor
public class PostResponseConverter {

    private final UserCacheService userCacheService;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;

    /**
     * 将Post实体转换为PostListItemResponse（单篇）
     * <p>
     * 内部走批量实现。批量场景请直接用 {@link #convertAll(List)}，不要循环调这个方法，
     * 否则每篇都会各查一次分类与标签。
     */
    public PostListItemResponse convertToPostListItemResponse(Post post) {
        return convertAll(List.of(post)).get(0);
    }

    /**
     * 批量转换，保持入参顺序
     * <p>
     * 分类与标签各自只查一次（分类一次 {@code selectBatchIds}，标签一次查关联表
     * + 一次查标签表），与文章数量无关。
     *
     * @param posts 文章列表
     * @return 与入参**一一对应、顺序相同**的响应列表
     */
    public List<PostListItemResponse> convertAll(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        // 分类名：一次查完
        Map<Long, String> categoryNames = Collections.emptyMap();
        Set<Long> categoryIds = posts.stream()
                .map(Post::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!categoryIds.isEmpty()) {
            categoryNames = categoryMapper.selectBatchIds(categoryIds).stream()
                    .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        }

        // 标签：一次查关联 + 一次查标签
        Map<Long, List<PostDetailResponse.TagInfo>> tagsByPostId = loadTagsByPostIds(
                posts.stream().map(Post::getId).filter(Objects::nonNull).collect(Collectors.toList()));

        List<PostListItemResponse> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            result.add(convert(post, categoryNames, tagsByPostId));
        }
        return result;
    }

    /**
     * 单篇转换的实际实现，数据由调用方预先批量取好
     */
    private PostListItemResponse convert(Post post,
                                         Map<Long, String> categoryNames,
                                         Map<Long, List<PostDetailResponse.TagInfo>> tagsByPostId) {
        PostListItemResponse response = new PostListItemResponse();

        // 设置String类型的ID
        response.setId(String.valueOf(post.getId()));
        response.setUserId(String.valueOf(post.getUserId()));

        // 获取作者信息
        User author = userCacheService.getUserById(post.getUserId());
        if (author != null) {
            response.setAuthorUsername(author.getUsername());
            response.setAuthorNickname(author.getNickname());
        }

        // 复制其他属性
        BeanUtils.copyProperties(post, response, "id", "userId");

        // 设置String类型的分类ID
        if (post.getCategoryId() != null) {
            response.setCategoryId(String.valueOf(post.getCategoryId()));
            response.setCategoryName(categoryNames.get(post.getCategoryId()));
        }

        // 设置String类型的阅读量
        if (post.getViewCount() != null) {
            response.setViewCount(String.valueOf(post.getViewCount()));
        }

        // 标签：查不到就是没有标签，给空列表而不是 null —— 前端 v-for 遍历 null 会静默不渲染，
        // 空列表则语义明确
        response.setTags(tagsByPostId.getOrDefault(post.getId(), Collections.emptyList()));

        return response;
    }

    /**
     * 批量取文章标签
     *
     * @param postIds 文章ID列表
     * @return postId → 标签列表（无标签的文章不在 map 里）
     */
    private Map<Long, List<PostDetailResponse.TagInfo>> loadTagsByPostIds(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        LambdaQueryWrapper<PostTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(PostTag::getPostId, postIds);
        List<PostTag> links = postTagMapper.selectList(wrapper);

        if (links.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> tagIds = links.stream()
                .map(PostTag::getTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (tagIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Tag> tagById = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, tag -> tag, (a, b) -> a));

        Map<Long, List<PostDetailResponse.TagInfo>> result = new LinkedHashMap<>();
        for (PostTag link : links) {
            Tag tag = tagById.get(link.getTagId());
            if (tag == null) {
                continue;
            }
            PostDetailResponse.TagInfo info = new PostDetailResponse.TagInfo();
            info.setId(String.valueOf(tag.getId()));
            info.setName(tag.getName());
            info.setColor(tag.getColor());
            result.computeIfAbsent(link.getPostId(), k -> new ArrayList<>()).add(info);
        }
        return result;
    }
}
