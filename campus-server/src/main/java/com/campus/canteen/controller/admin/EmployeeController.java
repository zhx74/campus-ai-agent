package com.campus.canteen.controller.admin;

import com.campus.canteen.constant.JwtClaimsConstant;
import com.campus.canteen.dto.EmployeeDTO;
import com.campus.canteen.dto.EmployeeLoginDTO;
import com.campus.canteen.dto.EmployeePageQueryDTO;
import com.campus.canteen.entity.Employee;
import com.campus.canteen.properties.JwtProperties;
import com.campus.canteen.result.PageResult;
import com.campus.canteen.result.Result;
import com.campus.canteen.service.EmployeeService;
import com.campus.canteen.utils.JwtUtil;
import com.campus.canteen.vo.EmployeeLoginVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Tag(name = "员工相关接口")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    @Operation(summary = "员工登录")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO);

        Employee employee = employeeService.login(employeeLoginDTO);

        //登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @PostMapping("/logout")
    @Operation(summary = "员工退出登录")
    public Result<String> logout() {
        return Result.success();
    }

    // 新增员工
    @PostMapping
    @Operation(summary = "新增员工")
    public Result save(@RequestBody EmployeeDTO employeeDTO) {
        log.info("新增员工：{}", employeeDTO);

        employeeService.save(employeeDTO);

        return Result.success();
    }

    // 员工分页查询
    @GetMapping("/page")
    @Operation(summary = "员工分页查询")
    public Result<PageResult> page(EmployeePageQueryDTO employeePageQueryDTO ) {
        log.info("员工分页查询,参数为{}", employeePageQueryDTO);
        PageResult pageResult = employeeService.pageQuery(employeePageQueryDTO);
        return Result.success(pageResult);
    }

    // 启用禁用员工账号
    @PostMapping("/status/{status}")
    @Operation(summary = "启用禁用员工账号")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("启用禁用员工账号，{}, {}",status, id);
        employeeService.startOrStop(status, id);

        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询员工信息")
    public Result<Employee> getById(@PathVariable Integer id) {
        log.info("根据ID查询员工信息：{}", id);

        Employee employee = employeeService.getById(id);
        return Result.success(employee);
    }

    @PutMapping
    @Operation(summary = "编辑员工信息")
    public Result update(@RequestBody EmployeeDTO employeeDTO) {
        log.info("编辑员工信息:{}",employeeDTO);
        employeeService.update(employeeDTO);

        return Result.success();
    }

}
