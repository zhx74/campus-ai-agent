package com.campus.canteen.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.common.utils.HttpUtil;
import com.campus.canteen.constant.MessageConstant;
import com.campus.canteen.dto.UserLoginDTO;
import com.campus.canteen.entity.User;
import com.campus.canteen.exception.LoginFailedException;
import com.campus.canteen.mapper.UserMapper;
import com.campus.canteen.properties.WeChatProperties;
import com.campus.canteen.service.UserService;
import com.campus.canteen.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    // 微信服务接口地址
    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;

    @Autowired
    private UserMapper userMapper;

    // 微信登陆
    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {

        String openid = getOpenid(userLoginDTO.getCode());

        // 判断openid是否为空，如果为空，登陆失败，抛出异�?
        if(openid == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        // 判断当前用户是否为新用户
        User user = userMapper.getByOpenid(openid);

        // 如果是新用户，自动完成注�?
        if(user == null) {
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }
        // 返回这个用户对象
        return user;
    }

    @Override
    public User phoneLogin(String phone) {
        User user = userMapper.getByPhone(phone);
        if (user == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        return user;
    }

    // 调用微信接口服务，获取微信用户的openid
    private String getOpenid(String code) {
        // 调用微信服务器的接口，获得当前用户的openid
        Map<String, String> map = new HashMap<>();
        map.put("appid", weChatProperties.getAppid());
        map.put("secret", weChatProperties.getSecret());
        map.put("js_code", code);
        map.put("grant_type", "authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN, map);

        JSONObject jsonObject = JSON.parseObject(json);
        String openid = jsonObject.getString("openid");
        return openid;
    }
}






