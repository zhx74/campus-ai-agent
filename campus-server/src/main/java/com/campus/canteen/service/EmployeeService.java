package com.campus.canteen.service;

import com.campus.canteen.dto.EmployeeDTO;
import com.campus.canteen.dto.EmployeeLoginDTO;
import com.campus.canteen.dto.EmployeePageQueryDTO;
import com.campus.canteen.entity.Employee;
import com.campus.canteen.result.PageResult;

public interface EmployeeService {

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    // 新增员工
    void save(EmployeeDTO employeeDTO);

    // 分页查询
    PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    // 启用禁用员工账号
    void startOrStop(Integer status, Long id);

    // 根据ID查询员工
    Employee getById(Integer id);

    // 编辑员工信息
    void update(EmployeeDTO employeeDTO);
}






