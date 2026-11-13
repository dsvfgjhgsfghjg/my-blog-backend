package com.leo.myblog.service;

import com.leo.myblog.dto.response.InviteCodeResponse;
import com.leo.myblog.entity.InviteCode;
import com.leo.myblog.entity.User;
import com.leo.myblog.exception.ForbiddenException;
import com.leo.myblog.mapper.InviteCodeMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InviteCodeServiceTest {

    @Test
    void superAdminCanGenerateOneTimeCodeAndOnlyHashIsStored() {
        InviteCodeMapper mapper = mock(InviteCodeMapper.class);
        UserCacheService users = mock(UserCacheService.class);
        User admin = new User();
        admin.setIsAdmin(true);
        admin.setIsSuperAdmin(true);
        when(users.getUserById(1L)).thenReturn(admin);

        InviteCodeService service = new InviteCodeService(mapper, users);
        InviteCodeResponse response = service.generate(1L);

        assertNotNull(response.getCode());
        assertEquals(22, response.getCode().length());
        ArgumentCaptor<InviteCode> captor = ArgumentCaptor.forClass(InviteCode.class);
        verify(mapper).insert(captor.capture());
        assertEquals(64, captor.getValue().getCodeHash().length());
        assertEquals(1L, captor.getValue().getCreatedBy());
    }

    @Test
    void ordinaryAdminCannotGenerateCodes() {
        InviteCodeMapper mapper = mock(InviteCodeMapper.class);
        UserCacheService users = mock(UserCacheService.class);
        User admin = new User();
        admin.setIsAdmin(true);
        admin.setIsSuperAdmin(false);
        when(users.getUserById(2L)).thenReturn(admin);

        InviteCodeService service = new InviteCodeService(mapper, users);
        assertThrows(ForbiddenException.class, () -> service.generate(2L));
    }
}
