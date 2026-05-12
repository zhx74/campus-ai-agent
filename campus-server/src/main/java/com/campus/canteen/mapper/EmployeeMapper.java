package com.campus.canteen.mapper;

import com.github.pagehelper.Page;
import com.campus.canteen.annotation.AutoFill;
import com.campus.canteen.dto.EmployeePageQueryDTO;
import com.campus.canteen.entity.Employee;
import com.campus.canteen.enumeration.OperationType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmployeeMapper {

    /**
     * 根据用户名查询员�?
     * @param username
     * @return
     */
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);

    // 插入员工数据
    @Insert("insert into employee (name, username, password, phone, sex, id_number, create_time, update_time, create_user, update_user, status)" + "values (#{name}, #{username}, #{password} ,#{phone}, #{sex}, #{idNumber}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser}, #{status})")
    @AutoFill(OperationType.INSERT)
    void insert(Employee employee);

    // 分页查询
    Page<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    //根据主键动态修改status
    @AutoFill(OperationType.UPDATE)
    void update(Employee employee);

    // 根据ID查询员工信息
    @Select("select * from employee where id = #{id}")
    Employee getById(Integer id);
}






