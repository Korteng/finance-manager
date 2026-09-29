package ru.korteng.camunda_service.controller;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Тонкий REST-слой поверх Camunda Java API (RuntimeService/TaskService) -
 * запуск процесса "transaction-approval" и работа с открытыми задачами,
 * без обязательного похода в Tasklist UI. Не для прод-нагрузки, для демонстрации
 * концепции (process instance, business key, candidate group, task completion).
 */
@RestController
@RequestMapping("/api/camunda")
public class ApprovalProcessController {

    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public ApprovalProcessController(RuntimeService runtimeService, TaskService taskService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    public record StartApprovalRequest(String transactionId, BigDecimal amount) {}

    @PostMapping("/transactions/approval-process")
    public Map<String, Object> startApprovalProcess(@RequestBody StartApprovalRequest request) {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "transaction-approval",
                request.transactionId(),
                Map.of(
                        "transactionId", request.transactionId(),
                        "amount", request.amount()
                )
        );
        return Map.of(
                "processInstanceId", instance.getId(),
                "businessKey", String.valueOf(instance.getBusinessKey()),
                "ended", instance.isEnded()
        );
    }

    @GetMapping("/tasks")
    public List<Map<String, Object>> openTasks() {
        return taskService.createTaskQuery().active().list().stream()
                .map(this::toTaskView)
                .collect(Collectors.toList());
    }

    @PostMapping("/tasks/{taskId}/approve")
    public Map<String, Object> approve(@PathVariable String taskId, @RequestParam(defaultValue = "") String comment) {
        taskService.complete(taskId, Map.of("approved", true, "comment", comment));
        return Map.of("taskId", taskId, "approved", true);
    }

    private Map<String, Object> toTaskView(Task task) {
        List<String> candidateGroups = taskService.getIdentityLinksForTask(task.getId()).stream()
                .map(link -> link.getGroupId())
                .filter(g -> g != null)
                .collect(Collectors.toList());
        return Map.of(
                "taskId", task.getId(),
                "name", task.getName(),
                "processInstanceId", task.getProcessInstanceId(),
                "candidateGroups", candidateGroups
        );
    }
}
