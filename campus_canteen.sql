-- 创建数据库
CREATE DATABASE IF NOT EXISTS `campus_canteen` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `campus_canteen`;

-- ----------------------------
-- 1. 员工表 (employee)
-- ----------------------------
DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) NOT NULL COMMENT '姓名',
  `username` varchar(32) NOT NULL COMMENT '用户名',
  `password` varchar(64) NOT NULL COMMENT '密码',
  `phone` varchar(11) NOT NULL COMMENT '手机号',
  `sex` varchar(2) NOT NULL COMMENT '性别',
  `id_number` varchar(18) DEFAULT NULL COMMENT '身份证号',
  `status` int(11) NOT NULL DEFAULT '1' COMMENT '账号状态 0禁用 1启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
  `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工信息';

-- 插入默认管理员账号 (密码: 123456, MD5加密)
INSERT INTO `employee` VALUES (1, '管理员', 'admin', 'e10adc3949ba59abbe56e057f20f883e', '13812345678', '1', '110101199001011234', 1, NOW(), NOW(), 1, 1);

-- ----------------------------
-- 2. 分类表 (category)
-- ----------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` int(11) DEFAULT NULL COMMENT '类型 1菜品分类 2套餐分类',
  `name` varchar(32) NOT NULL COMMENT '分类名称',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '顺序',
  `status` int(11) DEFAULT NULL COMMENT '分类状态 0禁用 1启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
  `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品及套餐分类';

-- 插入示例数据
INSERT INTO `category` VALUES 
(1, 1, '湘菜', 1, 1, NOW(), NOW(), 1, 1),
(2, 1, '川菜', 2, 1, NOW(), NOW(), 1, 1),
(3, 1, '粤菜', 3, 1, NOW(), NOW(), 1, 1),
(4, 2, '营养套餐', 1, 1, NOW(), NOW(), 1, 1),
(5, 2, '超值套餐', 2, 1, NOW(), NOW(), 1, 1);

-- ----------------------------
-- 3. 菜品表 (dish)
-- ----------------------------
DROP TABLE IF EXISTS `dish`;
CREATE TABLE `dish` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) NOT NULL COMMENT '菜品名称',
  `category_id` bigint(20) NOT NULL COMMENT '菜品分类id',
  `price` decimal(10,2) DEFAULT NULL COMMENT '菜品价格',
  `image` varchar(255) DEFAULT NULL COMMENT '图片',
  `description` varchar(255) DEFAULT NULL COMMENT '描述信息',
  `status` int(11) DEFAULT '1' COMMENT '0停售 1起售',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
  `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_dish_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品';

-- 插入示例数据
INSERT INTO `dish` VALUES 
(1, '麻婆豆腐', 1, 18.00, 'https://example.com/mapo.jpg', '经典川菜，麻辣鲜香', 1, NOW(), NOW(), 1, 1),
(2, '宫保鸡丁', 2, 28.00, 'https://example.com/gongbao.jpg', '酸甜微辣，口感丰富', 1, NOW(), NOW(), 1, 1),
(3, '清蒸鲈鱼', 3, 58.00, 'https://example.com/luyu.jpg', '鲜嫩可口，营养丰富', 1, NOW(), NOW(), 1, 1);

-- ----------------------------
-- 4. 菜品口味表 (dish_flavor)
-- ----------------------------
DROP TABLE IF EXISTS `dish_flavor`;
CREATE TABLE `dish_flavor` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dish_id` bigint(20) NOT NULL COMMENT '菜品',
  `name` varchar(32) DEFAULT NULL COMMENT '口味名称',
  `value` varchar(255) DEFAULT NULL COMMENT '口味数据list',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品口味关系表';

-- 插入示例数据
INSERT INTO `dish_flavor` VALUES 
(1, 1, '辣度', '[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]'),
(2, 2, '辣度', '[\"不辣\",\"微辣\",\"中辣\"]');

-- ----------------------------
-- 5. 套餐表 (setmeal)
-- ----------------------------
DROP TABLE IF EXISTS `setmeal`;
CREATE TABLE `setmeal` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_id` bigint(20) NOT NULL COMMENT '菜品分类id',
  `name` varchar(32) NOT NULL COMMENT '套餐名称',
  `price` decimal(10,2) NOT NULL COMMENT '套餐价格',
  `status` int(11) DEFAULT '1' COMMENT '售卖状态 0停售 1起售',
  `description` varchar(255) DEFAULT NULL COMMENT '描述信息',
  `image` varchar(255) DEFAULT NULL COMMENT '图片',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
  `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_setmeal_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐';

-- 插入示例数据
INSERT INTO `setmeal` VALUES 
(1, 4, '营养套餐A', 35.00, 1, '包含一荤两素一汤', 'https://example.com/setmeal-a.jpg', NOW(), NOW(), 1, 1),
(2, 5, '超值套餐B', 25.00, 1, '经济实惠，适合学生', 'https://example.com/setmeal-b.jpg', NOW(), NOW(), 1, 1);

-- ----------------------------
-- 6. 套餐菜品关系表 (setmeal_dish)
-- ----------------------------
DROP TABLE IF EXISTS `setmeal_dish`;
CREATE TABLE `setmeal_dish` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `setmeal_id` bigint(20) DEFAULT NULL COMMENT '套餐id',
  `dish_id` bigint(20) DEFAULT NULL COMMENT '菜品id',
  `name` varchar(32) DEFAULT NULL COMMENT '菜品名称',
  `price` decimal(10,2) DEFAULT NULL COMMENT '菜品单价',
  `copies` int(11) DEFAULT NULL COMMENT '菜品份数',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐菜品关系';

-- 插入示例数据
INSERT INTO `setmeal_dish` VALUES 
(1, 1, 1, '麻婆豆腐', 18.00, 1),
(2, 1, 2, '宫保鸡丁', 28.00, 1),
(3, 2, 1, '麻婆豆腐', 18.00, 1);

-- ----------------------------
-- 7. 用户表 (user)
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid` varchar(45) DEFAULT NULL COMMENT '微信用户唯一标识',
  `name` varchar(32) DEFAULT NULL COMMENT '姓名',
  `phone` varchar(11) DEFAULT NULL COMMENT '手机号',
  `sex` varchar(2) DEFAULT NULL COMMENT '性别',
  `id_number` varchar(18) DEFAULT NULL COMMENT '身份证号',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像',
  `create_time` datetime DEFAULT NULL COMMENT '注册时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息';

