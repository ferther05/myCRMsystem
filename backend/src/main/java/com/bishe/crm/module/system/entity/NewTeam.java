/* 文件作用：接收新经理团队的数据库自增主键，不作为前端入参。 */
package com.bishe.crm.module.system.entity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class NewTeam {
    private Long id;
    private String teamCode;
    private String teamName;
}
