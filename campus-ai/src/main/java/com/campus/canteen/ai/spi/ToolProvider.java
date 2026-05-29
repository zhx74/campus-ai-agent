package com.campus.canteen.ai.spi;

import com.campus.canteen.ai.agent.ToolRegistry;

/**
 * 工具提供者 SPI —— 唯一的业务扩展点。
 * 宿主系统实现此接口，向 AI Agent 注册业务工具。
 * <p>
 * 示例：
 * <pre>
 * registry.register("searchDishes", "搜索菜品", keyword -&gt; dishService.search(keyword));
 * </pre>
 */
@FunctionalInterface
public interface ToolProvider {
    void registerTools(ToolRegistry registry);
}
