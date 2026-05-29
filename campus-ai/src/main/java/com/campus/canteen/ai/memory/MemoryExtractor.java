package com.campus.canteen.ai.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;

/**
 * 从对话回合中自动提取值得长期记忆的用户信息。
 * 调用 LLM 进行意图提取，返回事实列表供 LongTermMemoryService 保存。
 */
@Slf4j
public class MemoryExtractor {

    private final ChatModel chatModel;

    public MemoryExtractor(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 从一轮对话中提取用户事实。
     *
     * @param dialogue 格式："用户: xxx\n助手: yyy"
     * @return 提取到的事实（多条用换行分隔），无内容返回 null
     */
    public String extract(String dialogue) {
        String prompt = """
            从以下对话中提取关于用户的值得长期记忆的信息：

            【提取规则】
            1. 只提取用户明确表达的偏好、习惯、限制条件（如口味偏好、过敏源、常用地址）
            2. 只提取明确的事实，不要推测
            3. 每条事实一句话，不要编号，不要前缀
            4. 如果没有值得记忆的信息，只输出一个词：无

            【示例】
            对话：
            用户: 我喜欢吃辣，有什么推荐吗？
            助手: 推荐您尝试麻辣香锅和麻婆豆腐盖饭

            输出：
            用户偏好辣味食物
            用户询问过辣味菜品推荐

            对话：
            用户: 你好
            助手: 你好！有什么可以帮你的？

            输出：
            无

            现在请提取以下对话中的记忆：
            %s""".formatted(dialogue);

        try {
            String result = chatModel.call(prompt);
            if (result == null || result.trim().equals("无") || result.trim().isEmpty()) {
                return null;
            }
            log.info("记忆提取成功: {}", result);
            return result.trim();
        } catch (Exception e) {
            log.error("记忆提取失败", e);
            return null;
        }
    }
}
