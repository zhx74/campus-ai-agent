package com.campus.canteen.ai.agent;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
public class ToolRegistry {

    private final Map<String, ToolDef> tools = new LinkedHashMap<>();

    public void register(String name, String description, Function<String, String> handler) {
        tools.put(name, new ToolDef(name, description, handler));
        log.info("Tool registered: {}", name);
    }

    public String execute(String name, String input) {
        ToolDef tool = tools.get(name);
        if (tool == null) {
            return "错误：未找到工具 '" + name + "'，可用工具：" + tools.keySet();
        }
        try {
            return tool.handler.apply(input);
        } catch (Exception e) {
            log.error("Tool {} execution failed: {}", name, e.getMessage());
            return "工具执行出错: " + e.getMessage();
        }
    }

    public String describeAll() {
        if (tools.isEmpty()) {
            return "（当前无可用工具）";
        }
        StringBuilder sb = new StringBuilder();
        for (ToolDef tool : tools.values()) {
            sb.append("- ").append(tool.name).append(": ").append(tool.description).append("\n");
        }
        return sb.toString();
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    private record ToolDef(String name, String description, Function<String, String> handler) {}
}
