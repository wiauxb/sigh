import norswap.autumn.AutumnTestFixture;
import norswap.autumn.Grammar;
import norswap.autumn.Grammar.rule;
import norswap.autumn.ParseResult;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.SighNode;
import norswap.sigh.interpreter.Interpreter;
import norswap.sigh.interpreter.InterpreterException;
import norswap.sigh.interpreter.Null;
import norswap.uranium.Reactor;
import norswap.uranium.SemanticError;
import norswap.utils.IO;
import norswap.utils.TestFixture;
import norswap.utils.data.wrappers.Pair;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;
import java.util.HashMap;
import java.util.Set;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;

public final class InterpreterTests extends TestFixture {

    // TODO peeling

    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final AutumnTestFixture autumnFixture = new AutumnTestFixture();

    {
        autumnFixture.runTwice = false;
        autumnFixture.bottomClass = this.getClass();
    }

    // ---------------------------------------------------------------------------------------------

    private Grammar.rule rule;

    // ---------------------------------------------------------------------------------------------

    private void check (String input, Object expectedReturn) {
        assertNotNull(rule, "You forgot to initialize the rule field.");
        check(rule, input, expectedReturn, null);
    }

    // ---------------------------------------------------------------------------------------------

    private void check (String input, Object expectedReturn, String expectedOutput) {
        assertNotNull(rule, "You forgot to initialize the rule field.");
        check(rule, input, expectedReturn, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void check (rule rule, String input, Object expectedReturn, String expectedOutput) {
        // TODO
        // (1) write proper parsing tests
        // (2) write some kind of automated runner, and use it here

        autumnFixture.rule = rule;
        ParseResult parseResult = autumnFixture.success(input);
        SighNode root = parseResult.topValue();

        Reactor reactor = new Reactor();
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        Interpreter interpreter = new Interpreter(reactor);
        walker.walk(root);
        reactor.run();
        Set<SemanticError> errors = reactor.errors();

        if (!errors.isEmpty()) {
            LineMapString map = new LineMapString("<test>", input);
            String report = reactor.reportErrors(it ->
                it.toString() + " (" + ((SighNode) it).span.startString(map) + ")");
            //            String tree = AttributeTreeFormatter.format(root, reactor,
            //                    new ReflectiveFieldWalker<>(SighNode.class, PRE_VISIT, POST_VISIT));
            //            System.err.println(tree);
            throw new AssertionError(report);
        }

        Pair<String, Object> result = IO.captureStdout(() -> interpreter.interpret(root));
        assertEquals(result.b, expectedReturn);
        if (expectedOutput != null) assertEquals(result.a, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkExpr (String input, Object expectedReturn, String expectedOutput) {
        rule = grammar.root;
        check("return " + input, expectedReturn, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkExpr (String input, Object expectedReturn) {
        rule = grammar.root;
        check("return " + input, expectedReturn);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkThrows (String input, Class<? extends Throwable> expected) {
        assertThrows(expected, () -> check(input, null));
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testLiteralsAndUnary () {
        checkExpr("42", 42L);
        checkExpr("42.0", 42.0d);
        checkExpr("\"hello\"", "hello");
        checkExpr("(42)", 42L);
        checkExpr("[1, 2, 3]", new Object[]{1L, 2L, 3L});
        checkExpr("[[1, 2, 3],[4, 5, 6]]", new Object[][]{
                                                        new Object[]{1L, 2L, 3L},
                                                        new Object[]{4L, 5L, 6L}});
        checkExpr("[0](3)", new Object[][]{ new Object[]{0L, 0L, 0L}});
        checkExpr("[0](2, 4)", new Object[][]{
                                            new Object[]{0L, 0L, 0L, 0L},
                                            new Object[]{0L, 0L, 0L, 0L}});
        checkExpr("true", true);
        checkExpr("false", false);
        checkExpr("null", Null.INSTANCE);
        checkExpr("!false", true);
        checkExpr("!true", false);
        checkExpr("!!true", true);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericBinary () {
        checkExpr("1 + 2", 3L);
        checkExpr("2 - 1", 1L);
        checkExpr("2 * 3", 6L);
        checkExpr("2 / 3", 0L);
        checkExpr("3 / 2", 1L);
        checkExpr("2 % 3", 2L);
        checkExpr("3 % 2", 1L);

        checkExpr("1.0 + 2.0", 3.0d);
        checkExpr("2.0 - 1.0", 1.0d);
        checkExpr("2.0 * 3.0", 6.0d);
        checkExpr("2.0 / 3.0", 2d / 3d);
        checkExpr("3.0 / 2.0", 3d / 2d);
        checkExpr("2.0 % 3.0", 2.0d);
        checkExpr("3.0 % 2.0", 1.0d);

        checkExpr("1 + 2.0", 3.0d);
        checkExpr("2 - 1.0", 1.0d);
        checkExpr("2 * 3.0", 6.0d);
        checkExpr("2 / 3.0", 2d / 3d);
        checkExpr("3 / 2.0", 3d / 2d);
        checkExpr("2 % 3.0", 2.0d);
        checkExpr("3 % 2.0", 1.0d);

        checkExpr("1.0 + 2", 3.0d);
        checkExpr("2.0 - 1", 1.0d);
        checkExpr("2.0 * 3", 6.0d);
        checkExpr("2.0 / 3", 2d / 3d);
        checkExpr("3.0 / 2", 3d / 2d);
        checkExpr("2.0 % 3", 2.0d);
        checkExpr("3.0 % 2", 1.0d);

        checkExpr("2 * (4-1) * 4.0 / 6 % (2+1)", 1.0d);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testOtherBinary () {
        checkExpr("true  && true",  true);
        checkExpr("true  || true",  true);
        checkExpr("true  || false", true);
        checkExpr("false || true",  true);
        checkExpr("false && true",  false);
        checkExpr("true  && false", false);
        checkExpr("false && false", false);
        checkExpr("false || false", false);

        checkExpr("1 + \"a\"", "1a");
        checkExpr("\"a\" + 1", "a1");
        checkExpr("\"a\" + true", "atrue");

        checkExpr("1 == 1", true);
        checkExpr("1 == 2", false);
        checkExpr("1.0 == 1.0", true);
        checkExpr("1.0 == 2.0", false);
        checkExpr("true == true", true);
        checkExpr("false == false", true);
        checkExpr("true == false", false);
        checkExpr("1 == 1.0", true);

        checkExpr("1 != 1", false);
        checkExpr("1 != 2", true);
        checkExpr("1.0 != 1.0", false);
        checkExpr("1.0 != 2.0", true);
        checkExpr("true != true", false);
        checkExpr("false != false", false);
        checkExpr("true != false", true);
        checkExpr("1 != 1.0", false);

        checkExpr("\"hi\" != \"hi2\"", true);

         // test short circuit
        checkExpr("true || print(\"x\") == \"y\"", true, "");
        checkExpr("false && print(\"x\") == \"y\"", false, "");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testVarDecl () {
        check("var x: Int = 1; return x", 1L);
        check("var x: Float = 2.0; return x", 2d);

        check("var x: Int = 0; return x = 3", 3L);
        check("var x: String = \"0\"; return x = \"S\"", "S");

        // implicit conversions
        check("var x: Float = 1; x = 2; return x", 2.0d);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testRootAndBlock () {
        rule = grammar.root;
        check("return", null);
        check("return 1", 1L);
        check("return 1; return 2", 1L);

        check("print(\"a\")", null, "a\n");
        check("print(\"a\" + 1)", null, "a1\n");
        check("print(\"a\"); print(\"b\")", null, "a\nb\n");

        check("{ print(\"a\"); print(\"b\") }", null, "a\nb\n");

        check(
            "var x: Int = 1;" +
            "{ print(\"\" + x); var x: Int = 2; print(\"\" + x) }" +
            "print(\"\" + x)",
            null, "1\n2\n1\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testCalls () {
        check(
            "fun add (a: Int, b: Int): Int { return a + b } " +
                "return add(4, 7)",
            11L);

        HashMap<String, Object> point = new HashMap<>();
        point.put("x", 1L);
        point.put("y", 2L);

        check(
            "struct Point { var x: Int; var y: Int }" +
                "return $Point(1, 2)",
            point);

        check("var str: String = null; return print(str + 1)", "null1", "null1\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testArrayStructAccess () {
        checkExpr("[1][0]", 1L);
        checkExpr("[1.0][0]", 1d);
        checkExpr("[1, 2][1]", 2L);

        checkExpr("[[1]][0]", new Object[]{1L});
        checkExpr("[[1.0]][0]", new Object[]{1d});
        checkExpr("[[1, 2], [3, 4]][1]", new Object[]{3L, 4L});

        // TODO check that this fails (& maybe improve so that it generates a better message?)
        // or change to make it legal (introduce a top type, and make it a top type array if thre
        // is no inference context available)
        // checkExpr("[].length", 0L);
        checkExpr("[1].length", 1L);
        checkExpr("[1, 2].length", 2L);

        checkThrows("var array: Int[] = null; return array[0]", NullPointerException.class);
        checkThrows("var array: Int[] = null; return array.length", NullPointerException.class);

        check("var x: Int[] = [0, 1]; x[0] = 3; return x[0]", 3L);
        checkThrows("var x: Int[] = []; x[0] = 3; return x[0]",
            ArrayIndexOutOfBoundsException.class);
        checkThrows("var x: Int[] = null; x[0] = 3",
            NullPointerException.class);

        check(
            "struct P { var x: Int; var y: Int }" +
                "return $P(1, 2).y",
            2L);

        checkThrows(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = null;" +
                "return p.y",
            NullPointerException.class);

        check(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = $P(1, 2);" +
                "p.y = 42;" +
                "return p.y",
            42L);

        checkThrows(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = null;" +
                "p.y = 42",
            NullPointerException.class);

        check(
                "var array: Int[] = [1, 2, 3, 4]"+
                "var arr: Int[] = array[:]"+
                "return arr",
            new Object[] {1L, 2L, 3L, 4L}
        );

        check(
            "var array: Int[] = [1, 2, 3, 4]"+
                "var arr: Int[] = array[1:]"+
                "return arr",
            new Object[] {2L, 3L, 4L}
        );

        check(
            "var array: Int[] = [1, 2, 3, 4]"+
                "var arr: Int[] = array[:2]"+
                "return arr",
            new Object[] {1L, 2L}
        );

        check(
            "var array: Int[] = [1, 2, 3, 4]"+
                "var arr: Int[] = array[1:3]"+
                "return arr",
            new Object[] {2L, 3L}
        );

        check(
            "var matrix: Mat#Int = [[1, 2, 3], [4, 5, 6], [7, 8, 9], [10, 11, 12]]"+
                "var mat: Mat#Int = matrix[:]"+
                "return mat",
            new Object[][] {new Object[]{1L, 2L, 3L},
                            new Object[]{4L, 5L, 6L},
                            new Object[]{7L, 8L, 9L},
                            new Object[]{10L, 11L, 12L}}
        );

        check(
            "var matrix: Mat#Int = [[1, 2, 3], [4, 5, 6], [7, 8, 9], [10, 11, 12]]"+
                "var mat: Mat#Int = matrix[2:]"+
                "return mat",
            new Object[][] {
                new Object[]{7L, 8L, 9L},
                new Object[]{10L, 11L, 12L}}
        );

        check(
            "var matrix: Mat#Int = [[1, 2, 3], [4, 5, 6], [7, 8, 9], [10, 11, 12]]"+
                "var mat: Mat#Int = matrix[:2]"+
                "return mat",
            new Object[][] {
                new Object[]{1L, 2L, 3L},
                new Object[]{4L, 5L, 6L}}
        );

        check(
            "var matrix: Mat#Int = [[1, 2, 3], [4, 5, 6], [7, 8, 9], [10, 11, 12]]"+
                "var mat: Mat#Int = matrix[1:3]"+
                "return mat",
            new Object[][] {
                new Object[]{4L, 5L, 6L},
                new Object[]{7L, 8L, 9L}}
        );

        checkThrows(
            "return [1](2, 2)[1000]",
            ArrayIndexOutOfBoundsException.class
        );

    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayMatrixSlicing(){

        checkExpr("[1, 2, 3, 4, 5, 6][:]", new Object[]{1L, 2L, 3L, 4L, 5L, 6L});
        checkExpr("[1, 2, 3, 4, 5, 6][:2]", new Object[]{1L, 2L});
        checkExpr("[1, 2, 3, 4, 5, 6][1:]", new Object[]{2L, 3L, 4L, 5L, 6L});
        checkExpr("[1, 2, 3, 4, 5, 6][1:2]", new Object[]{2L});

        checkExpr("[[1, 2, 3], [4, 5, 6], [7, 8, 9]][:]", new Object[][] {
                                                                new Object[]{1L, 2L, 3L},
                                                                new Object[]{4L, 5L, 6L},
                                                                new Object[]{7L, 8L, 9L}});
        checkExpr("[[1, 2, 3], [4, 5, 6], [7, 8, 9]][:2]", new Object[][]{
                                                                new Object[]{1L, 2L, 3L},
                                                                new Object[]{4L, 5L, 6L}});
        checkExpr("[[1, 2, 3], [4, 5, 6], [7, 8, 9]][1:]", new Object[][]{
                                                                new Object[]{4L, 5L, 6L},
                                                                new Object[]{7L, 8L, 9L}});
        checkExpr("[[1, 2, 3], [4, 5, 6], [7, 8, 9]][1:2]", new Object[][]{
                                                                new Object[]{4L, 5L, 6L}});
        checkThrows(
            "return [1](2, 2)[:1000]",
            InterpreterException.class
        );

        checkThrows(
            "return [1](2, 2)[1000:]",
            InterpreterException.class
        );
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testIfWhile () {
        check("if (true) return 1 else return 2", 1L);
        check("if (false) return 1 else return 2", 2L);
        check("if (false) return 1 else if (true) return 2 else return 3 ", 2L);
        check("if (false) return 1 else if (false) return 2 else return 3 ", 3L);

        check("var i: Int = 0; while (i < 3) { print(\"\" + i); i = i + 1 } ", null, "0\n1\n2\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testInference () {
        check("var array: Int[] = []", null);
        check("var array: String[] = []", null);
        check("fun use_array (array: Int[]) {} ; use_array([])", null);
        //FIXME is this inference ?
        check("var matrix: Mat#Int = [[1]]", null);
        check("var matrix: Mat#String = [[\"Hello\"]]", null);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testTypeAsValues () {
        check("struct S{} ; return \"\"+ S", "S");
        check("struct S{} ; var type: Type = S ; return \"\"+ type", "S");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testUnconditionalReturn()
    {
        check("fun f(): Int { if (true) return 1 else return 2 } ; return f()", 1L);
    }

    // ---------------------------------------------------------------------------------------------

    // NOTE(norswap): Not incredibly complete, but should cover the basics.

    // ---------------------------------------------------------------------------------------------

    @Test public void testMatrixArithmetic() {

        checkExpr("[[1]] + [[2]]", new Object[][]{new Object[]{3L}});
        checkExpr("[[1]] - [[2]]", new Object[][]{new Object[]{-1L}});
        checkExpr("[[1]] / [[2]]", new Object[][]{new Object[]{0L}});
        checkExpr("[[1]] * [[2]]", new Object[][]{new Object[]{2L}});
        checkExpr("[[1]] @ [[2]]", new Object[][]{new Object[]{2L}});

        checkExpr("[[1.0]] + [[2.0]]", new Object[][]{new Object[]{3.0d}});
        checkExpr("[[1.0]] - [[2.0]]", new Object[][]{new Object[]{-1.0d}});
        checkExpr("[[1.0]] / [[2.0]]", new Object[][]{new Object[]{0.5d}});
        checkExpr("[[1.0]] * [[2.0]]", new Object[][]{new Object[]{2.0d}});
        checkExpr("[[1.0]] @ [[2.0]]", new Object[][]{new Object[]{2.0d}});

        checkExpr("[[1]] + [[2.0]]", new Object[][]{new Object[]{3.0d}});
        checkExpr("[[1]] - [[2.0]]", new Object[][]{new Object[]{-1.0d}});
        checkExpr("[[1]] / [[2.0]]", new Object[][]{new Object[]{0.5d}});
        checkExpr("[[1]] * [[2.0]]", new Object[][]{new Object[]{2.0d}});
        checkExpr("[[1]] @ [[2.0]]", new Object[][]{new Object[]{2.0d}});
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayArithmetic() {

        checkExpr("[1] + [2]", new Object[][]{new Object[]{3L}});
        checkExpr("[1] - [2]", new Object[][]{new Object[]{-1L}});
        checkExpr("[1] / [2]", new Object[][]{new Object[]{0L}});
        checkExpr("[1] * [2]", new Object[][]{new Object[]{2L}});
        checkExpr("[1] @ [2]", new Object[][]{new Object[]{2L}});

        checkExpr("[1.0] + [2.0]", new Object[][]{new Object[]{3.0d}});
        checkExpr("[1.0] - [2.0]", new Object[][]{new Object[]{-1.0d}});
        checkExpr("[1.0] / [2.0]", new Object[][]{new Object[]{0.5d}});
        checkExpr("[1.0] * [2.0]", new Object[][]{new Object[]{2.0d}});
        checkExpr("[1.0] @ [2.0]", new Object[][]{new Object[]{2.0d}});

        checkExpr("[1] + [2.0]", new Object[][]{new Object[]{3.0d}});
        checkExpr("[1] - [2.0]", new Object[][]{new Object[]{-1.0d}});
        checkExpr("[1] / [2.0]", new Object[][]{new Object[]{0.5d}});
        checkExpr("[1] * [2.0]", new Object[][]{new Object[]{2.0d}});
        checkExpr("[1] @ [2.0]", new Object[][]{new Object[]{2.0d}});

    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testMatrixOperator() {

        checkExpr("[[1], [2]] =? [[1], [3]]", true);
        checkExpr("[[1], [2]] =? [[3], [4]]", false);
        checkExpr("[[1], [2]] !=? [[3], [4]]", true);
        checkExpr("[[1], [2]] !=? [[1], [2]]", false);
        checkExpr("[[1], [2]] <=> [[1], [2]]", true);
        checkExpr("[[1], [2]] <=> [[1], [3]]", false);
        checkExpr("[[1], [2]] !<=> [[4], [3]]", true);
        checkExpr("[[1], [2]] !<=> [[1], [2]]", false);
        checkExpr("[[1], [2]] <=? [[1], [1]]", true);
        checkExpr("[[3], [2]] <=? [[1], [1]]", false);
        checkExpr("[[1], [2]] <<= [[2], [4]]", true);
        checkExpr("[[5], [6]] <<= [[1], [2]]", false);
        checkExpr("[[5], [1]] >=? [[1], [5]]", true);
        checkExpr("[[1], [2]] >=? [[5], [6]]", false);
        checkExpr("[[1], [2]] >>= [[0], [2]]", true);
        checkExpr("[[1], [2]] >>= [[2], [3]]", false);
        checkExpr("[[1], [2]] << [[2], [3]]", true);
        checkExpr("[[2], [3]] << [[1], [2]]", false);
        checkExpr("[[1], [2]] <? [[2], [1]]", true);
        checkExpr("[[1], [2]] <? [[1], [2]]", false);
        checkExpr("[[1], [2]] >> [[0], [1]]", true);
        checkExpr("[[1], [2]] >> [[1], [3]]", false);
        checkExpr("[[1], [2]] >? [[0], [3]]", true);
        checkExpr("[[1], [2]] >? [[2], [2]]", false);

        checkThrows("[[1, 2, 3]] >> [[1, 2]]", Error.class);
        checkThrows("[[1, 2, 3]] >> 2", Error.class);

    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayOperator() {

        checkExpr("[1, 2] =? [1, 3]", true);
        checkExpr("[1, 2] =? [3, 4]", false);
        checkExpr("[1, 2] !=? [3, 4]", true);
        checkExpr("[1, 2] !=? [1, 2]", false);
        checkExpr("[1, 2] <=> [1, 2]", true);
        checkExpr("[1, 2] <=> [1, 3]", false);
        checkExpr("[1, 2] !<=> [4, 3]", true);
        checkExpr("[1, 2] !<=> [1, 2]", false);
        checkExpr("[1, 2] <=? [1, 1]", true);
        checkExpr("[3, 2] <=? [1, 1]", false);
        checkExpr("[1, 2] <<= [2, 4]", true);
        checkExpr("[5, 6] <<= [1, 2]", false);
        checkExpr("[5, 1] >=? [1, 5]", true);
        checkExpr("[1, 2] >=? [5, 6]", false);
        checkExpr("[1, 2] >>= [0, 2]", true);
        checkExpr("[1, 2] >>= [2, 3]", false);
        checkExpr("[1, 2] << [2, 3]", true);
        checkExpr("[2, 3] << [1, 2]", false);
        checkExpr("[1, 2] <? [2, 1]", true);
        checkExpr("[1, 2] <? [1, 2]", false);
        checkExpr("[1, 2] >> [0, 1]", true);
        checkExpr("[1, 2] >> [1, 3]", false);
        checkExpr("[1, 2] >? [0, 3]", true);
        checkExpr("[1, 2] >? [2, 2]", false);

        checkThrows("[1] >? [1, 2]", Error.class);
        checkThrows("[1, 2] > 2", Error.class);

    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testVectorizedFunction() {
        rule = grammar.root;

        check("fun bigTester (a : Int, b: Int, c: Float): Float {" +
            "    if (a > b && a > c)" +
            "        return a" +
            "    else if (b > a && b > c)" +
            "        return b" +
            "    else" +
            "        return c" +
            "}" +
            "var mat1: Mat#Int = [[6, 7, 8], [0, 0, 0], [-1, -2, -3]]" +
            "var mat2: Mat#Int = [[0, 0, 0], [3, 4, 5], [-1, -2, -3]]" +
            "var mat3: Mat#Int = [[1, 2, 3], [2, 3, 4], [1, 2, 3]]" +
            "return bigTester(mat1, mat2, mat3)", new Object[][]{new Object[]{6L, 7L, 8L},
                                                                 new Object[]{3L, 4L, 5L},
                                                                 new Object[]{1L, 2L, 3L}});

        checkThrows("fun fail(a : Int, b : Int) : Int {" +
            "   return a + b " +
            "}" +
            "return fail([1](2, 2), [3](5, 5))", InterpreterException.class);

    }//[[6, 7, 8], [3, 4, 5], [1, 2, 3]]


    // ---------------------------------------------------------------------------------------------

    @Test public void testCaseStatement() {
        rule = grammar.root;

        check("case 2 {" +
            "1 : {return 1}," +
            "2 : {return 2}," +
            "default : {return -1}}", 2L);

        check("case 2.5 {" +
            "1.2 : {return 1}," +
            "3.1 : {return 2}," +
            "_ : {return 3}}", 3L);

        check("case [1, 2, 3] {" +
            "[1, 2] : {return 1}," +
            "[1] : {return 2}," +
            "[1, 2, 3] : {return 3}," +
            "default : {return 4}}", 3L);

        check("case [1, 2, 3, 4, 5] {" +
            "[1, 2] : {return 1}," +
            "[1, _] : {return 2}," +
            "default : {return 3}}", 2L);

        check("case [1, 2, 3, 4, 5] {" +
            "[1, 2, _, 5] : {return 1}," +
            "[1, 2, 3, 4, 5] : {return 2}," +
            "default : {return 3}}", 1L);

        check("case [1, 2, 3, 4, 5] {" +
            "[_, 9] : {return 1}," +
            "[_, 1] : {return 2}," +
            "[_, 5] : {return 3}," +
            "default : {return 4}}", 3L);

        check("case [1](2, 2) {" +
            "[[1, 2], [1, 2]] : {return 1}," +
            "[[1, 1], [1, 1]] : {return 2}," +
            "default : {return 3}}", 2L);

        check("case [1](2, 2) {" +
            "[[2, 2], _] : {return 1}," +
            "[[1, 1], _] : {return 2}," +
            "default : {return 3}}", 2L);

        check("case [2](2, 2) {" +
            "[_, [1, 1]] : {return 1}," +
            "[[2, _], [2, 1]] : {return 2}," +
            "[[2, _], _] : {return 3}," +
            "default : {return 4}}", 3L);

        check("case [1](2, 2) {" +
            "[_, [1, 1]] : {return 1}," +
            "[[1, 1, 1], [1, 1, 1], [1, 1, 1]] : {return 2}," +
            "default : {return 3}}", 1L);

        check("case [2](2, 3).shape {" +
            "[1, 1] : {return 1}," +
            "[2, 2] : {return 2}," +
            "[3, 3] : {return 3}," +
            "[2, 3] : {return 4}," +
            "default : {return 5}}", 4L);

        check("case [1](2, 2) {" +
            "    [_, [1, _]] : {" +
            "        return 1" +
            "    },\n" +
            "    default : {\n" +
            "        return 2" +
            "    }\n" +
            "}", 1L);
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testGenericType() {
        rule = grammar.root;

        // simple cases

        check("fun test1(a : T) : T {" +
            "return a + 1" +
            "}" +
            "var i : Int = 3" +
            "return test1(i)", 4L);

        check("fun test1(a : T) : T {" +
            "return a + 1" +
            "}" +
            "var i : Float = 3.5" +
            "return test1(i)", 4.5d);

        check("fun test1(a : T) : T {" +
            "return a + 1" +
            "}" +
            "var i : Int[] = [1, 2]" +
            "return test1(i)", new Object[][]{new Object[]{2L, 3L}});

        check("fun test1(a : T) : T {" +
            "return a + 1" +
            "}" +
            "var i : Float[] = [1.5, 2.5]" +
            "return test1(i)", new Object[][]{new Object[]{2.5d, 3.5d}});

        check("fun test1(a : T) : T {" +
            "return a + 1" +
            "}" +
            "var i : Mat#Int = [1](2, 2)" +
            "return test1(i)", new Object[][]{new Object[]{2L, 2L}, new Object[]{2L, 2L}});

        check("fun test1(a : T) : T {" +
            "return a + 1" +
            "}" +
            "var i : Mat#Float = [1.5](2, 2)" +
            "return test1(i)", new Object[][]{new Object[]{2.5d, 2.5d}, new Object[]{2.5d, 2.5d}});


        // test with more than one generic type

        check("fun test2(a : T, b : U) : T {" +
            "return a + b" +
            "}" +
            "var i : Int[] = [1, 2, 3]" +
            "var j : Int = 2" +
            "return test2(i, j)", new Object[][]{new Object[]{3L, 4L, 5L}});

        check("fun test2(a : T, b : U) : T {" +
            "return a + b" +
            "}" +
            "var i : Int = 4" +
            "var j : Int = 2" +
            "return test2(i, j)", 6L);

        check("fun test2(a : T, b : U) : T {" +
            "return a + b" +
            "}" +
            "var i : Mat#Int = [0](2, 2)" +
            "var j : Int = 2" +
            "return test2(i, j)", new Object[][]{new Object[]{2L, 2L}, new Object[]{2L, 2L}});


        // test with using generic type in the function

        check("fun test3(a : T, b : U) : T {" +
            "var c : T = a + b " +
            "return c" +
            "}" +
            "var i : Int = 1" +
            "var j : Int = 2" +
            "return test3(i, j)", 3L);

        check("fun test3(a : T, b : U) : T {" +
            "var c : T = a + b " +
            "return c" +
            "}" +
            "var i : Int[] = [1, 2]" +
            "var j : Int = 2" +
            "return test3(i, j)", new Object[][]{new Object[]{3L, 4L}});

        check("fun test3(a : T, b : U) : T {" +
            "var c : T = a + b " +
            "return c" +
            "}" +
            "var i : Mat#Int = [1](2, 2)" +
            "var j : Int = 2" +
            "return test3(i, j)", new Object[][]{new Object[]{3L, 3L}, new Object[]{3L, 3L}});

        check("fun test3(a : T, b : U) : T {" +
            "var c : T = a + b " +
            "return c" +
            "}" +
            "var i : Mat#Int = [1](2, 2)" +
            "var j : Mat#Int = [[1, 2], [3, 4]]" +
            "return test3(i, j)", new Object[][]{new Object[]{2L, 3L}, new Object[]{4L, 5L}});


        // test with second generic type as return type

        check("fun test4(a : T, b : U) : U {" +
            "var c : U = b " +
            "return c" +
            "}" +
            "var i : Int = 1" +
            "var j : Int = 2" +
            "return test4(i, j)", 2L);

        check("fun test4(a : T, b : U) : U {" +
            "var c : U = b " +
            "return c" +
            "}" +
            "var i : Int = 1" +
            "var j : Int[] = [1, 2]" +
            "return test4(i, j)", new Object[]{1L, 2L});

        check("fun test4(a : T, b : U) : U {" +
            "var c : U = b " +
            "return c" +
            "}" +
            "var i : Int = 1" +
            "var j : Mat#Int = [1](2, 2)" +
            "return test4(i, j)", new Object[][]{new Object[]{1L, 1L}, new Object[]{1L, 1L}});


        // test using both generic type in the function

        check("fun test5(a : T, b : U) : U {" +
            "var c : T = a + 1" +
            "var d : U = b + c " +
            "return d" +
            "}" +
            "var i : Int = 1" +
            "var j : Int = 2" +
            "return test5(i, j)", 4L);

        check("fun test5(a : T, b : U) : U {" +
            "var c : T = a + 1" +
            "var d : U = b + c " +
            "return d" +
            "}" +
            "var i : Int = 1" +
            "var j : Float = 2.5" +
            "return test5(i, j)", 4.5d);


    }
}
