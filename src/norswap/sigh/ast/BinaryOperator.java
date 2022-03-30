package norswap.sigh.ast;

public enum BinaryOperator
{
    MULTIPLY("*"),
    DIVIDE("/"),
    REMAINDER("%"),
    ADD("+"),
    SUBTRACT("-"),
    DOT_PRODUCT("@"),
    EQUALITY("=="),
    NOT_EQUALS("!="),
    GREATER(">"),
    LOWER("<"),
    GREATER_EQUAL(">="),
    LOWER_EQUAL("<="),
    AND("&&"),
    OR("||"),

    M_ONE_EQUAL("=?"),      // comparison
    M_ALL_EQUAL("<=>"),     // equality
    M_ALL_LOWER("<<"),      // comparison
    M_ALL_LOWER_EQUAL("<<="), // ...
    M_ONE_LOWER("<?"),
    M_ONE_LOWER_EQUAL("<=?"),
    M_ALL_GREATER(">>"),
    M_ALL_GREATER_EQUAL(">>="),
    M_ONE_GREATER(">?"),
    M_ONE_GREATER_EQUAL(">=?");

    public final String string;

    BinaryOperator (String string) {
        this.string = string;
    }
}
