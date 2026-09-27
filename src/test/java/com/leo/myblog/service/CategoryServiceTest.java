package com.leo.myblog.service;

import com.leo.myblog.dto.request.CategoryRequest;
import com.leo.myblog.entity.Category;
import com.leo.myblog.exception.BusinessException;
import com.leo.myblog.mapper.CategoryMapper;
import com.leo.myblog.mapper.PostMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分类服务测试
 * <p>
 * 纯 Mockito 单测，不需要 PostgreSQL / Redis（沿用 {@code NotificationServiceTest} 的写法）。
 * <p>
 * 只钉住那些「错了会很难受」的规则：重名、分类下还有文章时不许删、
 * 排序字段缺省时不覆盖原值、以及库里的唯一约束被撞上时给出的文案。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryServiceTest {

    /** 建表语句里「软件开发」这一行的样子（V7__add_main_categories.sql） */
    private static final Long SOFTWARE_ID = 4L;

    @Mock private CategoryMapper categoryMapper;
    @Mock private PostMapper postMapper;

    @InjectMocks private CategoryService categoryService;

    private Category existingCategory() {
        Category category = new Category();
        category.setId(SOFTWARE_ID);
        category.setName("软件开发");
        category.setDescription("软件开发相关文章");
        category.setSortOrder(3);
        category.setCreateTime(LocalDateTime.now().minusDays(30));
        category.setUpdateTime(LocalDateTime.now().minusDays(1));
        return category;
    }

    private CategoryRequest request(String name, String description, Integer sortOrder) {
        CategoryRequest request = new CategoryRequest();
        request.setName(name);
        request.setDescription(description);
        request.setSortOrder(sortOrder);
        return request;
    }

    @Nested
    @DisplayName("新建")
    class Create {

        @Test
        @DisplayName("正常创建：名称与描述去首尾空白，排序缺省落 0，两个时间都写上")
        void createsWithDefaults() {
            when(categoryMapper.selectCount(any())).thenReturn(0L);
            when(categoryMapper.insert(any())).thenAnswer(inv -> {
                Category saved = inv.getArgument(0);
                saved.setId(SOFTWARE_ID);
                return 1;
            });

            Long id = categoryService.createCategory(
                    request("  软件开发  ", "  软件开发相关文章  ", null));

            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryMapper).insert(captor.capture());
            Category saved = captor.getValue();

            assertEquals(SOFTWARE_ID, id);
            assertEquals("软件开发", saved.getName(), "名称必须去掉首尾空白");
            assertEquals("软件开发相关文章", saved.getDescription());
            assertEquals(0, saved.getSortOrder(), "排序缺省应对齐 t_category.sort_order 的 DEFAULT 0");
            assertNotNull(saved.getCreateTime());
            assertNotNull(saved.getUpdateTime());
        }

        @Test
        @DisplayName("显式排序值按原样落库，不被缺省值覆盖")
        void keepsExplicitSortOrder() {
            when(categoryMapper.selectCount(any())).thenReturn(0L);
            when(categoryMapper.insert(any())).thenReturn(1);

            categoryService.createCategory(request("AI", "人工智能", 4));

            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryMapper).insert(captor.capture());
            assertEquals(4, captor.getValue().getSortOrder());
        }

        @Test
        @DisplayName("描述为空串时存 NULL，不留两种「没有描述」")
        void blankDescriptionBecomesNull() {
            when(categoryMapper.selectCount(any())).thenReturn(0L);
            when(categoryMapper.insert(any())).thenReturn(1);

            categoryService.createCategory(request("AI", "   ", null));

            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryMapper).insert(captor.capture());
            assertNull(captor.getValue().getDescription());
        }

        @Test
        @DisplayName("重名被拒，且不会写库")
        void rejectsDuplicateName() {
            when(categoryMapper.selectCount(any())).thenReturn(1L);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> categoryService.createCategory(request("AI", "人工智能", null)));

            assertEquals("分类名称已存在", e.getMessage());
            verify(categoryMapper, never()).insert(any());
        }

        @Test
        @DisplayName("并发下撞上唯一约束，也翻译成同一句可读文案")
        void translatesUniqueViolation() {
            // 查重与插入之间有窗口期，最后拦住的是数据库的 t_category_name_key
            when(categoryMapper.selectCount(any())).thenReturn(0L);
            when(categoryMapper.insert(any()))
                    .thenThrow(new DuplicateKeyException("duplicate key value violates unique constraint \"t_category_name_key\""));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> categoryService.createCategory(request("AI", "人工智能", null)));

            assertEquals("分类名称已存在", e.getMessage());
        }

        @Test
        @DisplayName("全空白名称在 service 层也被挡下（非 HTTP 调用方的兜底）")
        void rejectsBlankName() {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> categoryService.createCategory(request("   ", "x", null)));

            assertEquals("分类名称不能为空", e.getMessage());
            verify(categoryMapper, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("修改")
    class Update {

        @Test
        @DisplayName("改名正常路径：新名落库、时间戳刷新、排序保持原值")
        void renamesAndKeepsSortOrderWhenNull() {
            Category existing = existingCategory();
            when(categoryMapper.selectById(SOFTWARE_ID)).thenReturn(existing);
            when(categoryMapper.selectCount(any())).thenReturn(0L);

            categoryService.updateCategory(SOFTWARE_ID,
                    request("软件开发技术", "新的描述", null));

            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryMapper).updateById(captor.capture());
            Category saved = captor.getValue();

            assertEquals("软件开发技术", saved.getName());
            assertEquals("新的描述", saved.getDescription());
            assertEquals(3, saved.getSortOrder(), "排序字段缺省时应保留原值，不能被清零");
            assertTrue(saved.getUpdateTime().isAfter(LocalDateTime.now().minusMinutes(1)));
        }

        @Test
        @DisplayName("显式排序值会覆盖原值")
        void appliesExplicitSortOrder() {
            when(categoryMapper.selectById(SOFTWARE_ID)).thenReturn(existingCategory());
            when(categoryMapper.selectCount(any())).thenReturn(0L);

            categoryService.updateCategory(SOFTWARE_ID, request("软件开发", "描述", 9));

            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryMapper).updateById(captor.capture());
            assertEquals(9, captor.getValue().getSortOrder());
        }

        @Test
        @DisplayName("没改名时不查重：否则「只改描述」会被自己那一行挡住")
        void skipsDuplicateCheckWhenNameUnchanged() {
            when(categoryMapper.selectById(SOFTWARE_ID)).thenReturn(existingCategory());

            categoryService.updateCategory(SOFTWARE_ID, request("软件开发", "只改描述", 3));

            verify(categoryMapper, never()).selectCount(any());
            verify(categoryMapper).updateById(any());
        }

        @Test
        @DisplayName("改成别人已占用的名称被拒，且不写库")
        void rejectsNameTakenByAnother() {
            when(categoryMapper.selectById(SOFTWARE_ID)).thenReturn(existingCategory());
            when(categoryMapper.selectCount(any())).thenReturn(1L);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> categoryService.updateCategory(SOFTWARE_ID, request("AI", "描述", null)));

            assertEquals("分类名称已存在", e.getMessage());
            verify(categoryMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("并发改名撞上唯一约束，同样给出可读文案")
        void translatesUniqueViolationOnRename() {
            when(categoryMapper.selectById(SOFTWARE_ID)).thenReturn(existingCategory());
            when(categoryMapper.selectCount(any())).thenReturn(0L);
            when(categoryMapper.updateById(any())).thenThrow(new DuplicateKeyException("dup"));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> categoryService.updateCategory(SOFTWARE_ID, request("AI", "描述", null)));

            assertEquals("分类名称已存在", e.getMessage());
        }

        @Test
        @DisplayName("分类不存在时报错，不去写库")
        void rejectsUnknownCategory() {
            when(categoryMapper.selectById(anyLong())).thenReturn(null);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> categoryService.updateCategory(999L, request("AI", "描述", null)));

            assertEquals("分类不存在", e.getMessage());
            verify(categoryMapper, never()).updateById(any());
        }
    }

    @Nested
    @DisplayName("删除")
    class Delete {

        @Test
        @DisplayName("分类下还有文章时拒绝删除，错误信息带上条数")
        void rejectsWhenPostsStillUseIt() {
            when(categoryMapper.selectById(SOFTWARE_ID)).thenReturn(existingCategory());
            when(postMapper.selectCount(any())).thenReturn(2L);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> categoryService.deleteCategory(SOFTWARE_ID));

            assertEquals("该分类下还有 2 篇文章，无法删除。请先将这些文章移到其他分类。", e.getMessage());
            verify(categoryMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("只剩逻辑删除的文章时也拒绝：行还在，外键还指着这个分类")
        void rejectsWhenOnlySoftDeletedPostsRemain() {
            // PostMapper 被 @TableLogic 追加了 is_deleted = false，所以查出来是 0；
            // 但 t_post 里那些行还在，数据库的 fk_post_category 不会放行 DELETE。
            when(categoryMapper.selectById(SOFTWARE_ID)).thenReturn(existingCategory());
            when(postMapper.selectCount(any())).thenReturn(0L);
            when(categoryMapper.countPostsIncludingDeleted(SOFTWARE_ID)).thenReturn(3L);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> categoryService.deleteCategory(SOFTWARE_ID));

            assertEquals("该分类下还有 3 篇已删除的文章占用该分类，无法删除。", e.getMessage());
            verify(categoryMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("没有任何文章引用时正常删除")
        void deletesWhenUnused() {
            when(categoryMapper.selectById(SOFTWARE_ID)).thenReturn(existingCategory());
            when(postMapper.selectCount(any())).thenReturn(0L);
            when(categoryMapper.countPostsIncludingDeleted(SOFTWARE_ID)).thenReturn(0L);

            categoryService.deleteCategory(SOFTWARE_ID);

            verify(categoryMapper).deleteById(SOFTWARE_ID);
        }

        @Test
        @DisplayName("分类不存在时报错")
        void rejectsUnknownCategory() {
            when(categoryMapper.selectById(anyLong())).thenReturn(null);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> categoryService.deleteCategory(999L));

            assertEquals("分类不存在", e.getMessage());
            verify(categoryMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("未删除文章与软删文章都要算上，不能只看其中一边")
        void countsBothTables() {
            // 这条用例的用意是防回归：将来若有人把 countPostsIncludingDeleted 删掉、
            // 只留 postMapper.selectCount，软删行就会漏掉，删除会以 SQL 异常收场
            when(categoryMapper.selectById(SOFTWARE_ID)).thenReturn(existingCategory());
            when(postMapper.selectCount(any())).thenReturn(0L);
            when(categoryMapper.countPostsIncludingDeleted(SOFTWARE_ID)).thenReturn(1L);

            assertThrows(BusinessException.class, () -> categoryService.deleteCategory(SOFTWARE_ID));

            verify(categoryMapper).countPostsIncludingDeleted(SOFTWARE_ID);
        }
    }
}
