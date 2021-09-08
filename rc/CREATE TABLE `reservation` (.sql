CREATE TABLE `reservation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '预订单ID',
  `bookerId` bigint(20) DEFAULT NULL,
  `bookerPhone` varchar(50) DEFAULT NULL,
  `bookerName` varchar(50) DEFAULT NULL,
  `bookerLevel` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`reservationId`) USING BTREE,
) ENGINE=InnoDB AUTO_INCREMENT=3065 DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE `test` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `name` varchar(50) DEFAULT NULL,
  `phone` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试表';
