package com.leo.myblog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leo.myblog.dto.request.CategoryRequest;
import com.leo.myblog.entity.Category;
import com.leo.myblog.entity.Post;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.CategoryMapper;
import com.leo.myblog.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 分类服务（后台维护用）
 * <p>
 * 读接口沿用 {@code PostController#getAllCategories}（{@code GET /api/posts/categories}，
 * 已按 sort_order 升序，前端 admin 页面已经在调），这里只补增删改。
 * <p>
 * 两处刻意的选择，理由见各自方法上的注释：
 * <ul>
 *   <li>名称唯一性：库里已有 {@code t_category_name_key} 唯一约束，service 层查重只是为了给出
 *       可读文案；并发下真正兜底的是那条约束。</li>
 *   <li>删除：分类下还有文章时<b>拒绝</b>，不做「移到未分类」。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    /** 排序值缺省（对齐 t_category.sort_order 的 DEFAULT 0） */
    private static final int DEFAULT_SORT_ORDER = 0;

    private final CategoryMapper categoryMapper;
    private final PostMapper postMapper;

    /**
     * 新建分类
     *
     * @return 新分类ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createCategory(CategoryRequest request) {
        String name = normalizeName(request.getName());
        ensureNameAvailable(name, null);

        Category category = new Category();
        category.setName(name);
        category.setDescription(normalizeDescription(request.getDescription()));
        category.setSortOrder(request.getSortOrder() != null
                ? request.getSortOrder() : DEFAULT_SORT_ORDER);

        LocalDateTime now = LocalDateTime.now();
        category.setCreateTime(now);
        category.setUpdateTime(now);

        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException e) {
            // 两个请求同时通过了上面的查重时，唯一约束是最后的守门人。
            // 转成和查重失败同一句话，免得前端收到 409「数据已存在，请勿重复提交」这种对不上号的文案。
            log.warn("并发创建同名分类，被唯一约束拦下: name={}", name);
            throw new BusinessException("分类名称已存在");
        }

        log.info("分类创建成功: categoryId={}, name={}", category.getId(), name);
        return category.getId();
    }

    /**
     * 修改分类（name / description / sortOrder）
     * <p>
     * {@code sortOrder} 为 null 表示「不动原值」：前端编辑表单只改描述时漏传排序，
     * 不应该把排序悄悄清成 0。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(Long categoryId, CategoryRequest request) {
        Category category = requireCategory(categoryId);

        String name = normalizeName(request.getName());
        if (!name.equals(category.getName())) {
            // 只有真的改名才查重：否则「改描述但不改名」会被自己这行挡住
            ensureNameAvailable(name, categoryId);
            log.info("分类改名: categoryId={}, oldName={}, newName={}",
                    categoryId, category.getName(), name);
        }

        category.setName(name);
        category.setDescription(normalizeDescription(request.getDescription()));
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        category.setUpdateTime(LocalDateTime.now());

        try {
            categoryMapper.updateById(category);
        } catch (DuplicateKeyException e) {
            log.warn("并发改名撞上唯一约束: categoryId={}, name={}", categoryId, name);
            throw new BusinessException("分类名称已存在");
        }

        log.info("分类更新成功: categoryId={}, name={}", categoryId, name);
    }

    /**
     * 删除分类
     * <p>
     * <b>分类下还有文章时拒绝删除</b>，而不是把文章挪到「未分类」。理由：
     * <ol>
     *   <li>与既有先例一致：{@code TagServiceImpl#deleteTag} 同样在标签被引用时拒绝并给出条数。</li>
     *   <li>爆炸半径不对称：误删一个分类会让一批文章静默失去归类，而让管理员多点一次「先移出文章」
     *       几乎没有成本。</li>
     *   <li>「移到未分类」缺少产品定义：{@code t_post.category_id} 虽然可空，但究竟是置 NULL 还是
     *       挪到 id 未知的那行「未分类」，以及公开列表/NULL 归类怎么展示，都得先定清楚 ——
     *       这些不该由这一次改动替产品拍板。</li>
     *   <li>数据库本来也不允许：{@code fk_post_category} 没有 ON DELETE 子句，
     *       DELETE 会被外键挡下。这里提前把它翻译成可读文案（否则前端只看到「系统繁忙」）。</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long categoryId) {
        Category category = requireCategory(categoryId);

        // 1. 还在用的文章：给管理员一条能照着做的提示
        Long activeCount = postMapper.selectCount(
                new LambdaQueryWrapper<Post>().eq(Post::getCategoryId, categoryId));
        if (activeCount != null && activeCount > 0) {
            log.warn("分类下仍有 {} 篇文章，拒绝删除: categoryId={}, name={}",
                    activeCount, categoryId, category.getName());
            throw new BusinessException(String.format(
                    "该分类下还有 %d 篇文章，无法删除。请先将这些文章移到其他分类。", activeCount));
        }

        // 2. 逻辑删除的文章：列表里看不见，但行还在、外键还指着这个分类。
        //    不单独拦一道的话，删除会以一条外键冲突 SQL 异常收场。
        long totalCount = categoryMapper.countPostsIncludingDeleted(categoryId);
        if (totalCount > 0) {
            log.warn("分类被已删除的文章占用，拒绝删除: categoryId={}, count={}", categoryId, totalCount);
            throw new BusinessException(String.format(
                    "该分类下还有 %d 篇已删除的文章占用该分类，无法删除。", totalCount));
        }

        categoryMapper.deleteById(categoryId);
        log.info("分类删除成功: categoryId={}, name={}", categoryId, category.getName());
    }

    /**
     * 取分类，不存在直接报错（避免后续以 null 继续走下去）
     */
    private Category requireCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BusinessException("分类ID不能为空");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        return category;
    }

    /**
     * 名称查重
     * <p>
     * 库里已有唯一约束 {@code t_category_name_key}，所以这里<b>不是</b>最终保证，只是一次
     * 提前的、能给出中文提示的检查。两个并发请求可以同时通过这里，最后靠约束拒绝其中一个；
     * 调用方下面的 catch(DuplicateKeyException) 负责把那条也翻译成同一句话。
     * <p>
     * 比较是大小写敏感的（和数据库约束一致）：所以 {@code AI} 与 {@code ai} 在库层面是两个分类。
     *
     * @param excludeId 需要排除的分类ID（改名时排除自己），新建传 null
     */
    private void ensureNameAvailable(String name, Long excludeId) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getName, name);
        if (excludeId != null) {
            wrapper.ne(Category::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("分类名称已存在");
        }
    }

    /**
     * 名称规整：去首尾空白
     * <p>
     * 不做这一步，「软件开发 」和「软件开发」会各自占一行 —— 唯一约束是逐字节比较，拦不住。
     * 而公开分类页是按名称精确查的（{@code getPublicPostsByCategoryName}），多一个空格就等于查不到。
     * <p>
     * 控制器侧的 {@code @NotBlank} 已经挡住全空白，这里再挡一次是给非 HTTP 调用方兜底。
     */
    private String normalizeName(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("分类名称不能为空");
        }
        return trimmed;
    }

    /**
     * 描述规整：去首尾空白，空串统一存 NULL（避免 "" 和 NULL 两种「没有描述」并存）
     */
    private String normalizeDescription(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
