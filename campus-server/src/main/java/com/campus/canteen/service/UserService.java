package com.campus.canteen.service;

import com.campus.canteen.dto.UserLoginDTO;
import com.campus.canteen.entity.User;

public interface UserService {
    User wxLogin(UserLoginDTO userLoginDTO);
    User phoneLogin(String phone);
}
