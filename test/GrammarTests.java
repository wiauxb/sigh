import norswap.autumn.AutumnTestFixture;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.*;
import norswap.sigh.types.IntType;
import norswap.sigh.types.MatType;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static norswap.sigh.ast.BinaryOperator.*;

public class GrammarTests extends AutumnTestFixture {
    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final Class<?> grammarClass = grammar.getClass();

    // ---------------------------------------------------------------------------------------------

    private static IntLiteralNode intlit (long i) {
        return new IntLiteralNode(null, i);
    }

    private static FloatLiteralNode floatlit (double d) {
        return new FloatLiteralNode(null, d);
    }

    private static StringLiteralNode stringlit (String str) {return new StringLiteralNode(null, str);}

    private static MatrixLiteralNode matlit(List<ArrayLiteralNode> m) {
        return new MatrixLiteralNode(null, m);
    }

    private static ArrayLiteralNode arraylit(List<Object> l) {
        return new ArrayLiteralNode(null, l);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testLiteralsAndUnary () {
        rule = grammar.expression;

        successExpect("42", intlit(42));
        successExpect("42.0", floatlit(42d));
        successExpect("\"hello\"", new StringLiteralNode(null, "hello"));
        successExpect("(42)", new ParenthesizedNode(null, intlit(42)));
        successExpect("[1, 2, 3]", new ArrayLiteralNode(null, asList(intlit(1), intlit(2), intlit(3))));
        successExpect("[[1, 2, 3],[4, 5, 6]]", matlit(asList(arraylit(asList(intlit(1), intlit(2), intlit(3))), arraylit(asList(intlit(4), intlit(5), intlit(6))))));
        successExpect("[0](2)", new MatrixGeneratorNode(null, intlit(0), asList(intlit(2))));
        successExpect("[0](2, 2)", new MatrixGeneratorNode(null, intlit(0), asList(intlit(2), intlit(2))));
        successExpect("true", new ReferenceNode(null, "true"));
        successExpect("false", new ReferenceNode(null, "false"));
        successExpect("null", new ReferenceNode(null, "null"));
        successExpect("!false", new UnaryExpressionNode(null, UnaryOperator.NOT, new ReferenceNode(null, "false")));
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericBinary () {
        rule = grammar.expression;
        successExpect("1 + 2", new BinaryExpressionNode(null, intlit(1), ADD, intlit(2)));
        successExpect("2 - 1", new BinaryExpressionNode(null, intlit(2), SUBTRACT,  intlit(1)));
        successExpect("2 * 3", new BinaryExpressionNode(null, intlit(2), MULTIPLY, intlit(3)));
        successExpect("2 / 3", new BinaryExpressionNode(null, intlit(2), DIVIDE, intlit(3)));
        successExpect("2 % 3", new BinaryExpressionNode(null, intlit(2), REMAINDER, intlit(3)));

        successExpect("1.0 + 2.0", new BinaryExpressionNode(null, floatlit(1), ADD, floatlit(2)));
        successExpect("2.0 - 1.0", new BinaryExpressionNode(null, floatlit(2), SUBTRACT, floatlit(1)));
        successExpect("2.0 * 3.0", new BinaryExpressionNode(null, floatlit(2), MULTIPLY, floatlit(3)));
        successExpect("2.0 / 3.0", new BinaryExpressionNode(null, floatlit(2), DIVIDE, floatlit(3)));
        successExpect("2.0 % 3.0", new BinaryExpressionNode(null, floatlit(2), REMAINDER, floatlit(3)));

        successExpect("2 * (4-1) * 4.0 / 6 % (2+1)", new BinaryExpressionNode(null,
            new BinaryExpressionNode(null,
                new BinaryExpressionNode(null,
                    new BinaryExpressionNode(null,
                        intlit(2),
                        MULTIPLY,
                        new ParenthesizedNode(null, new BinaryExpressionNode(null,
                            intlit(4),
                            SUBTRACT,
                            intlit(1)))),
                    MULTIPLY,
                    floatlit(4d)),
                DIVIDE,
                intlit(6)),
            REMAINDER,
            new ParenthesizedNode(null, new BinaryExpressionNode(null,
                intlit(2),
                ADD,
                intlit(1)))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayStructAccess () {
        rule = grammar.expression;
        successExpect("[1][0]", new ArrayAccessNode(null,
            new ArrayLiteralNode(null, asList(intlit(1))), intlit(0)));
        successExpect("[1].length", new FieldAccessNode(null,
            new ArrayLiteralNode(null, asList(intlit(1))), "length"));
        successExpect("p.x", new FieldAccessNode(null, new ReferenceNode(null, "p"), "x"));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testDeclarations() {
        rule = grammar.statement;

        successExpect("var x: Int = 1", new VarDeclarationNode(null,
            "x", new SimpleTypeNode(null, "Int"), intlit(1)));

        successExpect("struct P {}", new StructDeclarationNode(null, "P", asList()));

        successExpect("struct P { var x: Int; var y: Int }",
            new StructDeclarationNode(null, "P", asList(
                new FieldDeclarationNode(null, "x", new SimpleTypeNode(null, "Int")),
                new FieldDeclarationNode(null, "y", new SimpleTypeNode(null, "Int")))));

        successExpect("fun f (x: Int): Int { return 1 }",
            new FunDeclarationNode(null, "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "Int"))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, intlit(1))))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testStatements() {
        rule = grammar.statement;

        successExpect("return", new ReturnNode(null, null));
        successExpect("return 1", new ReturnNode(null, intlit(1)));
        successExpect("print(1)", new ExpressionStatementNode(null,
            new FunCallNode(null, new ReferenceNode(null, "print"), asList(intlit(1)))));
        successExpect("{ return }", new BlockNode(null, asList(new ReturnNode(null, null))));


        successExpect("if true return 1 else return 2", new IfNode(null, new ReferenceNode(null, "true"),
            new ReturnNode(null, intlit(1)),
            new ReturnNode(null, intlit(2))));

        successExpect("if false return 1 else if true return 2 else return 3 ",
            new IfNode(null, new ReferenceNode(null, "false"),
                new ReturnNode(null, intlit(1)),
                new IfNode(null, new ReferenceNode(null, "true"),
                    new ReturnNode(null, intlit(2)),
                    new ReturnNode(null, intlit(3)))));

        successExpect("while 1 < 2 { return } ", new WhileNode(null,
            new BinaryExpressionNode(null, intlit(1), LOWER, intlit(2)),
            new BlockNode(null, asList(new ReturnNode(null, null)))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testMatrixCreation() {
        rule = grammar.statement;

        successExpect("var m : Mat#Int = [[1, 2], [3, 4]]", new VarDeclarationNode(null,
            "m", new MatrixTypeNode(null, new SimpleTypeNode(null, "Int")),
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("var n : Mat#Int = [1](2, 2)", new VarDeclarationNode(null,
            "n", new MatrixTypeNode(null, new SimpleTypeNode(null, "Int")),
            new MatrixGeneratorNode(null, intlit(1),asList(intlit(2), intlit(2)))));

        successExpect("return [[1, 2], [3, 4]].shape", new ReturnNode(null, new FieldAccessNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            "shape")));

    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testMatrixShape() {
        rule = grammar.statement;

        successExpect("return [[1, 2], [3, 4]].shape", new ReturnNode(null, new FieldAccessNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            "shape")));

        successExpect("return [1](2, 2).shape", new ReturnNode(null, new FieldAccessNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            "shape")));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testIndexing() {
        rule = grammar.expression;

        successExpect("[1](2, 2)[1]", new ArrayAccessNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            intlit(1)));

        successExpect("[[1, 2], [3, 4]][1]", new ArrayAccessNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            intlit(1)));

        // array access already tested above
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testSlicing() {
        rule = grammar.expression;

        successExpect("[1](3, 3)[:1]", new SlicingAccessNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(3), intlit(3))),
            intlit(0), intlit(1)));

        successExpect("[1](3, 3)[1:]", new SlicingAccessNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(3), intlit(3))),
            intlit(1)));

        successExpect("[1](3, 3)[1:2]", new SlicingAccessNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(3), intlit(3))),
            intlit(1), intlit(2)));

        successExpect("[[1, 2], [3, 4]][:1]", new SlicingAccessNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            intlit(0), intlit(1)));

        successExpect("[[1, 2], [3, 4]][1:]", new SlicingAccessNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            intlit(1)));

        successExpect("[[1, 2], [3, 4]][1:2]", new SlicingAccessNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            intlit(1), intlit(2)));

