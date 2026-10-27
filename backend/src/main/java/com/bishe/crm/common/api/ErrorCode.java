package com.bishe.crm.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(40000, HttpStatus.BAD_REQUEST, "请求参数不正确"),
    VALIDATION_ERROR(40001, HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "请先登录"),
    LOGIN_FAILED(40101, HttpStatus.UNAUTHORIZED, "账号或密码错误，或账号不可用"),
    TOKEN_INVALID(40102, HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录"),
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "没有权限执行此操作"),
    PASSWORD_CHANGE_REQUIRED(40301, HttpStatus.FORBIDDEN, "请先修改初始密码"),
    PASSWORD_INCORRECT(40002, HttpStatus.BAD_REQUEST, "原密码不正确"),
    PASSWORD_UNCHANGED(40003, HttpStatus.BAD_REQUEST, "新密码不能与原密码相同"),
    ACCOUNT_RULE(40004, HttpStatus.BAD_REQUEST, "账号配置不符合规则，请检查角色、团队或账号状态"),
    OWNERSHIP_PENDING(40901, HttpStatus.CONFLICT, "该账号仍关联业务数据，请完成业务交接后再调整角色或团队"),
    CUSTOMER_DUPLICATE(40902, HttpStatus.CONFLICT, "当前客户中已存在相同信用代码或名称与主电话组合，不能重复创建"),
    CUSTOMER_REVIEW_REQUIRED(40903, HttpStatus.CONFLICT, "存在同名客户，请由经理或管理员人工核对并填写确认原因"),
    CUSTOMER_HAS_HISTORY(40904, HttpStatus.CONFLICT, "客户已有关联业务或归属流转记录，不能删除"),
    PRIMARY_CONTACT_REQUIRED(40005, HttpStatus.BAD_REQUEST, "请先将其他联系人设为主要联系人，再删除当前主要联系人"),
    DICTIONARY_INVALID(40006, HttpStatus.BAD_REQUEST, "分类或标签不可用，请刷新选项；最多选择10个不同标签"),
    CUSTOMER_CLAIM_LIMIT(40007, HttpStatus.BAD_REQUEST, "已达到客户私海持有量或当日领取上限"),
    OPPORTUNITY_RULE(40008, HttpStatus.BAD_REQUEST, "商机状态不允许此操作，请检查相邻阶段、终态、合同和回款条件"),
    OPPORTUNITY_QUOTE_REQUIRED(40009, HttpStatus.BAD_REQUEST, "请先录入报价金额和报价说明"),
    OPPORTUNITY_CONFIRM_REQUIRED(40010, HttpStatus.BAD_REQUEST, "请先录入客户合作确认记录"),
    OPPORTUNITY_SIGNING_REQUIRED(40011, HttpStatus.BAD_REQUEST, "赢单需合同模块完成签约资料及经理确认，当前不能手工绕过"),
    OPPORTUNITY_COLLABORATION_RULE(40012, HttpStatus.BAD_REQUEST, "协作人最多3位且合计比例不得超过30%，成员必须为同团队在职专员"),
    CONTRACT_RULE(40013, HttpStatus.BAD_REQUEST, "合同登记状态或关联商机不允许此操作"),
    CONTRACT_REVIEW_REQUIRED(40014, HttpStatus.BAD_REQUEST, "请先上传签署文件并提交经理核验"),
    CONTRACT_FILE_INVALID(40015, HttpStatus.BAD_REQUEST, "合同资料格式或大小不符合要求"),
    OPPORTUNITY_FOLLOW_REQUIRED(40016, HttpStatus.BAD_REQUEST, "请先为本商机补充有效跟进；未接通、无回应或已打回记录不满足推进条件"),
    CONTRACT_DETAILS_REQUIRED(40017, HttpStatus.BAD_REQUEST, "请补齐合同编号、双方主体、合同金额和签订日期后再提交或通过核验"),
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(40500, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    CONFLICT(40900, HttpStatus.CONFLICT, "数据已变化或与现有记录冲突，请刷新后重试"),
    UNSUPPORTED_MEDIA_TYPE(41500, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求内容类型不支持"),
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "系统暂时无法处理请求"),
    DEPENDENCY_UNAVAILABLE(50300, HttpStatus.SERVICE_UNAVAILABLE, "基础服务暂不可用");

    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public int code() { return code; }
    public HttpStatus status() { return status; }
    public String message() { return message; }
}
