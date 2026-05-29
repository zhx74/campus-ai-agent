package com.campus.canteen.mapper;

import com.campus.canteen.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface UserMapper {

    // 根据openid查询用户
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    // 插入新用�?
    void insert(User user);

    @Select("select * from user where id = #{userId}")
    User getById(Long userId);

    @Select("select * from user where phone = #{phone}")
    User getByPhone(String phone);

    // 根据动态条件来统计用户数量
    Integer countByMap(Map map);
}






