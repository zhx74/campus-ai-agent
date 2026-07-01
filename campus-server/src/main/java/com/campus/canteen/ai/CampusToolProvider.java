package com.campus.canteen.ai;

import com.campus.canteen.ai.agent.ToolRegistry;
import com.campus.canteen.ai.rag.KnowledgeBaseService;
import com.campus.canteen.ai.spi.ToolProvider;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.mapper.DishMapper;
import com.campus.canteen.service.OrderService;
import com.campus.canteen.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class CampusToolProvider implements ToolProvider {

    private final DishMapper dishMapper;
    private final OrderService orderService;
    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public void registerTools(ToolRegistry registry) {
        registry.register("searchDishes",
                "搜索菜品。参数：关键词（如'麻辣'、'面食'等，留空查全部）。当用户询问'有什么吃的'、'推荐菜品'、'辣的口味'时使用。",
                this::searchDishes);

        registry.register("getOrderStatus",
                "查询订单状态。参数：订单ID（数字）。当用户询问'我的订单到哪了'、'订单状态'时使用。",
                input -> {
                    try {
                        return getOrderStatus(Long.parseLong(input.trim()));
                    } catch (NumberFormatException e) {
                        return "参数错误：订单ID必须是纯数字，收到的是 '" + input + "'。";
                    }
                });

        registry.register("searchKnowledge",
                "检索知识库，涵盖食堂与校园信息。参数：自然语言查询。适用场景：食堂名称/地址/营业时间/支付方式/退款规则/菜品推荐/学校概况/学院/宿舍/交通/图书馆/校园文化等一切与学校或食堂相关的问题。",
                this::searchKnowledge);
    }

    private String searchDishes(String keyword) {
        log.info("AI工具调用：搜索菜品，keyword={}", keyword);
        try {
            Dish query = Dish.builder().status(1).build();
            List<Dish> dishes = dishMapper.list(query);

            if (dishes.isEmpty()) {
                return "抱歉，暂时没有可用的菜品。";
            }

            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = keyword.trim();
                dishes = dishes.stream()
                        .filter(d -> (d.getName() != null && d.getName().contains(kw))
                                  || (d.getDescription() != null && d.getDescription().contains(kw)))
                        .collect(Collectors.toList());
            }

            if (dishes.isEmpty()) {
                return "抱歉，没有找到与\"" + keyword + "\"相关的菜品。您可以换个关键词试试。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(dishes.size()).append(" 个相关菜品：\n\n");
            int limit = Math.min(dishes.size(), 5);
            for (int i = 0; i < limit; i++) {
                Dish d = dishes.get(i);
                sb.append(i + 1).append(". ").append(d.getName())
                        .append(" - ¥").append(d.getPrice())
                        .append("\n   ").append(d.getDescription() != null ? d.getDescription() : "暂无简介")
                        .append("\n\n");
            }
            if (dishes.size() > 5) {
                sb.append("...还有更多菜品，可以缩小搜索范围查看。\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("搜索菜品失败", e);
            return "抱歉，菜品服务暂时不可用，请稍后再试。";
        }
    }

    private String getOrderStatus(Long orderId) {
        log.info("AI工具调用：查询订单状态，orderId={}", orderId);
        try {
            OrderVO order = orderService.details(orderId);
            if (order == null) {
                return "抱歉，没有找到订单ID为" + orderId + "的订单信息。请确认订单号是否正确。";
            }
            String statusText = switch (order.getStatus() != null ? order.getStatus() : 0) {
                case 1 -> "待付款";
                case 2 -> "待接单";
                case 3 -> "已接单";
                case 4 -> "派送中";
                case 5 -> "已完成";
                case 6 -> "已取消";
                default -> "未知状态";
            };
            StringBuilder sb = new StringBuilder();
            sb.append("【订单详情】\n");
            sb.append("订单号：").append(order.getNumber()).append("\n");
            sb.append("状态：").append(statusText).append("\n");
            sb.append("收货人：").append(order.getConsignee() != null ? order.getConsignee() : "未知").append("\n");
            sb.append("地址：").append(order.getAddress() != null ? order.getAddress() : "未知").append("\n");
            sb.append("金额：¥").append(order.getAmount() != null ? order.getAmount() : "0").append("\n");
            return sb.toString();
        } catch (Exception e) {
            log.error("查询订单状态失败", e);
            return "抱歉，查询订单状态时出现错误，请稍后重试。";
        }
    }

    private String searchKnowledge(String query) {
        log.info("AI工具调用：检索知识库，query={}", query);
        try {
            String result = knowledgeBaseService.retrieveKnowledge(query);
            if (result != null) {
                return result;
            }
            return "未从知识库中找到与\"" + query + "\"相关的信息。";
        } catch (Exception e) {
            log.error("知识检索失败", e);
            return "知识检索服务暂时不可用。";
        }
    }
}
