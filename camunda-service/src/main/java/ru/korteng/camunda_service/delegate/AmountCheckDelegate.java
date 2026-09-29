package ru.korteng.camunda_service.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Service-task делегат процесса "transaction-approval".
 * Читает переменную процесса "amount" и решает, требуется ли ручное одобрение
 * (сумма превышает лимит) или транзакцию можно выполнять автоматически.
 * <p>
 * Bean-имя "amountCheckDelegate" совпадает с camunda:delegateExpression в BPMN
 * (${amountCheckDelegate}) по умолчанию - имя класса с маленькой первой буквы.
 */
@Component
public class AmountCheckDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(AmountCheckDelegate.class);
    private static final BigDecimal APPROVAL_THRESHOLD = new BigDecimal("50000");

    @Override
    public void execute(DelegateExecution execution) {
        Object rawAmount = execution.getVariable("amount");
        if (rawAmount == null) {
            throw new IllegalStateException("Переменная процесса 'amount' не задана при старте transaction-approval");
        }

        BigDecimal amount = rawAmount instanceof BigDecimal
                ? (BigDecimal) rawAmount
                : new BigDecimal(rawAmount.toString());

        boolean requiresApproval = amount.compareTo(APPROVAL_THRESHOLD) > 0;
        execution.setVariable("requiresApproval", requiresApproval);

        log.info("transaction-approval [{}]: amount={}, threshold={}, requiresApproval={}",
                execution.getProcessInstanceId(), amount, APPROVAL_THRESHOLD, requiresApproval);
    }
}
