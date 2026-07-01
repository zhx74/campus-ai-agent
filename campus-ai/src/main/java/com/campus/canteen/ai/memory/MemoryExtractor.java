package com.campus.canteen.ai.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;

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
            1. 提取用户的身份信息（姓名、年级、专业、角色等）
            2. 提取用户的偏好、习惯、限制条件（饮食偏好、常用信息、约束等）
            3. 只提取明确的事实，不要推测
            4. 每条事实一句话，不要编号，不要前缀
            5. 如果没有值得记忆的信息，只输出一个词：无

            【示例】
            对话：
            用户: 我叫小周，想问一下食堂有什么好吃的
            助手: 小周你好！食堂有很多美食...

            输出：
            用户叫小周

            对话：
            用户: 帮我查一下我的订单状态，订单号 ORD001
            助手: 好的，正在为您查询订单 ORD001

            输出：
            用户查询了订单 ORD001

            对话：
            用户: 你好
            助手: 你好！有什么可以帮你的？

            输出：
            无

            现在请提取以下对话中的记忆：
            %s""".formatted(dialogue);

        try {
            String result = chatModel.call(prompt);
            if (result == null || result.trim().isEmpty()) {
                return null;
            }
            String trimmed = result.trim();
            // 兼容 LLM 输出 "无"、"无。"、"暂无"、"没有" 等变体
            if (trimmed.matches("^[无暫没].{0,4}[。.！!？?]?$")) {
                return null;
            }
            log.debug("记忆提取成功: {}", trimmed);
            return trimmed;
        } catch (Exception e) {
            log.error("记忆提取失败", e);
            return null;
        }
    }
}
