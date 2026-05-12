package com.campus.canteen.service;

import com.campus.canteen.dto.UserLoginDTO;
import com.campus.canteen.entity.User;
import org.springframework.stereotype.Service;


public interface UserService {

    // 微信登陆
    User wxLogin(UserLoginDTO userLoginDTO);
}






