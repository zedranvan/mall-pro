package com.mall.pro.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.pro.common.BusinessException;
import com.mall.pro.common.util.IdCardSecurityUtil;
import com.mall.pro.common.util.JWTUtil;
import com.mall.pro.common.util.PasswordUtil;
import com.mall.pro.common.util.UserContext;
import com.mall.pro.module.user.dto.LoginResponseDTO;
import com.mall.pro.module.user.dto.UserAuthDTO;
import com.mall.pro.module.user.entity.User;
import com.mall.pro.module.user.entity.UserAttendee;
import com.mall.pro.module.user.mapper.UserAttendeeMapper;
import com.mall.pro.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserAttendeeMapper userAttendeeMapper;

    /**
    * 1. 用户注册 (账号排他 + 密码 BCrypt 加密)
     */
    @Transactional(rollbackFor = Exception.class)
    public User register(UserAuthDTO dto) {
        if(!StringUtils.hasText(dto.getUsername())||!StringUtils.hasText(dto.getPassword())){
            throw new BusinessException(400, "用户名和密码不能为空！");
        }
        // 校验用户名唯一性
        Long count = userMapper.selectCount(new
                LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException(400, "该用户名已被注册！");
        }

        // 校验手机号唯一性
        if (StringUtils.hasText(dto.getPhone()))
        {
            Long phoneCount = userMapper
                    .selectCount(new LambdaQueryWrapper<User>()
                            .eq(User::getPhone, dto.getPhone()));

            if (phoneCount > 0) {
                throw new BusinessException(400, "该手机号已被注册！");
            }
        }

        User user = User.builder()
                .username(dto.getUsername())
                .phone(dto.getPhone())
                .password(PasswordUtil.encode(dto.getPassword())) // 👈 BCrypt 加密存储
                .nickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : "会员_" + dto.getUsername())
                .status(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        userMapper.insert(user);
        user.setPassword(null); // 出参擦除密码
        log.info("[用户注册成功] userId={}, username={}", user.getId(), user.getUsername());
        return user;
        }
    /**
     * 2. 用户登录 (密码哈希校验 + 签发 JWT)
     */
    public LoginResponseDTO login(UserAuthDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (user == null || !PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            // 安全军规：永远提示模糊的“账号或密码错误”，防黑客试探用户名存不存在
            throw new BusinessException(400, "账号或密码错误！");
        }

        if (user.getStatus() == 1) {
            throw new BusinessException(403, "该账号已被冻结，请联系管理员！");
        }

        // 签发 JWT 令牌
        String token = JWTUtil.generateToken(user.getId(), user.getUsername());
        log.info("[用户登录成功] userId={}, username={}", user.getId(), user.getUsername());

        return LoginResponseDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .token(token)
                .build();
    }

    /**
     * 3. 查询当前登录人资料 (免参：直接从 UserContext 线程上下文读)
     */
    public User getCurrentProfile() {
        Long currentUserId = UserContext.getUserId();
        User user = userMapper.selectById(currentUserId);
        if (user != null) {
            user.setPassword(null); // 敏感密文脱敏
        }
        return user;
    }

    /**
     * 4. 添加常用实名观演人
     */
    public UserAttendee addAttendee(String realName, String idCard, String phone) {
        Long currentUserId = UserContext.getUserId();
        UserAttendee attendee = UserAttendee.builder()
                .userId(currentUserId)
                .realName(realName)
                .idCardMasked(IdCardSecurityUtil.maskIdCard(idCard))
                .idCardHash(IdCardSecurityUtil.sha256WithSalt(idCard))
                .phone(phone)
                .createTime(LocalDateTime.now())
                .build();
        userAttendeeMapper.insert(attendee);
        return attendee;
    }
    /**
     * 5. 查询当前登录人的常用观演人列表
     */
    public List<UserAttendee> listAttendees() {
        Long currentUserId = UserContext.getUserId();
        return userAttendeeMapper.selectList(
                new LambdaQueryWrapper<UserAttendee>().eq(UserAttendee::getUserId, currentUserId)
        );
    }

}