        successExpect("[1, 2, 3][:1]", new SlicingAccessNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            intlit(0), intlit(1)));

        successExpect("[1, 2, 3][1:]", new SlicingAccessNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            intlit(1)));

        successExpect("[1, 2, 3][1:2]", new SlicingAccessNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            intlit(1), intlit(2)));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testMatrixOperator() {
        rule = grammar.expression;

        successExpect("[1](2, 2) =? [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            M_ONE_EQUAL,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) !=? [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            M_ONE_NOT_EQUAL,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) <=> [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            M_ALL_EQUAL,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) !<=> [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            M_ALL_NOT_EQUAL,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) <=? [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            M_ONE_LOWER_EQUAL,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) <<= [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            M_ALL_LOWER_EQUAL,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) >=? [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            M_ONE_GREATER_EQUAL,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) >>= [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            M_ALL_GREATER_EQUAL,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) << [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            M_ALL_LOWER,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) <? [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            M_ONE_LOWER,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) >> [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            M_ALL_GREATER,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) >? [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            M_ONE_GREATER,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        // --- without generator ---

        successExpect("[[1, 2], [3, 4]] =? [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            M_ONE_EQUAL,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] !=? [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            M_ONE_NOT_EQUAL,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] <=> [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            M_ALL_EQUAL,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] !<=> [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            M_ALL_NOT_EQUAL,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] <=? [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            M_ONE_LOWER_EQUAL,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] <<= [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            M_ALL_LOWER_EQUAL,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] >=? [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            M_ONE_GREATER_EQUAL,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] >>= [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            M_ALL_GREATER_EQUAL,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] << [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            M_ALL_LOWER,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] <? [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            M_ONE_LOWER,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] >> [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            M_ALL_GREATER,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] >? [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            M_ONE_GREATER,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayOperator() {
        rule = grammar.expression;

        successExpect("[1, 2, 3] =? [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            M_ONE_EQUAL,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] <=> [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            M_ALL_EQUAL,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] <=? [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            M_ONE_LOWER_EQUAL,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] <<= [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            M_ALL_LOWER_EQUAL,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] >=? [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            M_ONE_GREATER_EQUAL,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] >>= [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            M_ALL_GREATER_EQUAL,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] << [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            M_ALL_LOWER,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] <? [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            M_ONE_LOWER,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] >> [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            M_ALL_GREATER,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] >? [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            M_ONE_GREATER,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testMatrixArithmetic() {
        rule = grammar.expression;

        successExpect("[1](2, 2) + [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            ADD,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) - [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            SUBTRACT,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) / [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            DIVIDE,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) * [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            MULTIPLY,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) % [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            REMAINDER,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        successExpect("[1](2, 2) @ [1](2, 2)", new BinaryExpressionNode(null,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
            DOT_PRODUCT,
            new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2)))));

        // --- without generator ---

        successExpect("[[1, 2], [3, 4]] + [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            ADD,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] - [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            SUBTRACT,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] / [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            DIVIDE,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] * [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            MULTIPLY,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] % [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            REMAINDER,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));

        successExpect("[[1, 2], [3, 4]] @ [[1, 2], [3, 4]]", new BinaryExpressionNode(null,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4))))),
            DOT_PRODUCT,
            matlit(asList(arraylit(asList(intlit(1), intlit(2))), arraylit(asList(intlit(3), intlit(4)))))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayArithmetic() {
        rule = grammar.expression;

        successExpect("[1, 2, 3] + [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            ADD,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] - [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            SUBTRACT,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] / [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            DIVIDE,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] * [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            MULTIPLY,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));

        successExpect("[1, 2, 3] % [4, 5, 6]", new BinaryExpressionNode(null,
            arraylit(asList(intlit(1), intlit(2), intlit(3))),
            REMAINDER,
            arraylit(asList(intlit(4), intlit(5), intlit(6)))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testCaseStatement() {
        rule = grammar.statement;

        successExpect("case 2 {" +
                            "1 : {}," +
                            "_ : {}" +
                            "}",
            new CaseNode(null,
                intlit(2), asList(new CaseBodyNode(null, intlit(1), new BlockNode(null, asList())),
                                    new CaseBodyNode(null, new ReferenceNode(null, "_"), new BlockNode(null, asList()))),
                null));

        successExpect("case 2.5 {" +
                "1.2 : {}," +
                "_ : {}" +
                "}",
            new CaseNode(null,
                floatlit(2.5), asList(new CaseBodyNode(null, floatlit(1.2), new BlockNode(null, asList())),
                new CaseBodyNode(null, new ReferenceNode(null, "_"), new BlockNode(null, asList()))),
                null));

        successExpect("case [1, 2] {" +
                "[3, 4] : {}," +
                "[1] : {}," +
                "default : {}" +
                "}",
            new CaseNode(null,
                arraylit(asList(intlit(1), intlit(2))),
                asList(new CaseBodyNode(null, arraylit(asList(intlit(3), intlit(4))), new BlockNode(null, asList())),
                        new CaseBodyNode(null, arraylit(asList(intlit(1))), new BlockNode(null, asList()))),
                new BlockNode(null, asList())));

        successExpect("case [1](2, 2) {" +
                "[3](2, 2) : {}," +
                "[_](2, 2) : {}" +
                "}",
            new CaseNode(null,
                new MatrixGeneratorNode(null, intlit(1), asList(intlit(2), intlit(2))),
                asList(new CaseBodyNode(null, new MatrixGeneratorNode(null, intlit(3), asList(intlit(2), intlit(2))), new BlockNode(null, asList())),
                    new CaseBodyNode(null, new MatrixGeneratorNode(null, new ReferenceNode(null, "_"), asList(intlit(2), intlit(2))), new BlockNode(null, asList()))),
                null));

        successExpect("case \"aaa\" {" +
                "\"b\" : {}," +
                "\"a_a\" : {}," +
                "default : {}" +
                "}",
            new CaseNode(null,
                stringlit("aaa"),
                asList(new CaseBodyNode(null, stringlit("b"), new BlockNode(null, asList())),
                    new CaseBodyNode(null, stringlit("a_a"), new BlockNode(null, asList()))),
                new BlockNode(null, asList())));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testGenericFunction() {
        rule = grammar.statement;

        successExpect("fun f (x: T): T { return x }",
            new FunDeclarationNode(null, "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "T"))),
                new SimpleTypeNode(null, "T"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "x"))))));

        successExpect("fun f (x: T, y : U): Int { return 1 }",
            new FunDeclarationNode(null, "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "T")),
                    new ParameterNode(null, "y", new SimpleTypeNode(null, "U"))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, intlit(1))))));
    }

}
