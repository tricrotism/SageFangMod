package com.tricrotism.modules.math;

/**
 * Recursive-descent parser for arithmetic expressions.
 * <p>
 * Supports: {@code + - * / % ^} operators, parentheses, unary minus/plus,
 * and the constants {@code pi} and {@code e}. Respects standard operator
 * precedence (PEMDAS) with right-associative exponentiation.
 *
 * <pre>
 *   expression → term (('+' | '-') term)*
 *   term       → unary (('*' | '/' | '%') unary)*
 *   unary      → ('-' | '+')? power
 *   power      → atom ('^' power)?        // right-assoc
 *   atom       → NUMBER | CONSTANT | '(' expression ')'
 * </pre>
 */
public final class MathExprParser {

    private final String input;
    private int pos;

    private MathExprParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    /**
     * Parses and evaluates the expression.
     *
     * @param expr the expression string, e.g. {@code "69 + 420"}
     * @return the result
     * @throws IllegalArgumentException if the input is not a valid expression
     */
    public static double evaluate(String expr) {
        MathExprParser parser = new MathExprParser(expr.trim());
        double result = parser.parseExpression();
        parser.skipWhitespace();
        if (parser.pos < parser.input.length()) {
            throw new IllegalArgumentException("Unexpected character at position " + parser.pos
                + ": '" + parser.input.charAt(parser.pos) + "'");
        }
        return result;
    }

    /**
     * Returns true if the string looks like it could be a math expression
     * (cheap pre-filter before attempting a full parse).
     */
    public static boolean looksLikeMath(String s) {
        if (s == null || s.isBlank()) return false;
        String trimmed = s.trim();
        // Must contain at least one digit
        boolean hasDigit = false;
        // Must contain at least one operator
        boolean hasOperator = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isDigit(c)) hasDigit = true;
            else if (c == '+' || c == '*' || c == '/' || c == '^' || c == '%') hasOperator = true;
            else if (c == '-' && i > 0 && Character.isDigit(trimmed.charAt(i - 1))) hasOperator = true;
            else if (c == '.' || c == '(' || c == ')' || c == ' ' || c == '-') { /* allowed */ } else if (Character.isLetter(c)) {
                // Allow 'pi' and 'e' constants, reject anything else
                String rest = trimmed.substring(i).toLowerCase();
                if (rest.startsWith("pi")) {
                    i += 1;
                    hasDigit = true;
                } else if (rest.startsWith("e") && (i + 1 >= trimmed.length() || !Character.isLetter(trimmed.charAt(i + 1)))) {
                    hasDigit = true;
                } else return false;
            } else {
                return false;
            }
        }
        return hasDigit && hasOperator;
    }

    private double parseExpression() {
        double left = parseTerm();
        skipWhitespace();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '+') {
                pos++;
                left += parseTerm();
            } else if (c == '-') {
                pos++;
                left -= parseTerm();
            } else break;
            skipWhitespace();
        }
        return left;
    }

    private double parseTerm() {
        double left = parseUnary();
        skipWhitespace();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '*') {
                pos++;
                left *= parseUnary();
            } else if (c == '/') {
                pos++;
                double divisor = parseUnary();
                if (divisor == 0) throw new IllegalArgumentException("Division by zero");
                left /= divisor;
            } else if (c == '%') {
                pos++;
                double divisor = parseUnary();
                if (divisor == 0) throw new IllegalArgumentException("Modulo by zero");
                left %= divisor;
            } else break;
            skipWhitespace();
        }
        return left;
    }

    private double parseUnary() {
        skipWhitespace();
        if (pos < input.length()) {
            if (input.charAt(pos) == '-') {
                pos++;
                return -parsePower();
            }
            if (input.charAt(pos) == '+') {
                pos++;
                return parsePower();
            }
        }
        return parsePower();
    }

    private double parsePower() {
        double base = parseAtom();
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == '^') {
            pos++;
            double exp = parseUnary(); // right-associative via unary recursion
            return Math.pow(base, exp);
        }
        return base;
    }

    private double parseAtom() {
        skipWhitespace();
        if (pos >= input.length()) {
            throw new IllegalArgumentException("Unexpected end of expression");
        }

        char c = input.charAt(pos);

        if (c == '(') {
            pos++;
            double result = parseExpression();
            skipWhitespace();
            if (pos >= input.length() || input.charAt(pos) != ')') {
                throw new IllegalArgumentException("Missing closing parenthesis");
            }
            pos++;
            return result;
        }

        if (Character.isDigit(c) || c == '.') {
            return parseNumber();
        }

        // Constants
        String rest = input.substring(pos).toLowerCase();
        if (rest.startsWith("pi")) {
            pos += 2;
            return Math.PI;
        }
        if (rest.startsWith("e") && (pos + 1 >= input.length() || !Character.isLetterOrDigit(input.charAt(pos + 1)))) {
            pos++;
            return Math.E;
        }

        throw new IllegalArgumentException("Unexpected character at position " + pos + ": '" + c + "'");
    }

    private double parseNumber() {
        int start = pos;
        boolean hasDot = false;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isDigit(c)) {
                pos++;
            } else if (c == '.' && !hasDot) {
                hasDot = true;
                pos++;
            } else break;
        }
        if (pos == start) {
            throw new IllegalArgumentException("Expected number at position " + pos);
        }
        return Double.parseDouble(input.substring(start, pos));
    }

    private void skipWhitespace() {
        while (pos < input.length() && input.charAt(pos) == ' ') {
            pos++;
        }
    }
}
