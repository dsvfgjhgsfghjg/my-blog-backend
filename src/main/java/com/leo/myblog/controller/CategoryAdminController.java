package com.leo.myblog.controller;

import com.leo.myblog.annotation.AuditLog;
import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.annotation.RequireTotp;
import com.leo.myblog.common.AuditAction;
import com.leo.myblog.common.AuditModule;
import com.leo.myblog.common.Result;
import com.leo.myblog.dto.request.CategoryRequest;
import com.leo.myblog.service.CategoryService;
import com.leo.myblog.util.AbilityValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分类管理控制器（管理员专用）
 * <p>
 * <b>没有 GET 列表</b>：读接口沿用 {@code GET /api/posts/categories}
 * （{@code PostController#getAllCategories}）。它已经按 {@code sort_order} 升序返回全部分类，
 * 且 {@code SecurityConfig} 把它标成 authenticated（后台页必然在登录态下），
 * 前端 admin 文章管理页也已经在调它 —— 再开一个「管理员版列表」只会多一份要同步维护的契约。
 * 管理后台需要「某分类下的文章」，那是文章查询的职责，走 {@code POST /api/posts/admin/list}
 * （{@code GetPostListRequest} 自带 categoryId 筛选），同样不必在这里重复造。
 * <p>
 * 写接口的三件套用法（对照 {@link TagController}，标签与分类同属「内容分类法」维护）：
 * <ul>
 *   <li>{@code @RequireAdmin} —— 后台维护接口，只对管理员开放。</li>
 *   <li>{@code @RequireTotp} —— 增/改/删<b>都要</b>二次验证。分类改名会直接影响前台三个固定分类页
 *       的取数（它们按名称查，见 {@code getPublicPostsByCategoryName}），属于「改内容结构」的操作，
 *       和标签管理一样按敏感操作对待；模块开关交给系统配置，管理员可整体关掉。</li>
 *   <li>{@code @AuditLog} —— 显式钉住 module/action/description。不写的话模块会由类名兜底成
 *       {@code CATEGORY_ADMIN}（{@code AuditModuleResolver} 的映射表里没有这个类名），
 *       审计页的模块筛选就多出一个谁都不认识的值。等级不用在这里写，由 {@code AuditAction} 推出：
 *       增/改=NOTICE，删=WARNING。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
public class CategoryAdminController {

    /**
     * TOTP 模块配置键：复用文章管理的开关。
     * <p>
     * 分类是文章的组织方式，后台入口也挂在文章/页面管理下；{@code security.totp.*} 的键集与
     * {@code sql/init_system_config.sql} 里的行一一对应，新建 {@code category_management} 键要连带
     * 改初始化脚本与线上库。代价是关掉「文章管理」的二次验证会同时关掉分类的 —— 若团队要独立开关，
     * 需要新增配置键（三处：init_system_config.sql、系统设置页、前端 services 常量）。
     */
    private static final String TOTP_MODULE = "security.totp.post_management";

    private final CategoryService categoryService;
    private final AbilityValidator abilityValidator;

    /**
     * 新建分类（管理员 + edit_posts 能力 + TOTP）
     * <p>
     * 能力用的是 {@code edit_posts}：能力名是一份封闭清单（{@code AbilityValidator} 的描述表、
     * 前端 {@code utils/permissions.ts}、WebSocket 下发的清单三处同步），没有 {@code edit_categories}，
     * 而分类是文章元数据、入口就在文章管理域内，借用文章能力比新开一个能力更划算。
     */
    @RequireTotp(operation = "创建分类", module = TOTP_MODULE)
    @RequireAdmin
    @AuditLog(module = AuditModule.CATEGORY, action = AuditAction.CREATE,
            description = "新建分类「{request.name}」")
    @PostMapping
    public Result<Long> createCategory(@RequestAttribute Long userId,
                                      @Valid @RequestBody CategoryRequest request) {
        log.info("创建分类: userId={}, name={}", userId, request.getName());
        abilityValidator.validateAbility(userId, "edit_posts");
        Long categoryId = categoryService.createCategory(request);
        return Result.success("分类创建成功", categoryId);
    }

    /**
     * 修改分类（管理员 + edit_posts 能力 + TOTP）
     */
    @RequireTotp(operation = "修改分类", module = TOTP_MODULE)
    @RequireAdmin
    @AuditLog(module = AuditModule.CATEGORY, action = AuditAction.UPDATE,
            description = "修改分类 {categoryId} 为「{request.name}」")
    @PutMapping("/{categoryId}")
    public Result<Void> updateCategory(@RequestAttribute Long userId,
                                       @PathVariable Long categoryId,
                                       @Valid @RequestBody CategoryRequest request) {
        log.info("修改分类: userId={}, categoryId={}, name={}", userId, categoryId, request.getName());
        abilityValidator.validateAbility(userId, "edit_posts");
        categoryService.updateCategory(categoryId, request);
        return Result.success("分类更新成功", null);
    }

    /**
     * 删除分类（管理员 + delete_posts 能力 + TOTP）
     * <p>
     * 删除用的是 {@code delete_posts} 而不是 {@code edit_posts}：删东西的能力应当单独授予，
     * 与 {@code PostController#deletePost} 的取舍一致（那边也是编辑=edit_posts、删除=delete_posts）。
     * <p>
     * 分类下还有文章时不会真的删掉，见 {@code CategoryService#deleteCategory}。
     */
    @RequireTotp(operation = "删除分类", module = TOTP_MODULE)
    @RequireAdmin
    @AuditLog(module = AuditModule.CATEGORY, action = AuditAction.DELETE,
            description = "删除分类 {categoryId}")
    @DeleteMapping("/{categoryId}")
    public Result<Void> deleteCategory(@RequestAttribute Long userId,
                                       @PathVariable Long categoryId) {
        log.info("删除分类: userId={}, categoryId={}", userId, categoryId);
        abilityValidator.validateAbility(userId, "delete_posts");
        categoryService.deleteCategory(categoryId);
        return Result.success("分类删除成功", null);
    }
}
