/* 文件作用：任务中心待办、已读、业务跳转、日志查询及管理员补跑入口。 */
package com.bishe.crm.module.task.controller;

import com.bishe.crm.common.api.*;
import com.bishe.crm.module.auth.security.AuthPrincipal;
import com.bishe.crm.module.task.dto.*;
import com.bishe.crm.module.task.entity.JobRun;
import com.bishe.crm.module.task.service.TaskService;
import com.bishe.crm.module.task.vo.TaskViews;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 参数校验及统一响应，业务权限和事务交由服务层。 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Validated
public class TaskController {
    private final TaskService tasks;
    private final com.bishe.crm.module.task.service.OperationDetailService details;
    /** TASK-08：授权范围内的字段前后值对比。 */
    @GetMapping("/operations/{id}")
    public ApiResponse<com.bishe.crm.module.task.service.OperationDetailService.Detail> operation(@AuthenticationPrincipal AuthPrincipal a,@Positive @PathVariable Long id) { return ApiResponse.success(details.detail(a,id)); }
    /** TASK-09：本人未读待处理数量与待办数量，供导航提醒使用。 */
    @GetMapping("/summary")
    public ApiResponse<TaskViews.Summary> summary(@AuthenticationPrincipal AuthPrincipal a) { return ApiResponse.success(tasks.summary(a)); }
    /** TASK-01：本人待办分页。 */
    @GetMapping("/todos")
    public ApiResponse<PageResult<TaskViews.TodoRow>> todos(@AuthenticationPrincipal AuthPrincipal a,@Valid TaskQuery q) { return ApiResponse.success(tasks.todos(a,q)); }
    /** TASK-02：本人待办标记已读。 */
    @PostMapping("/todos/{id}/read")
    public ApiResponse<Void> read(@AuthenticationPrincipal AuthPrincipal a,@Positive @PathVariable Long id,@Valid @RequestBody TaskRequests.Read r) { tasks.read(a,id,r.version());return ApiResponse.success(null); }
    /** TASK-03：重新授权后定位业务。 */
    @GetMapping("/todos/{id}/target")
    public ApiResponse<TaskViews.Target> target(@AuthenticationPrincipal AuthPrincipal a,@Positive @PathVariable Long id) { return ApiResponse.success(tasks.link(a,id)); }
    /** TASK-04：授权范围内的业务操作摘要。 */
    @GetMapping("/operations")
    public ApiResponse<PageResult<TaskViews.Operation>> operations(@AuthenticationPrincipal AuthPrincipal a,@Valid TaskQuery q) { return ApiResponse.success(tasks.operations(a,q)); }
    /** TASK-05：管理员任务执行日志。 */
    @GetMapping("/jobs")
    public ApiResponse<PageResult<JobRun>> jobs(@AuthenticationPrincipal AuthPrincipal a,@Valid TaskQuery q) { return ApiResponse.success(tasks.jobs(a,q)); }
    /** TASK-06：管理员手动执行当天扫描。 */
    @PostMapping("/jobs/scan")
    public ApiResponse<Map<String,Object>> scan(@AuthenticationPrincipal AuthPrincipal a,@Valid @RequestBody TaskRequests.Scan r) { return ApiResponse.success(tasks.scan(a,r.jobName())); }
    /** TASK-07：管理员只重试最新记录的失败对象。 */
    @PostMapping("/jobs/{id}/retry")
    public ApiResponse<Map<String,Object>> retry(@AuthenticationPrincipal AuthPrincipal a,@Positive @PathVariable Long id) { return ApiResponse.success(tasks.retry(a,id)); }
}
