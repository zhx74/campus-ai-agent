package com.campus.canteen.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PasswordEditDTO implements Serializable {

    //员工id
    private Long empId;

    //旧密�?
    private String oldPassword;

    //新密�?
    private String newPassword;

}






