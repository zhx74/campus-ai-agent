package com.campus.canteen.ai;

import com.campus.canteen.ai.spi.KnowledgeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CampusKnowledgeProvider implements KnowledgeProvider {

    @Override
    public List<Document> getDocuments() {
        log.info("播种校园食堂知识库...");
        return List.of(
                // 食堂基本信息
                new Document("校园食堂地址：学校第一食堂（东区）、第二食堂（西区）。东区食堂一层为大众餐厅，二层为特色风味窗口。",
                        Map.of("category", "info")),
                new Document("食堂营业时间：早餐 6:30-8:30，午餐 11:00-13:00，晚餐 17:00-19:00。节假日营业时间另行通知。",
                        Map.of("category", "info")),
                new Document("东区食堂联系电话：010-8888-1001，西区食堂联系电话：010-8888-1002。如有食品安全问题或建议，可到食堂服务台反映。",
                        Map.of("category", "info")),

                // 菜品（仅描述类型和价格范围，具体菜品以 searchDishes 工具查询数据库为准）
                new Document("食堂每天提供约 50 种菜品，每周更换部分菜品。有中式热菜、面食、清真窗口、麻辣烫等多种类型。具体可用菜品请通过菜品搜索功能查询。",
                        Map.of("category", "dish")),

                // 支付
                new Document("支付方式：支持校园一卡通、微信支付、支付宝支付。暂不支持现金和银行卡。新生入学后可在校园卡服务中心办理和充值一卡通。",
                        Map.of("category", "payment")),
                new Document("校园一卡通充值方式：1) 食堂门口自助充值机 2) 校园App在线充值 3) 校园卡服务中心人工充值。充值时间为工作日 9:00-17:00。",
                        Map.of("category", "payment")),
                new Document("食堂消费价格区间：早餐 ¥3-8，午晚餐 ¥9-20。人均月消费约 ¥600-800。",
                        Map.of("category", "payment")),

                // 退订/投诉
                new Document("如遇菜品质量问题（异物、变质等），可持菜品到食堂服务台申请全额退款，服务台工作人员核实后当场处理。",
                        Map.of("category", "refund")),
                new Document("投诉建议渠道：1) 食堂服务台现场反馈 2) 校园App「食堂反馈」板块 3) 学生会权益部邮箱 feedback@campus.edu.cn。",
                        Map.of("category", "refund")),

                // 卫生安全
                new Document("食堂食品安全等级：A级（优秀）。所有菜品每日留样48小时备查，后厨每周进行卫生大检查。食材由学校统一采购，确保来源可追溯。",
                        Map.of("category", "quality")),
                new Document("如有食物过敏史，请在下单时告知窗口工作人员。常见过敏源包括花生、海鲜、牛奶、鸡蛋、小麦。清真窗口严格遵循清真食品规范。",
                        Map.of("category", "quality"))
        );
    }
}
