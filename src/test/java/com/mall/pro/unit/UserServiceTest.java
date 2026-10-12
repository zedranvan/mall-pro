package com.mall.pro.unit;


import com.mall.pro.common.BusinessException;
import com.mall.pro.common.util.PasswordUtil;
import com.mall.pro.module.user.dto.LoginResponseDTO;
import com.mall.pro.module.user.dto.UserAuthDTO;
import com.mall.pro.module.user.entity.User;
import com.mall.pro.module.user.mapper.UserAttendeeMapper;
import com.mall.pro.module.user.mapper.UserMapper;
import com.mall.pro.module.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class})
public class UserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserAttendeeMapper userAttendeeMapper;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("1. 测试用户登录-用户不存在应抛出400异常")
    void testLogin_UserNotFound_ThrowsException() {
        UserAuthDTO dto = new UserAuthDTO();
        dto.setUsername("non_existent_user");
        dto.setPassword("123456");

        when(userMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.
                login(dto));
        assertEquals(400, ex.getCode());
        assertEquals("账号或密码错误！", ex.getMessage());
    }

    @Test
    @DisplayName("2. 测试用户登录-密码错误应抛出400异常")
    void testLogin_WrongPassword_ThrowsException() {
        User mockUser = User.builder()
                .id(1001L)
                .username("test_user")
                .password(PasswordUtil.encode("123456"))
                .status(0)
                .build();
        when(userMapper.selectOne(any())).thenReturn(mockUser);

        UserAuthDTO dto = new UserAuthDTO();
        dto.setUsername("test_user");
        dto.setPassword("wrong_pwd");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.
                login(dto));
        assertEquals(400, ex.getCode());
        assertEquals("账号或密码错误！", ex.getMessage());
    }
    @Test
    @DisplayName("3. 测试用户登录-密码正确成功签发Token")
    void testLogin_Success() {
        User mockUser = User.builder()
                .id(1001L)
                .username("test_user")
                .nickname("测试昵称")
                .password(PasswordUtil.encode("123456"))
                .status(0)
                .build();
        when(userMapper.selectOne(any())).thenReturn(mockUser);

        UserAuthDTO dto = new UserAuthDTO();
        dto.setUsername("test_user");
        dto.setPassword("123456");

        LoginResponseDTO res = userService.login(dto);

        assertNotNull(res);
        assertNotNull(res.getToken()); // 验证必须签发出有效 JWT
        assertEquals(1001L, res.getUserId());
    }

    @Test
    @DisplayName("4. 测试用户登录-账号被冻结应抛出403异常")
    void testLogin_AccountFrozen_ThrowsException() {
        User mockUser = User.builder()
                .id(1001L)
                .username("test_user")
                .password(PasswordUtil.encode("123456"))
                .status(1) // 👈 status=1 代表冻结
                .build();
        when(userMapper.selectOne(any())).thenReturn(mockUser);

        UserAuthDTO dto = new UserAuthDTO();
        dto.setUsername("test_user");
        dto.setPassword("123456");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.
                login(dto));
        assertEquals(403, ex.getCode());
        assertEquals("该账号已被冻结，请联系管理员！", ex.getMessage());
    }

}
