package com.campus.canteen.ai.config;

import com.campus.canteen.ai.agent.ReActAgent;
import com.campus.canteen.ai.agent.ToolRegistry;
import com.campus.canteen.ai.memory.LongTermMemoryService;
import com.campus.canteen.ai.spi.ToolProvider;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiChatConfiguration {

    private static final int MAX_ITERATIONS = 10;
    private static final int HISTORY_WINDOW_SIZE = 20;

    @Value("${campus.ai.system-prompt:}")
    private String externalSystemPrompt;

    @Bean
    public ToolRegistry toolRegistry(List<ToolProvider> toolProviders) {
        ToolRegistry registry = new ToolRegistry();
        for (ToolProvider provider : toolProviders) {
            provider.registerTools(registry);
        }
        return registry;
    }

    @Bean
    public ReActAgent reActAgent(ChatModel chatModel,
                                  ToolRegistry toolRegistry,
                                  ChatMemory chatMemory,
                                  ObjectProvider<LongTermMemoryService> longTermMemoryProvider) {
        LongTermMemoryService longTermMemory = longTermMemoryProvider.getIfAvailable();
        String systemPrompt = (externalSystemPrompt != null && !externalSystemPrompt.isBlank())
                ? externalSystemPrompt
                : defaultSystemPrompt();

        return new ReActAgent(
                chatModel,
                toolRegistry,
                systemPrompt,
                chatMemory,
                longTermMemory,
                MAX_ITERATIONS,
                HISTORY_WINDOW_SIZE,
                longTermMemory != null
        );
    }

    private String defaultSystemPrompt() {
        return """
            请遵循 ReAct 模式工作。

            【ReAct 工作模式】
            你必须严格按照以下步骤工作：

            1. 分析用户意图，输出你的思考：
               Thought: <你打算怎么做、需要什么信息>

            2. 如果需要调用工具获取数据：
               Action: <工具名称>
               Action Input: <参数>

               系统将返回 Observation。你可以基于结果继续思考。

            3. 当你确信已掌握足够信息时：
               Thought: 信息已足够
               Final Answer: <你的回答>

            【核心规则】
            1. 不要编造任何数据 —— 必须通过 Action 调用工具获取真实数据
            2. 工具返回"没有找到"时，如实告知用户
            3. 每次只能调用一个 Action
            4. 能直接回答的问题跳过 Action，直接 Final Answer
            5. Thought 要简洁，1-2 句话即可
            """;
    }
}
