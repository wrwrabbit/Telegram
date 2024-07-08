package org.telegram.messenger.partisan.masked_passcode_screen.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Stack;

public class CalculatorHelper {
    private String remainingExpression;
    private final Stack<BigDecimal> valueStack = new Stack<>();
    private final Stack<Character> operationStack = new Stack<>();

    private CalculatorHelper(String expression) {
        remainingExpression = expression;
    }

    public static BigDecimal calculateExpression(String expression) throws Exception {
        return new CalculatorHelper(expression).calculateExpressionInternal();
    }

    private BigDecimal calculateExpressionInternal() throws Exception {
        while (!remainingExpression.isEmpty()) {
            char firstChar = remainingExpression.charAt(0);
            if ("0123456789.".contains(String.valueOf(firstChar))) {
                processValue();
            } else {
                processOperation(firstChar);
            }
        }
        while (!operationStack.isEmpty()) {
            performLastOperation();
        }
        return !valueStack.isEmpty() ? valueStack.pop() : null;
    }

    private void processValue() throws Exception {
        int numberEndPos = 0;
        BigDecimal currentValue = null;
        try {
            while (numberEndPos < remainingExpression.length()) {
                currentValue = new BigDecimal(remainingExpression.substring(0, numberEndPos + 1));
                numberEndPos++;
            }
        } catch (Exception ignore) {
        }
        if (currentValue == null) {
            throw new Exception();
        }
        remainingExpression = remainingExpression.substring(numberEndPos);
        while (remainingExpression.startsWith("%")) {
            currentValue = currentValue.divide(new BigDecimal(100));
            remainingExpression = remainingExpression.substring(1);
        }
        valueStack.push(currentValue);
    }

    private void processOperation(char firstChar) throws Exception {
        char operation = firstChar;
        remainingExpression = remainingExpression.substring(1);
        if (operation == '%') {
            if (!valueStack.isEmpty()) {
                BigDecimal newValue = valueStack.pop().divide(new BigDecimal(100));
                valueStack.push(newValue);
            }
        } else {
            Character prevOperation = !operationStack.isEmpty() ? operationStack.peek() : null;
            if (prevOperation != null && getOperationPriority(operation) <= getOperationPriority(prevOperation)) {
                performLastOperation();
            }
            if (!remainingExpression.isEmpty()) {
                operationStack.push(operation);
            }
        }
    }

    private static int getOperationPriority(char operation) throws Exception {
        if ("+-".contains(String.valueOf(operation))) {
            return 1;
        } else if ("×/".contains(String.valueOf(operation))) {
            return 2;
        } else {
            throw new Exception();
        }
    }

    private void performLastOperation() {
        BigDecimal val2 = valueStack.pop();
        BigDecimal val1 = !valueStack.isEmpty() ? valueStack.pop() : BigDecimal.ZERO;
        char lastOperation = operationStack.pop();
        BigDecimal result = calculateOperationResult(val1, val2, lastOperation);
        valueStack.push(result);
    }

    private static BigDecimal calculateOperationResult(BigDecimal val1, BigDecimal val2, char action) {
        switch (action) {
            case '+': return val1.add(val2);
            case '-': return val1.subtract(val2);
            case '×': return val1.multiply(val2);
            case '/': return val1.divide(val2, 32, RoundingMode.HALF_UP);
            default: throw new RuntimeException("Invalid action");
        }
    }
}
