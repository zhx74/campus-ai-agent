package com.campus.canteen.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.campus.canteen.constant.MessageConstant;
import com.campus.canteen.constant.PasswordConstant;
import com.campus.canteen.constant.StatusConstant;
import com.campus.canteen.context.BaseContext;
import com.campus.canteen.dto.EmployeeDTO;
import com.campus.canteen.dto.EmployeeLoginDTO;
import com.campus.canteen.dto.EmployeePageQueryDTO;
import com.campus.canteen.entity.Employee;
import com.campus.canteen.exception.AccountLockedException;
import com.campus.canteen.exception.AccountNotFoundException;
import com.campus.canteen.exception.PasswordErrorException;
import com.campus.canteen.mapper.EmployeeMapper;
import com.campus.canteen.result.PageResult;
import com.campus.canteen.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数�?
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定�?
        if (employee == null) {
            //账号不存�?
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对
        // 对前端传递的铭文密码进行md5加密处理
        password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!password.equals(employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁�?
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对�?
        return employee;
    }

    // 新增员工
    @Override
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();

        // 对象属性拷�?
        BeanUtils.copyProperties(employeeDTO, employee);

        // 设置状态，默认正常(1为正常，0为锁�?
        employee.setStatus(StatusConstant.ENABLE);

        // 设置密码�?23456默认
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()));

        // 设置创建时间和修改时�?
        //employee.setCreateTime(LocalDateTime.now());
        //employee.setUpdateTime(LocalDateTime.now());

        // 设置当前记录创建人id和修改人id
        // TODO 后期改为当前创建人id和登录用户id
        //employee.setCreateUser(BaseContext.getCurrentId());
        //employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.insert(employee);
    }

    // 分页查询
    @Override
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {

        // 开始分页查�?
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());

        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);

        long total = page.getTotal();
        List<Employee> records = page.getResult();

        return new PageResult(total, records);
    }

    // 启用禁用员工账号
    @Override
    public void startOrStop(Integer status, Long id) {
        //因为只设置两个属性所以用build方法  属性少时用 Builder（手动精确指定）
        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();

        employeeMapper.update(employee);
    }

    // 根据ID查询员工
    @Override
    public Employee getById(Integer id) {
        Employee employee = employeeMapper.getById(id);
        employee.setPassword("******");
        // 密码设置�?*****
        return employee;
    }

    // 编辑员工信息
    @Override
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        //属性多时用 copyProperties（自动批量复制）
        BeanUtils.copyProperties(employeeDTO, employee);

//    employee.setUpdateTime(LocalDateTime.now());
//     employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.update(employee);
    }
}