-- ----------------------------
-- 8. 地址簿表 (address_book)
-- ----------------------------
DROP TABLE IF EXISTS `address_book`;
CREATE TABLE `address_book` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '用户id',
  `consignee` varchar(50) DEFAULT NULL COMMENT '收货人',
  `sex` varchar(2) DEFAULT NULL COMMENT '性别',
  `phone` varchar(11) NOT NULL COMMENT '手机号',
  `province_code` varchar(12) DEFAULT NULL COMMENT '省级区划编号',
  `province_name` varchar(32) DEFAULT NULL COMMENT '省级名称',
  `city_code` varchar(12) DEFAULT NULL COMMENT '市级区划编号',
  `city_name` varchar(32) DEFAULT NULL COMMENT '市级名称',
  `district_code` varchar(12) DEFAULT NULL COMMENT '区级区划编号',
  `district_name` varchar(32) DEFAULT NULL COMMENT '区级名称',
  `detail` varchar(200) DEFAULT NULL COMMENT '详细地址',
  `label` varchar(100) DEFAULT NULL COMMENT '标签',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '默认 0否 1是',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地址簿';

-- ----------------------------
-- 9. 购物车表 (shopping_cart)
-- ----------------------------
DROP TABLE IF EXISTS `shopping_cart`;
CREATE TABLE `shopping_cart` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) DEFAULT NULL COMMENT '商品名称',
  `image` varchar(255) DEFAULT NULL COMMENT '图片',
  `user_id` bigint(20) NOT NULL COMMENT '用户id',
  `dish_id` bigint(20) DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint(20) DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) DEFAULT NULL COMMENT '口味',
  `number` int(11) NOT NULL DEFAULT '1' COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

-- ----------------------------
-- 10. 订单表 (orders)
-- ----------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `number` varchar(50) DEFAULT NULL COMMENT '订单号',
  `status` int(11) NOT NULL DEFAULT '1' COMMENT '订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款',
  `user_id` bigint(20) NOT NULL COMMENT '下单用户',
  `address_book_id` bigint(20) NOT NULL COMMENT '地址id',
  `order_time` datetime NOT NULL COMMENT '下单时间',
  `checkout_time` datetime DEFAULT NULL COMMENT '结账时间',
  `pay_method` int(11) NOT NULL DEFAULT '1' COMMENT '支付方式 1微信支付 2支付宝支付',
  `pay_status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '支付状态 0未支付 1已支付 2退款',
  `amount` decimal(10,2) NOT NULL COMMENT '实收金额',
  `remark` varchar(100) DEFAULT NULL COMMENT '备注',
  `phone` varchar(11) DEFAULT NULL COMMENT '手机号',
  `address` varchar(255) DEFAULT NULL COMMENT '地址',
  `user_name` varchar(32) DEFAULT NULL COMMENT '用户名称',
  `consignee` varchar(32) DEFAULT NULL COMMENT '收货人',
  `cancel_reason` varchar(255) DEFAULT NULL COMMENT '订单取消原因',
  `rejection_reason` varchar(255) DEFAULT NULL COMMENT '订单拒绝原因',
  `cancel_time` datetime DEFAULT NULL COMMENT '订单取消时间',
  `estimated_delivery_time` datetime DEFAULT NULL COMMENT '预计送达时间',
  `delivery_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '配送状态 1立即送出 0选择具体时间',
  `delivery_time` datetime DEFAULT NULL COMMENT '送达时间',
  `pack_amount` int(11) DEFAULT NULL COMMENT '打包费',
  `tableware_number` int(11) DEFAULT NULL COMMENT '餐具数量',
  `tableware_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '餐具数量状态 1按餐量提供 0选择具体数量',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ----------------------------
-- 11. 订单明细表 (order_detail)
-- ----------------------------
DROP TABLE IF EXISTS `order_detail`;
CREATE TABLE `order_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) DEFAULT NULL COMMENT '名字',
  `image` varchar(255) DEFAULT NULL COMMENT '图片',
  `order_id` bigint(20) NOT NULL COMMENT '订单id',
  `dish_id` bigint(20) DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint(20) DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) DEFAULT NULL COMMENT '口味',
  `number` int(11) NOT NULL DEFAULT '1' COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- ----------------------------
-- 完成提示
-- ----------------------------
SELECT '✅ 数据库 campus_canteen 创建成功！' AS message;
SELECT '✅ 已创建 11 张数据表' AS message;
SELECT '✅ 已插入示例数据' AS message;
SELECT '📝 默认管理员账号: admin / 123456' AS message;










