-- 用户确认：普通领取每日3次；客户页面等级/历史演示标签显示中文。
-- 现有库在备份后执行；只更新额度和字典，不改客户标签编码、归属或领取记录。
-- 事务内可重复执行；已有中文自定义名称保留，未知历史演示项不用于新录入。
USE crm_system;
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_config(config_type,config_group,config_key,config_value,display_name,sort_no,enabled)
VALUES('PARAM','crm.customer','claim_daily_limit','3','专员每日普通领取上限',20,1)
ON DUPLICATE KEY UPDATE version=version+IF(config_value<>'3',1,0),config_value='3';

INSERT INTO sys_config(config_type,config_group,config_key,config_value,display_name,sort_no,enabled)
VALUES
('DICT','customer.level','KEY','KEY','重点客户',10,1),
('DICT','customer.level','NORMAL','NORMAL','常规客户',20,1),
('DICT','customer.level','WATCH','WATCH','观望客户',30,1),
('DICT','customer.tag','POTENTIAL','POTENTIAL','高潜力',10,1),
('DICT','customer.tag','LONG_TERM','LONG_TERM','长期合作',20,1),
('DICT','customer.tag','PRICE_SENSITIVE','PRICE_SENSITIVE','价格敏感',30,1),
('DICT','customer.tag','LOCAL','LOCAL','本地客户',40,1),
('DICT','customer.tag','NEW_DEMAND','NEW_DEMAND','新增需求',50,1),
('DICT','customer.tag','CRM-DEBUG-AUTH-V1','CRM-DEBUG-AUTH-V1','权限演示数据',900,0),
('DICT','customer.tag','演示数据','演示数据','演示数据',901,0),
('DICT','customer.tag','EM01','EM01','华东经理示例一',910,0),
('DICT','customer.tag','EM02','EM02','华东经理示例二',911,0),
('DICT','customer.tag','ES01','ES01','华东专员示例一',912,0),
('DICT','customer.tag','ES02','ES02','华东专员示例二',913,0),
('DICT','customer.tag','ES03','ES03','华东专员示例三',914,0),
('DICT','customer.tag','ES04','ES04','华东专员示例四',915,0),
('DICT','customer.tag','SM01','SM01','华南经理示例一',920,0),
('DICT','customer.tag','SM02','SM02','华南经理示例二',921,0),
('DICT','customer.tag','SS01','SS01','华南专员示例一',922,0),
('DICT','customer.tag','SS02','SS02','华南专员示例二',923,0),
('DICT','customer.tag','SS03','SS03','华南专员示例三',924,0),
('DICT','customer.tag','SS04','SS04','华南专员示例四',925,0),
('DICT','customer.tag','P01','P01','公海示例一',930,0),
('DICT','customer.tag','P02','P02','公海示例二',931,0),
('DICT','customer.tag','P03','P03','公海示例三',932,0),
('DICT','customer.tag','P04','P04','公海示例四',933,0)
ON DUPLICATE KEY UPDATE
 version=version+IF(display_name REGEXP '[一-龥]',0,1),
 display_name=IF(display_name REGEXP '[一-龥]',display_name,VALUES(display_name));

COMMIT;
