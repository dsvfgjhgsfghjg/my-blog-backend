package com.leo.myblog.controller;

import com.leo.myblog.annotation.AuditLog;
import com.leo.myblog.annotation.RequireAdmin;
import com.leo.myblog.annotation.RequireTotp;
import com.leo.myblog.aspect.audit.AuditMetadataResolver;
import com.leo.myblog.common.AuditAction;
import com.leo.myblog.common.AuditModule;
import com.leo.myblog.dto.request.CategoryRequest;
import com.leo.myblog.enums.AuditLevel;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.exception.GlobalExceptionHandler;
import com.leo.myblog.service.CategoryService;
import com.leo.myblog.util.AbilityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分类管理控制器测试
 * <p>
 * standalone MockMvc（不起 Spring 容器），所以不依赖 PostgreSQL / Redis；挂上真实的
 * {@link GlobalExceptionHandler} 与 JSR-303 校验器，让「参数非法」「业务异常」走线上同一套响应格式。
 * <p>
 * 注意本项目异常处理器都返回 HTTP 200，错误体现在 body 的 {@code code} 里，所以断言 {@code $.code}。
 * <p>
 * {@code @RequireAdmin} / {@code @RequireTotp} 是切面，standalone 环境下不会织入，
 * 它们的存在性与取值单独放在「注解契约」里用反射核对。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryAdminControllerTest {

    private static final Long CURRENT_USER_ID = 3003L;
    private static final Long CATEGORY_ID = 4L;
    private static final String BASE = "/api/admin/categories";

    @Mock private CategoryService categoryService;
    @Mock private AbilityValidator abilityValidator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        CategoryAdminController controller =
                new CategoryAdminController(categoryService, abilityValidator);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Nested
    @DisplayName("新建分类")
    class Create {

        @Test
        @DisplayName("正常创建：返回新ID，能力校验用的是 edit_posts")
        void createsCategory() throws Exception {
            when(categoryService.createCategory(any())).thenReturn(CATEGORY_ID);

            mockMvc.perform(post(BASE)
                            .requestAttr("userId", CURRENT_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"AI\",\"description\":\"人工智能\",\"sortOrder\":4}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("分类创建成功"))
                    .andExpect(jsonPath("$.data").value(CATEGORY_ID));

            verify(categoryService).createCategory(any(CategoryRequest.class));
            verify(abilityValidator).validateAbility(CURRENT_USER_ID, "edit_posts");
        }

        @Test
        @DisplayName("名称为空被参数校验挡下，不会进到 service")
        void rejectsMissingName() throws Exception {
            mockMvc.perform(post(BASE)
                            .requestAttr("userId", CURRENT_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"description\":\"没有名称\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("请求参数无效"));

            verify(categoryService, never()).createCategory(any());
        }

        @Test
        @DisplayName("名称超过 50（列宽）被拒")
        void rejectsTooLongName() throws Exception {
            String tooLong = "a".repeat(51);

            mockMvc.perform(post(BASE)
                            .requestAttr("userId", CURRENT_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + tooLong + "\"}"))
                    .andExpect(jsonPath("$.code").value(400));

            verify(categoryService, never()).createCategory(any());
        }

        @Test
        @DisplayName("描述超过 255（列宽）被拒")
        void rejectsTooLongDescription() throws Exception {
            String tooLong = "d".repeat(256);

            mockMvc.perform(post(BASE)
                            .requestAttr("userId", CURRENT_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"AI\",\"description\":\"" + tooLong + "\"}"))
                    .andExpect(jsonPath("$.code").value(400));

            verify(categoryService, never()).createCategory(any());
        }

        @Test
        @DisplayName("负数排序值被拒（负值等价于没定义过的「置顶」）")
        void rejectsNegativeSortOrder() throws Exception {
            mockMvc.perform(post(BASE)
                            .requestAttr("userId", CURRENT_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"AI\",\"sortOrder\":-1}"))
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("请求参数无效"));

            verify(categoryService, never()).createCategory(any());
        }

        @Test
        @DisplayName("排序值不是数字时按请求体解析失败处理（不是悄悄当 0）")
        void rejectsNonNumericSortOrder() throws Exception {
            mockMvc.perform(post(BASE)
                            .requestAttr("userId", CURRENT_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"AI\",\"sortOrder\":\"abc\"}"))
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("请求数据格式错误，请检查JSON格式"));

            verify(categoryService, never()).createCategory(any());
        }

        @Test
        @DisplayName("重名时把 service 的业务异常原样透出")
        void surfacesDuplicateName() throws Exception {
            when(categoryService.createCategory(any()))
                    .thenThrow(new BusinessException("分类名称已存在"));

            mockMvc.perform(post(BASE)
                            .requestAttr("userId", CURRENT_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"AI\"}"))
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("分类名称已存在"));
        }
    }

    @Nested
    @DisplayName("修改分类")
    class Update {

        @Test
        @DisplayName("正常改名：路径ID与请求体都传到了 service，能力校验用 edit_posts")
        void updatesCategory() throws Exception {
            mockMvc.perform(put(BASE + "/{categoryId}", CATEGORY_ID)
                            .requestAttr("userId", CURRENT_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"软件开发技术\",\"description\":\"新描述\",\"sortOrder\":3}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("分类更新成功"));

            verify(categoryService).updateCategory(eq(CATEGORY_ID), any(CategoryRequest.class));
            verify(abilityValidator).validateAbility(CURRENT_USER_ID, "edit_posts");
        }

        @Test
        @DisplayName("名称为空被参数校验挡下")
        void rejectsBlankName() throws Exception {
            mockMvc.perform(put(BASE + "/{categoryId}", CATEGORY_ID)
                            .requestAttr("userId", CURRENT_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"   \"}"))
                    .andExpect(jsonPath("$.code").value(400));

            verify(categoryService, never()).updateCategory(any(), any());
        }

        @Test
        @DisplayName("分类不存在时把错误透出")
        void surfacesUnknownCategory() throws Exception {
            doThrow(new BusinessException("分类不存在"))
                    .when(categoryService).updateCategory(eq(999L), any());

            mockMvc.perform(put(BASE + "/{categoryId}", 999L)
                            .requestAttr("userId", CURRENT_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"AI\"}"))
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("分类不存在"));
        }
    }

    @Nested
    @DisplayName("删除分类")
    class Delete {

        @Test
        @DisplayName("正常删除：能力校验用 delete_posts（删除权限需单独授予）")
        void deletesCategory() throws Exception {
            mockMvc.perform(delete(BASE + "/{categoryId}", CATEGORY_ID)
                            .requestAttr("userId", CURRENT_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("分类删除成功"));

            verify(categoryService).deleteCategory(CATEGORY_ID);
            verify(abilityValidator).validateAbility(CURRENT_USER_ID, "delete_posts");
        }

        @Test
        @DisplayName("分类下有文章时拒绝删除，文案带条数")
        void refusesWhenPostsExist() throws Exception {
            doThrow(new BusinessException("该分类下还有 2 篇文章，无法删除。请先将这些文章移到其他分类。"))
                    .when(categoryService).deleteCategory(CATEGORY_ID);

            mockMvc.perform(delete(BASE + "/{categoryId}", CATEGORY_ID)
                            .requestAttr("userId", CURRENT_USER_ID))
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message")
                            .value("该分类下还有 2 篇文章，无法删除。请先将这些文章移到其他分类。"));
        }
    }

    /**
     * 切面在 standalone 环境不生效，所以这里用反射把「注解有没有、取值对不对」钉住 ——
     * 少一个 {@code @RequireAdmin} 就是一个未授权入口，靠人眼 review 不保险。
     */
    @Nested
    @DisplayName("注解契约")
    class Annotations {

        @Test
        @DisplayName("增/改/删都带 @RequireAdmin 与 @RequireTotp（模块=文章管理）")
        void writeEndpointsAreGuarded() throws Exception {
            for (Method method : writeMethods()) {
                assertNotNull(method.getAnnotation(RequireAdmin.class),
                        method.getName() + " 缺少 @RequireAdmin，会变成未授权入口");

                RequireTotp totp = method.getAnnotation(RequireTotp.class);
                assertNotNull(totp, method.getName() + " 缺少 @RequireTotp");
                assertEquals("security.totp.post_management", totp.module(),
                        method.getName() + " 的 TOTP 模块键与前端 services 里用的必须一致");
            }
        }

        @Test
        @DisplayName("审计元数据钉住 CATEGORY 模块；删除是 WARNING 级")
        void auditMetadataIsExplicit() throws Exception {
            // 不写 @AuditLog 的话模块会兜底成类名转换的 CATEGORY_ADMIN，审计页就多出一个陌生筛选项
            assertEquals(AuditModule.CATEGORY,
                    createMethod().getAnnotation(AuditLog.class).module());
            assertEquals(AuditModule.CATEGORY,
                    updateMethod().getAnnotation(AuditLog.class).module());
            assertEquals(AuditModule.CATEGORY,
                    deleteMethod().getAnnotation(AuditLog.class).module());

            assertEquals(AuditAction.CREATE, createMethod().getAnnotation(AuditLog.class).action());
            assertEquals(AuditAction.UPDATE, updateMethod().getAnnotation(AuditLog.class).action());
            assertEquals(AuditAction.DELETE, deleteMethod().getAnnotation(AuditLog.class).action());

            assertEquals(AuditLevel.NOTICE, levelOf(createMethod()), "创建 = 记录级");
            assertEquals(AuditLevel.NOTICE, levelOf(updateMethod()), "修改 = 记录级");
            assertEquals(AuditLevel.WARNING, levelOf(deleteMethod()), "删除 = 警告级");
        }

        @Test
        @DisplayName("描述模板渲染出人话：分类名与ID都落到位")
        void descriptionsRenderReadableText() throws Exception {
            CategoryRequest createReq = new CategoryRequest();
            createReq.setName("软件开发");
            assertEquals("新建分类「软件开发」",
                    resolve(createMethod(), CURRENT_USER_ID, createReq).description());

            CategoryRequest updateReq = new CategoryRequest();
            updateReq.setName("软件开发技术");
            assertEquals("修改分类 4 为「软件开发技术」",
                    resolve(updateMethod(), CURRENT_USER_ID, CATEGORY_ID, updateReq).description());

            assertEquals("删除分类 4",
                    resolve(deleteMethod(), CURRENT_USER_ID, CATEGORY_ID).description());
        }

        private Method createMethod() throws Exception {
            return CategoryAdminController.class.getMethod(
                    "createCategory", Long.class, CategoryRequest.class);
        }

        private Method updateMethod() throws Exception {
            return CategoryAdminController.class.getMethod(
                    "updateCategory", Long.class, Long.class, CategoryRequest.class);
        }

        private Method deleteMethod() throws Exception {
            return CategoryAdminController.class.getMethod(
                    "deleteCategory", Long.class, Long.class);
        }

        private Method[] writeMethods() throws Exception {
            return new Method[]{createMethod(), updateMethod(), deleteMethod()};
        }

        private AuditLevel levelOf(Method method) {
            return AuditMetadataResolver.resolve(method, new Object[]{CURRENT_USER_ID},
                    paramNames(method), method.getParameterAnnotations(),
                    CategoryAdminController.class, "POST", BASE).baseLevel();
        }

        private AuditMetadataResolver.AuditMetadata resolve(Method method, Object... args) {
            return AuditMetadataResolver.resolve(method, args, paramNames(method),
                    method.getParameterAnnotations(), CategoryAdminController.class, "POST", BASE);
        }

        /** 复刻 AspectJ MethodSignature.getParameterNames()：依赖编译期 -parameters */
        private String[] paramNames(Method method) {
            java.lang.reflect.Parameter[] params = method.getParameters();
            String[] names = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                names[i] = params[i].getName();
            }
            return names;
        }
    }
}
