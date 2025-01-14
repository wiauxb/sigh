package norswap.sigh;

import norswap.sigh.ast.*;
import norswap.sigh.scopes.DeclarationContext;
import norswap.sigh.scopes.DeclarationKind;
import norswap.sigh.scopes.RootScope;
import norswap.sigh.scopes.Scope;
import norswap.sigh.scopes.SyntheticDeclarationNode;
import norswap.sigh.types.*;
import norswap.uranium.Attribute;
import norswap.uranium.Reactor;
import norswap.uranium.Rule;
import norswap.uranium.SemanticError;
import norswap.utils.visitors.ReflectiveFieldWalker;
import norswap.utils.visitors.Walker;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static norswap.sigh.ast.BinaryOperator.*;
import static norswap.utils.Util.cast;
import static norswap.utils.Vanilla.forEachIndexed;
import static norswap.utils.Vanilla.list;
import static norswap.utils.visitors.WalkVisitType.POST_VISIT;
import static norswap.utils.visitors.WalkVisitType.PRE_VISIT;

/**
 * Holds the logic implementing semantic analyzis for the language, including typing and name
 * resolution.
 *
 * <p>The entry point into this class is {@link #createWalker(Reactor)}.
 *
 * <h2>Big Principles
 * <ul>
 *     <li>Every {@link DeclarationNode} instance must have its {@code type} attribute to an
 *     instance of {@link Type} which is the type of the value declared (note that for struct
 *     declaration, this is always {@link TypeType}.</li>
 *
 *     <li>Additionally, {@link StructDeclarationNode} (and default
 *     {@link SyntheticDeclarationNode} for types) must have their {@code declared} attribute set to
 *     an instance of the type being declared.</li>
 *
 *     <li>Every {@link ExpressionNode} instance must have its {@code type} attribute similarly
 *     set.</li>
 *
 *     <li>Every {@link ReferenceNode} instance must have its {@code decl} attribute set to the the
 *     declaration it references and its {@code scope} attribute set to the {@link Scope} in which
 *     the declaration it references lives. This speeds up lookups in the interpreter and simplifies the compiler.</li>
 *
 *     <li>For the same reasons, {@link VarDeclarationNode} and {@link ParameterNode} should have
 *     their {@code scope} attribute set to the scope in which they appear (this also speeds up the
 *     interpreter).</li>
 *
 *     <li>All statements introducing a new scope must have their {@code scope} attribute set to the
 *     corresponding {@link Scope} (only {@link RootNode}, {@link BlockNode} and {@link
 *     FunDeclarationNode} (for parameters)). These nodes must also update the {@code scope}
 *     field to track the current scope during the walk.</li>
 *
 *     <li>Every {@link TypeNode} instance must have its {@code value} set to the {@link Type} it
 *     denotes.</li>
 *
 *     <li>Every {@link ReturnNode}, {@link BlockNode} and {@link IfNode} must have its {@code
 *     returns} attribute set to a boolean to indicate whether its execution causes
 *     unconditional exit from the surrounding function or main script.</li>
 *
 *     <li>The rules check typing constraints: assignment of values to variables, of arguments to
 *     parameters, checking that if/while conditions are booleans, and array indices are
 *     integers.</li>
 *
 *     <li>The rules also check a number of other constraints: that accessed struct fields exist,
 *     that variables are declared before being used, etc...</li>
 * </ul>
 */
public final class SemanticAnalysis
{
    // =============================================================================================
    // region [Initialization]
    // =============================================================================================

    private final Reactor R;

    /** Current scope. */
    private Scope scope;

    /** Current context for type inference (currently only to infer the type of empty arrays). */
    private SighNode inferenceContext;

    /** Index of the current function argument. */
    private int argumentIndex;

    // ---------------------------------------------------------------------------------------------

    private SemanticAnalysis(Reactor reactor) {
        this.R = reactor;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Call this method to create a tree walker that will instantiate the typing rules defined
     * in this class when used on an AST, using the given {@code reactor}.
     */
    public static Walker<SighNode> createWalker (Reactor reactor)
    {
        ReflectiveFieldWalker<SighNode> walker = new ReflectiveFieldWalker<>(
            SighNode.class, PRE_VISIT, POST_VISIT);

        SemanticAnalysis analysis = new SemanticAnalysis(reactor);

        // expressions
        walker.register(IntLiteralNode.class,           PRE_VISIT,  analysis::intLiteral);
        walker.register(FloatLiteralNode.class,         PRE_VISIT,  analysis::floatLiteral);
        walker.register(StringLiteralNode.class,        PRE_VISIT,  analysis::stringLiteral);
        walker.register(ReferenceNode.class,            PRE_VISIT,  analysis::reference);
        walker.register(ConstructorNode.class,          PRE_VISIT,  analysis::constructor);
        walker.register(ArrayLiteralNode.class,         PRE_VISIT,  analysis::arrayLiteral);
        walker.register(ParenthesizedNode.class,        PRE_VISIT,  analysis::parenthesized);
        walker.register(FieldAccessNode.class,          PRE_VISIT,  analysis::fieldAccess);
        walker.register(ArrayAccessNode.class,          PRE_VISIT,  analysis::arrayAccess);
        walker.register(SlicingAccessNode.class,        PRE_VISIT,  analysis::slicingAccess);
        walker.register(FunCallNode.class,              PRE_VISIT,  analysis::funCall);
        walker.register(UnaryExpressionNode.class,      PRE_VISIT,  analysis::unaryExpression);
        walker.register(BinaryExpressionNode.class,     PRE_VISIT,  analysis::binaryExpression);
        walker.register(AssignmentNode.class,           PRE_VISIT,  analysis::assignment);
        walker.register(MatrixLiteralNode.class,        PRE_VISIT,  analysis::matrixLiteral);
        walker.register(MatrixGeneratorNode.class,      PRE_VISIT,  analysis::matrixGenerator);

        // types
        walker.register(SimpleTypeNode.class,           PRE_VISIT,  analysis::simpleType);
        walker.register(ArrayTypeNode.class,            PRE_VISIT,  analysis::arrayType);
        walker.register(MatrixTypeNode.class,           PRE_VISIT,  analysis::matrixType);

        // declarations & scopes
        walker.register(RootNode.class,                 PRE_VISIT,  analysis::root);
        walker.register(BlockNode.class,                PRE_VISIT,  analysis::block);
        walker.register(VarDeclarationNode.class,       PRE_VISIT,  analysis::varDecl);
        walker.register(FieldDeclarationNode.class,     PRE_VISIT,  analysis::fieldDecl);
        walker.register(ParameterNode.class,            PRE_VISIT,  analysis::parameter);
        walker.register(FunDeclarationNode.class,       PRE_VISIT,  analysis::funDecl);
        walker.register(StructDeclarationNode.class,    PRE_VISIT,  analysis::structDecl);

        walker.register(CaseBodyNode.class,             PRE_VISIT,  node -> {});

        walker.register(RootNode.class,                 POST_VISIT, analysis::popScope);
        walker.register(BlockNode.class,                POST_VISIT, analysis::popScope);
        walker.register(FunDeclarationNode.class,       POST_VISIT, analysis::popScope);
        walker.register(CaseNode.class,                 POST_VISIT, analysis::popScope);

        // statements
        walker.register(ExpressionStatementNode.class,  PRE_VISIT,  node -> {});
        walker.register(IfNode.class,                   PRE_VISIT,  analysis::ifStmt);
        walker.register(WhileNode.class,                PRE_VISIT,  analysis::whileStmt);
        walker.register(CaseNode.class,                 PRE_VISIT,  analysis::caseStmt);
        walker.register(ReturnNode.class,               PRE_VISIT,  analysis::returnStmt);

        walker.registerFallback(POST_VISIT, node -> {});

        return walker;
    }

    // endregion
    // =============================================================================================
    // region [Expressions]
    // =============================================================================================

    private void intLiteral (IntLiteralNode node) {
        R.set(node, "type", IntType.INSTANCE);
    }

    // ---------------------------------------------------------------------------------------------

    private void floatLiteral (FloatLiteralNode node) {
        R.set(node, "type", FloatType.INSTANCE);
    }

    // ---------------------------------------------------------------------------------------------

    private void stringLiteral (StringLiteralNode node) {
        R.set(node, "type", StringType.INSTANCE);
    }

    // ---------------------------------------------------------------------------------------------

    private void reference (ReferenceNode node)
    {
        final Scope scope = this.scope;

        // Try to lookup immediately. This must succeed for variables, but not necessarily for
        // functions or types. By looking up now, we can report looked up variables later
        // as being used before being defined.
        DeclarationContext maybeCtx = scope.lookup(node.name);

        if (maybeCtx != null) {
            R.set(node, "decl",  maybeCtx.declaration);
            R.set(node, "scope", maybeCtx.scope);

            R.rule(node, "type")
            .using(maybeCtx.declaration, "type")
            .by(Rule::copyFirst);
            return;
        }

        // Re-lookup after the scopes have been built.
        R.rule(node.attr("decl"), node.attr("scope"))
        .by(r -> {
            DeclarationContext ctx = scope.lookup(node.name);
            DeclarationNode decl = ctx == null ? null : ctx.declaration;

            if (ctx == null) {
                r.errorFor("Could not resolve: " + node.name,
                    node, node.attr("decl"), node.attr("scope"), node.attr("type"));
            }
            else {
                r.set(node, "scope", ctx.scope);
                r.set(node, "decl", decl);

                if (decl instanceof VarDeclarationNode)
                    r.errorFor("Variable used before declaration: " + node.name,
                        node, node.attr("type"));
                else
                    R.rule(node, "type")
                    .using(decl, "type")
                    .by(Rule::copyFirst);
            }
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void constructor (ConstructorNode node)
    {
        R.rule()
        .using(node.ref, "decl")
        .by(r -> {
            DeclarationNode decl = r.get(0);

            if (!(decl instanceof StructDeclarationNode)) {
                String description =
                        "Applying the constructor operator ($) to non-struct reference for: "
                        + decl;
                r.errorFor(description, node, node.attr("type"));
                return;
            }

            StructDeclarationNode structDecl = (StructDeclarationNode) decl;

            Attribute[] dependencies = new Attribute[structDecl.fields.size() + 1];
            dependencies[0] = decl.attr("declared");
            forEachIndexed(structDecl.fields, (i, field) ->
                dependencies[i + 1] = field.attr("type"));

            R.rule(node, "type")
            .using(dependencies)
            .by(rr -> {
                Type structType = rr.get(0);
                Type[] params = IntStream.range(1, dependencies.length).<Type>mapToObj(rr::get)
                        .toArray(Type[]::new);
                rr.set(0, new FunType(structType, params));
            });
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void arrayLiteral (ArrayLiteralNode node)
    {
        if (node.components.size() == 0) { // []
            // Empty array: we need a type int to know the desired type.

            final SighNode context = this.inferenceContext;

            if (context instanceof VarDeclarationNode)
                R.rule(node, "type")
                    .using(context, "type")
                    .by(Rule::copyFirst);
            else if (context instanceof SymbolicVarDeclarationNode)
                R.rule(node, "type")
                    .using(context, "type")
                    .by(Rule::copyFirst);
            else if (context instanceof FunCallNode) {
                R.rule(node, "type")
                    .using(((FunCallNode) context).function.attr("type"), node.attr("index"))
                    .by(r -> {
                        FunType funType = r.get(0);
                        r.set(0, funType.paramTypes[(int) r.get(1)]);
                    });
            }
            else if (context instanceof CaseNode){
                R.rule(node, "type")
                    .using(context, "type")
                    .by(Rule::copyFirst);
            }
            return;
        }

        Attribute[] dependencies =
            node.components.stream().map(it -> it.attr("type")).toArray(Attribute[]::new);

        R.rule(node, "type")
            .using(dependencies)
            .by(r -> {
                Type[] types = IntStream.range(0, dependencies.length).<Type>mapToObj(r::get)
                    .distinct().toArray(Type[]::new);

                int i = 0;
                Type supertype = null;
                for (Type type: types) {
                    if (type instanceof VoidType)
                        // We report the error, but compute a type for the array from the other elements.
                        r.errorFor("Void-valued expression in array literal", node.components.get(i));
                    else if (supertype == null)
                        supertype = type;
                    else {
                        supertype = commonSupertype(supertype, type);
                        if (supertype == null) {
                            r.error("Could not find common supertype in array literal.", node);
                            return;
                        }
                    }
                    ++i;
                }

                if (supertype == null)
                    r.error(
                        "Could not find common supertype in array literal: all members have Void type.",
                        node);
                else
                    r.set(0, new ArrayType(supertype));
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void matrixLiteral (MatrixLiteralNode node)
    {
        if (node.components.size() == 0) { // [[]]
            // Empty array: we need a type int to know the desired type.
            R.error(new SemanticError("Cannot create empty matrix", null, node));
        }

        int shape2 = -1;
        for (ArrayLiteralNode line : node.components) {
            if (shape2 == -1)
                shape2 = line.components.size();
            else if (shape2 != line.components.size())
                R.error(new SemanticError("Matrices must have constant line lengths", null, node));
        }


        Attribute[] dependencies =
            node.components.stream().map(it -> it.attr("type")).toArray(Attribute[]::new);

        R.rule(node, "type")
            .using(dependencies)
            .by(r -> {
                ArrayType[] types = IntStream.range(0, dependencies.length).<ArrayType>mapToObj(r::get)
                    .distinct().toArray(ArrayType[]::new);


                int i = 0;
                ArrayType supertype = null;
                for (ArrayType type: types) {
//                    if (type instanceof VoidType)
//                        // We report the error, but compute a type for the array from the other elements.
//                        r.errorFor("Void-valued expression in matrix literal", node.components.get(i));
                    if (supertype == null)
                        supertype = type;
                    else {
                        supertype = (ArrayType) commonSupertype(supertype, type);
                        if (supertype == null) {
                            r.error("Could not find common supertype in matrix literal.", node);
                            return;
                        }
                    }
                    ++i;
                }

                if (supertype == null)
                    r.error(
                        "Could not find common supertype in array literal: all members have Void type.",
                        node);
                else
                    r.set(0, new MatType(supertype.componentType));
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void matrixGenerator (MatrixGeneratorNode node)
    {
        R.rule()
            .using(node.shape.get(0).attr("type"),
                        node.shape.get(1).attr("type"))
            .by(r -> {
                if (node.shape.size() > 2){
                    r.error(format("Too many arguments for matrixGenerator, expected 1 or 2 but got %s", node.shape.size()), node);
                }

                if (!(r.get(0) instanceof IntType && r.get(1) instanceof IntType))
                    r.error("Invalid shape type", node);
            }
            );

        R.rule(node, "type")
            .using(node.filler.attr("type"))
            .by(r -> {
                Type supertype = r.get(0);
                if (supertype.isArrayLike())
                    r.error("Invalid filler type", node);
                else
                    r.set(0, new MatType(supertype));
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void parenthesized (ParenthesizedNode node)
    {
        R.rule(node, "type")
        .using(node.expression, "type")
        .by(Rule::copyFirst);
    }

    // ---------------------------------------------------------------------------------------------

    private void fieldAccess (FieldAccessNode node)
    {
        R.rule()
        .using(node.stem, "type")
        .by(r -> {
            Type type = r.get(0);

            if (type instanceof ArrayType) {
                if (node.fieldName.equals("length"))
                    R.rule(node, "type")
                    .by(rr -> rr.set(0, IntType.INSTANCE));
                else
                    r.errorFor("Trying to access a non-length field on an array", node,
                        node.attr("type"));
                return;
            }

            // matrix shape function
            if (type instanceof MatType) {
                if (node.fieldName.equals("shape"))
                    R.rule(node, "type")
                        .by(rr -> rr.set(0, new ArrayType(IntType.INSTANCE)));
                else
                    r.errorFor("Trying to access an unknown field on a matrix", node,
                        node.attr("type"));
                return;
            }
            
            if (!(type instanceof StructType)) {
                r.errorFor("Trying to access a field on an expression of type " + type,
                        node,
                        node.attr("type"));
                return;
            }

            StructDeclarationNode decl = ((StructType) type).node;

            for (DeclarationNode field: decl.fields)
            {
                if (!field.name().equals(node.fieldName)) continue;

                R.rule(node, "type")
                .using(field, "type")
                .by(Rule::copyFirst);

                return;
            }

            String description = format("Trying to access missing field %s on struct %s",
                    node.fieldName, decl.name);
            r.errorFor(description, node, node.attr("type"));
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void arrayAccess (ArrayAccessNode node)
    {
        R.rule()
        .using(node.index, "type")
        .by(r -> {
            Type type = r.get(0);
            if (!(type instanceof IntType))
                r.error("Indexing an array using a non-Int-valued expression", node.index);
        });

        R.rule(node, "type")
        .using(node.array, "type")
        .by(r -> {
            Type type = r.get(0);
            if (type instanceof ArrayType)
                r.set(0, ((ArrayType) type).componentType);
            else if (type instanceof MatType)
                r.set(0, new ArrayType(((MatType) type).componentType));
            else
                r.error("Trying to index a non-array expression of type " + type, node);
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void slicingAccess (SlicingAccessNode node) {
        R.rule()
            .using(node.startIndex, "type")
            .by(r -> {
                Type type = r.get(0);
                if (!(type instanceof IntType))
                    r.error("Slicing an array at start using a non-Int-valued expression", node.startIndex);
            });

        R.rule()
            .using(node.endIndex, "type")
            .by(r -> {
                Type type = r.get(0);
                if (!(type instanceof IntType))
                    r.error("Slicing an array at end using a non-Int-valued expression", node.endIndex);
            });

        R.rule(node, "type")
            .using(node.array, "type")
            .by(r -> {
                Type type = r.get(0);
                if (type instanceof ArrayType)
                    r.set(0, new ArrayType(((ArrayType) type).componentType));
                else if (type instanceof MatType)
                    r.set(0, new MatType(((MatType) type).componentType));
                else
                    r.error("Trying to slice an invalid type : " + type, node);
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void funCall (FunCallNode node)
    {
        this.inferenceContext = node;

        Attribute[] dependencies = new Attribute[node.arguments.size() + 1];
        dependencies[0] = node.function.attr("type");
        forEachIndexed(node.arguments, (i, arg) -> {
            dependencies[i + 1] = arg.attr("type");
            R.set(arg, "index", i);
        });

        R.rule(node, "type")
        .using(dependencies)
        .by(r -> {
            Type maybeFunType = r.get(0);

            if (!(maybeFunType instanceof FunType)) {
                r.error("trying to call a non-function expression: " + node.function, node.function);
                return;
            }

            FunType funType = cast(maybeFunType);

            Type[] params = funType.paramTypes;
            List<ExpressionNode> args = node.arguments;

            if (params.length != args.size())
                r.errorFor(format("wrong number of arguments, expected %d but got %d",
                        params.length, args.size()),
                    node);

            int checkedArgs = Math.min(params.length, args.size());
            boolean isVectorized = false;

            for (int i = 0; i < checkedArgs; ++i) {
                Type argType = r.get(i + 1);
                Type paramType = funType.paramTypes[i];
                if(paramType instanceof GenericType) paramType = argType;
                boolean isValidArrayLike = isArrayLikeAndNeedToCast(argType, paramType);
                if (isValidArrayLike) isVectorized = true;
                if (!isAssignableTo(paramType, argType) && !isValidArrayLike)
                    r.errorFor(format(
                            "incompatible argument provided for argument %d: expected %s but got %s",
                            i, paramType, argType),
                        node.arguments.get(i));
            }

            Type ret = funType.returnType;

            if (isVectorized)
                r.set(0, new MatType(ret));
            else
                r.set(0, ret);
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void unaryExpression (UnaryExpressionNode node)
    {
        assert node.operator == UnaryOperator.NOT; // only one for now
        R.set(node, "type", BoolType.INSTANCE);

        R.rule()
        .using(node.operand, "type")
        .by(r -> {
            Type opType = r.get(0);
            if (!(opType instanceof BoolType))
                r.error("Trying to negate type: " + opType, node);
        });
    }

    // endregion
    // =============================================================================================
    // region [Binary Expressions]
    // =============================================================================================

    private void binaryExpression (BinaryExpressionNode node)
    {
        R.rule(node, "type")
        .using(node.left.attr("type"), node.right.attr("type"))
        .by(r -> {
            Type left  = r.get(0);
            Type right = r.get(1);

            if (left instanceof GenericType || right instanceof GenericType)
                if (left.equals(right))
                    r.set(0, left);
                else if (right.equals(left))
                    r.set(0, right);
                else
                    r.set(0, GenericType.UNKNOWN);
            else if (node.operator == ADD && (left instanceof StringType || right instanceof StringType))
                r.set(0, StringType.INSTANCE);
            else if (isArithmetic(node.operator))
                binaryArithmetic(r, node, left, right);
            else if (isComparison(node.operator))
                binaryComparison(r, node, left, right);
            else if (isArrayLikeComparison(node.operator))
                arrayLikeComparison(r, node, left, right);
            else if (isLogic(node.operator))
                binaryLogic(r, node, left, right);
            else if (isEquality(node.operator))
                binaryEquality(r, node, left, right);
            else if (isArrayLikeEquality(node.operator))
                binaryArrayLikeEquality(r, node, left, right);
        });
    }

    // ---------------------------------------------------------------------------------------------

    private boolean isArithmetic (BinaryOperator op) {
        return op == ADD || op == MULTIPLY || op == SUBTRACT || op == DIVIDE || op == REMAINDER || op == DOT_PRODUCT;
    }

    private boolean isComparison (BinaryOperator op) {
        return op == GREATER || op == GREATER_EQUAL || op == LOWER || op == LOWER_EQUAL;
    }

    private boolean isArrayLikeComparison (BinaryOperator op) {
        return op == M_ONE_EQUAL ||op == M_ONE_NOT_EQUAL || op == M_ALL_LOWER || op == M_ALL_LOWER_EQUAL || op == M_ONE_LOWER ||
            op == M_ONE_LOWER_EQUAL || op == M_ALL_GREATER || op == M_ALL_GREATER_EQUAL || op == M_ONE_GREATER ||
            op == M_ONE_GREATER_EQUAL;
    }

    private boolean isLogic (BinaryOperator op) {
        return op == OR || op == AND;
    }

    private boolean isEquality (BinaryOperator op) {
        return op == EQUALITY || op == NOT_EQUALS;
    }

    private boolean isArrayLikeEquality (BinaryOperator op) {
        return op == M_ALL_EQUAL|| op == M_ALL_NOT_EQUAL;
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryArithmetic (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        if (left instanceof IntType)
            if (right instanceof IntType)
                r.set(0, IntType.INSTANCE);
            else if (right instanceof FloatType)
                r.set(0, FloatType.INSTANCE);
            else if (right instanceof ArrayType)
                if (((ArrayType) right).componentType instanceof IntType)
                    r.set(0, new ArrayType(IntType.INSTANCE));
                else if (((ArrayType) right).componentType instanceof FloatType)
                    r.set(0, new ArrayType((FloatType.INSTANCE)));
                else
                    r.error(arithmeticError(node, "Int", right), node);
            else if (right instanceof MatType)
                if (((MatType) right).componentType instanceof IntType)
                    r.set(0, new MatType(IntType.INSTANCE));
                else if (((MatType) right).componentType instanceof FloatType)
                    r.set(0, new MatType(FloatType.INSTANCE));
                else
                    r.error(arithmeticError(node, "Int", right), node);
            else
                r.error(arithmeticError(node, "Int", right), node);
        else if (left instanceof FloatType)
            if (right instanceof IntType || right instanceof FloatType)
                r.set(0, FloatType.INSTANCE);
            else if (right instanceof ArrayType)
                if (((ArrayType) right).componentType instanceof IntType || ((ArrayType) right).componentType instanceof FloatType)
                    r.set(0, new ArrayType(FloatType.INSTANCE));
                else
                    r.error(arithmeticError(node, "Float", right), node);
            else if (right instanceof MatType)
                if (((MatType) right).componentType instanceof IntType || ((MatType) right).componentType instanceof FloatType)
                    r.set(0, new MatType(FloatType.INSTANCE));
                else
                    r.error(arithmeticError(node, "Float", right), node);
            else
                r.error(arithmeticError(node, "Float", right), node);
        else if (left instanceof MatType)
            if (right instanceof MatType)
                if (((MatType) left).componentType instanceof IntType)
                    if (((MatType) right).componentType instanceof IntType)
                        r.set(0, new MatType(IntType.INSTANCE));
                    else if (((MatType) right).componentType instanceof FloatType)
                        r.set(0, new MatType(FloatType.INSTANCE));
                    else
                        r.error(arithmeticError(node, left, right), node);
                else if (((MatType) left).componentType instanceof FloatType)
                    if (((MatType) right).componentType instanceof IntType ||
                        ((MatType) right).componentType instanceof FloatType)
                        r.set(0, new MatType(FloatType.INSTANCE));
                    else
                        r.error(arithmeticError(node, left, right), node);
                else
                    r.error(arithmeticError(node, left, right), node);
            else if (right instanceof ArrayType)
                    if (((MatType) left).componentType instanceof IntType)
                        if (((ArrayType) right).componentType instanceof IntType)
                            r.set(0, new MatType(IntType.INSTANCE));
                        else if (((ArrayType) right).componentType instanceof FloatType)
                            r.set(0, new MatType(FloatType.INSTANCE));
                        else
                            r.error(arithmeticError(node, left, right), node);
                    else if (((MatType) left).componentType instanceof FloatType)
                        if (((ArrayType) right).componentType instanceof IntType ||
                            ((ArrayType) right).componentType instanceof FloatType)
                            r.set(0, new MatType(FloatType.INSTANCE));
                        else
                            r.error(arithmeticError(node, left, right), node);
                    else
                        r.error(arithmeticError(node, left, right), node);
            else if (right instanceof IntType)
                if (((MatType) left).componentType instanceof IntType)
                    r.set(0, new MatType(IntType.INSTANCE));
                else if (((MatType) left).componentType instanceof FloatType)
                    r.set(0, new MatType(FloatType.INSTANCE));
                else
                    r.error(arithmeticError(node, left, right), node);
            else if (right instanceof FloatType)
                if (((MatType) left).componentType instanceof IntType || ((MatType) left).componentType instanceof FloatType)
                    r.set(0, new MatType(FloatType.INSTANCE));
                else
                    r.error(arithmeticError(node, left, right), node);
            else
                r.error(arithmeticError(node, left, right), node);
        else if (left instanceof ArrayType)
            if (right instanceof ArrayType)
                if (((ArrayType) left).componentType instanceof IntType)
                    if (((ArrayType) right).componentType instanceof IntType)
                        r.set(0, new ArrayType(IntType.INSTANCE));
                    else if (((ArrayType) right).componentType instanceof FloatType)
                        r.set(0, new ArrayType(FloatType.INSTANCE));
                    else
                        r.error(arithmeticError(node, left, right), node);
                else if (((ArrayType) left).componentType instanceof FloatType)
                    if (((ArrayType) right).componentType instanceof IntType ||
                        ((ArrayType) right).componentType instanceof FloatType)
                        r.set(0, new ArrayType(FloatType.INSTANCE));
                    else
                        r.error(arithmeticError(node, left, right), node);
                else
                    r.error(arithmeticError(node, left, right), node);
            else if (right instanceof MatType)
                if (((ArrayType) left).componentType instanceof IntType)
                    if (((MatType) right).componentType instanceof IntType)
                        r.set(0, new MatType(IntType.INSTANCE));
                    else if (((MatType) right).componentType instanceof FloatType)
                        r.set(0, new MatType(FloatType.INSTANCE));
                    else
                        r.error(arithmeticError(node, left, right), node);
                else if (((ArrayType) left).componentType instanceof FloatType)
                    if (((MatType) right).componentType instanceof IntType ||
                        ((MatType) right).componentType instanceof FloatType)
                        r.set(0, new MatType(FloatType.INSTANCE));
                    else
                        r.error(arithmeticError(node, left, right), node);
                else
                    r.error(arithmeticError(node, left, right), node);
            else if (right instanceof IntType)
                if (((ArrayType) left).componentType instanceof IntType)
                    r.set(0, new ArrayType(IntType.INSTANCE));
                else if (((ArrayType) left).componentType instanceof FloatType)
                    r.set(0, new ArrayType(FloatType.INSTANCE));
                else
                    r.error(arithmeticError(node, left, right), node);
            else if (right instanceof FloatType)
                if (((ArrayType) left).componentType instanceof IntType || ((ArrayType) left).componentType instanceof FloatType)
                    r.set(0, new ArrayType(FloatType.INSTANCE));
                else
                    r.error(arithmeticError(node, left, right), node);
            else
                r.error(arithmeticError(node, left, right), node);
        else
            r.error(arithmeticError(node, left, right), node);
    }

    // ---------------------------------------------------------------------------------------------

    private static String arithmeticError (BinaryExpressionNode node, Object left, Object right) {
        return format("Trying to %s %s with %s", node.operator.name().toLowerCase(), left, right);
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryComparison (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!(left instanceof IntType) &&
            !(left instanceof FloatType))
            r.errorFor("Attempting to perform arithmetic comparison on non-numeric type: " + left,
                node.left);
        if (!(right instanceof IntType) &&
            !(right instanceof FloatType))
            r.errorFor("Attempting to perform arithmetic comparison on non-numeric type: " + right,
                node.right);

    }

    // ---------------------------------------------------------------------------------------------

    private void arrayLikeComparison (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!((left instanceof MatType) || (left instanceof ArrayType)) ||
            !(((ArrayLikeType) left).componentType instanceof IntType || ((ArrayLikeType) left).componentType instanceof FloatType))
            r.errorFor("Attempting to perform arithmetic comparison on non-arraylike type: " + left,
                node.left);

        if (!((right instanceof MatType) || (right instanceof ArrayType)) ||
            !(((ArrayLikeType) right).componentType instanceof IntType || ((ArrayLikeType) right).componentType instanceof FloatType))
            r.errorFor("Attempting to perform arithmetic comparison on non-arraylike type: " + right,
                node.right);

    }

    // ---------------------------------------------------------------------------------------------

    private void binaryEquality (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!isComparableTo(left, right))
            r.errorFor(format("Trying to compare incomparable types %s and %s", left, right),
                node);
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryArrayLikeEquality (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!isArrayLikeComparableTo(left, right))
            r.errorFor(format("Trying to compare incomparable types %s and %s", left, right),
                node);
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryLogic (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!(left instanceof BoolType))
            r.errorFor("Attempting to perform binary logic on non-boolean type: " + left,
                node.left);
        if (!(right instanceof BoolType))
            r.errorFor("Attempting to perform binary logic on non-boolean type: " + right,
                node.right);
    }

    // ---------------------------------------------------------------------------------------------

    private void assignment (AssignmentNode node)
    {
        R.rule(node, "type")
        .using(node.left.attr("type"), node.right.attr("type"))
        .by(r -> {
            Type left  = r.get(0);
            Type right = r.get(1);

            r.set(0, r.get(0)); // the type of the assignment is the left-side type

            if (node.left instanceof ReferenceNode
            ||  node.left instanceof FieldAccessNode
            ||  node.left instanceof ArrayAccessNode
            ||  node.left instanceof SlicingAccessNode) {
                if (!isAssignableTo(right, left))
                    r.errorFor("Trying to assign a value to a non-compatible lvalue.", node);
            }
            else
                r.errorFor("Trying to assign to an non-lvalue expression.", node.left);
        });
    }

    // endregion
    // =============================================================================================
    // region [Types & Typing Utilities]
    // =============================================================================================

    private void simpleType (SimpleTypeNode node)
    {
        final Scope scope = this.scope;
        SighNode context = this.inferenceContext;

        R.rule()
        .by(r -> {
            // type declarations may occur after use
            DeclarationContext ctx = scope.lookup(node.name);
            DeclarationNode decl = ctx == null ? null : ctx.declaration;

            if (ctx == null) {
                if (context instanceof FunDeclarationNode){
                    GenericType type = new GenericType(node.name);
                    R.rule(node, "value")
                        .by(s -> s.set(0, type));
                    DeclarationNode typeDecl = new SyntheticDeclarationNode(node.name,  DeclarationKind.TYPE);
                    scope.declare(node.name, typeDecl);

                    R.rule(new Attribute(typeDecl, "declared"), new Attribute(typeDecl, "type"))
                        .by(v -> {
                            v.set(0, type);
                            v.set(1, TypeType.INSTANCE);
                        });
                }
                else {
                    r.errorFor("could not resolve: " + node.name,
                        node,
                        node.attr("value"));
                }
            }

            else if (!isTypeDecl(decl))
                r.errorFor(format(
                    "%s did not resolve to a type declaration but to a %s declaration",
                    node.name, decl.declaredThing()),
                    node,
                    node.attr("value"));

            else
                R.rule(node, "value")
                .using(decl, "declared")
                .by(Rule::copyFirst);
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void arrayType (ArrayTypeNode node)
    {
        R.rule(node, "value")
        .using(node.componentType, "value")
        .by(r -> r.set(0, new ArrayType(r.get(0))));
    }

    // ---------------------------------------------------------------------------------------------

    private void matrixType(MatrixTypeNode node)
    {
        R.rule(node, "value")
            .using(node.componentType, "value")
            .by(r -> {
                Type cptType = r.get(0);
                if (cptType.isArrayLike()){
                    r.error("Cannot declare a matrix of type "+cptType, node);
                    return;
                }
                r.set(0, new MatType(cptType));
            });
    }

    // ---------------------------------------------------------------------------------------------

    private static boolean isTypeDecl (DeclarationNode decl)
    {
        if (decl instanceof StructDeclarationNode) return true;
        if (!(decl instanceof SyntheticDeclarationNode)) return false;
        SyntheticDeclarationNode synthetic = cast(decl);
        return synthetic.kind() == DeclarationKind.TYPE;
    }

    // ---------------------------------------------------------------------------------------------

    private static boolean isArrayLikeAndNeedToCast(Type a, Type b){

        if (a instanceof ArrayType)
            if (!(b instanceof ArrayType || b instanceof MatType)){
                return isAssignableTo(((ArrayType)a).componentType, b);
            }
        if (a instanceof MatType)
            if (!(b instanceof ArrayType || b instanceof MatType)){
                return isAssignableTo(((MatType)a).componentType, b);
            }

        return false;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Indicates whether a value of type {@code a} can be assigned to a location (variable,
     * parameter, ...) of type {@code b}.
     */
    private static boolean isAssignableTo (Type a, Type b)
    {
        if (a == GenericType.UNKNOWN || b == GenericType.UNKNOWN)
            return true;
        if (a instanceof SymbolicType)
            return true;
        if (a instanceof VoidType || b instanceof VoidType)
            return false;

        if (a instanceof IntType && b instanceof FloatType)
            return true;

        if (a instanceof ArrayType)
            return b instanceof ArrayType
                && isAssignableTo(((ArrayType)a).componentType, ((ArrayType)b).componentType);
        if (a instanceof MatType)
            return b instanceof MatType
                && isAssignableTo(((MatType)a).componentType, ((MatType)b).componentType);

        return a instanceof NullType && b.isReference() || a.equals(b);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Indicate whether the two types are comparable.
     */
    private static boolean isComparableTo (Type a, Type b)
    {
        if (a instanceof VoidType || b instanceof VoidType)
            return false;

        return (!a.isArrayLike() && !b.isArrayLike()) &&
            (a.isReference() && b.isReference()
            || a.equals(b)
            || a instanceof IntType && b instanceof FloatType
            || a instanceof FloatType && b instanceof IntType);
        //            || a instanceof MatType && b instanceof ArrayType
        //            || a instanceof ArrayType && b instanceof MatType
        //            || a instanceof MatType && b instanceof IntType
        //            || a instanceof IntType && b instanceof MatType
        //            || a instanceof MatType && b instanceof FloatType
        //            || a instanceof FloatType && b instanceof MatType
        //            || a instanceof ArrayType && b instanceof IntType
        //            || a instanceof IntType && b instanceof ArrayType
        //            || a instanceof ArrayType && b instanceof FloatType
        //            || a instanceof FloatType && b instanceof ArrayType;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Indicate whether the two types are comparable.
     */
    private static boolean isArrayLikeComparableTo (Type a, Type b)
    {
        if (a instanceof VoidType || b instanceof VoidType)
            return false;

        return (a.isArrayLike() && b.isArrayLike() && isComparableTo(((ArrayLikeType)a).componentType, ((ArrayLikeType) b).componentType))
            || (a.isArrayLike() || b.isArrayLike()) &&
            (  a instanceof IntType || b instanceof IntType
            || a instanceof FloatType || b instanceof FloatType
            || a instanceof StringType || b instanceof StringType
            );
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the common supertype between both types, or {@code null} if no such supertype
     * exists.
     */
    private static Type commonSupertype (Type a, Type b)
    {
        if (a instanceof VoidType || b instanceof VoidType)
            return null;
        if (isAssignableTo(a, b))
            return b;
        if (isAssignableTo(b, a))
            return a;
        else
            return null;
    }

    // endregion
    // =============================================================================================
    // region [Scopes & Declarations]
    // =============================================================================================

    private void popScope (SighNode node) {
        scope = scope.parent;
    }

    // ---------------------------------------------------------------------------------------------

    private void root (RootNode node) {
        assert scope == null;
        scope = new RootScope(node, R);
        R.set(node, "scope", scope);
    }

    // ---------------------------------------------------------------------------------------------

    private void block (BlockNode node) {
        scope = new Scope(node, scope);
        R.set(node, "scope", scope);

        Attribute[] deps = getReturnsDependencies(node.statements);
        R.rule(node, "returns")
        .using(deps)
        .by(r -> r.set(0, deps.length != 0 && Arrays.stream(deps).anyMatch(r::get)));
    }

    // ---------------------------------------------------------------------------------------------

    private void varDecl (VarDeclarationNode node)
    {
        this.inferenceContext = node;

        scope.declare(node.name, node);
        R.set(node, "scope", scope);

        R.rule(node, "type")
            .using(node.type, "value")
            .by(Rule::copyFirst);

        R.rule()
            .using(node.type.attr("value"), node.initializer.attr("type"))
            .by(r -> {
                Type expected = r.get(0);
                Type actual = r.get(1);

                if (!isAssignableTo(actual, expected))
                    r.error(format(
                            "incompatible initializer type provided for variable `%s`: expected %s but got %s",
                            node.name, expected, actual),
                        node.initializer);
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void fieldDecl (FieldDeclarationNode node)
    {
        R.rule(node, "type")
        .using(node.type, "value")
        .by(Rule::copyFirst);
    }

    // ---------------------------------------------------------------------------------------------

    private void parameter (ParameterNode node)
    {
        R.set(node, "scope", scope);
        scope.declare(node.name, node); // scope pushed by FunDeclarationNode

        R.rule(node, "type")
        .using(node.type, "value")
        .by(Rule::copyFirst);
    }

    // ---------------------------------------------------------------------------------------------

    private void funDecl (FunDeclarationNode node)
    {
        this.inferenceContext = node;
        scope.declare(node.name, node);
        scope = new Scope(node, scope);
        R.set(node, "scope", scope);

        Attribute[] dependencies = new Attribute[node.parameters.size() + 1];
        dependencies[0] = node.returnType.attr("value");
        forEachIndexed(node.parameters, (i, param) ->
            dependencies[i + 1] = param.attr("type"));

        R.rule()
            .using(dependencies)
            .by(r -> {
                Type retType = r.get(0);
                boolean retTypeDeclared = false;
                if (retType instanceof GenericType){
                    for (int i = 0; i < node.parameters.size(); ++i){
                        if (retType.equals(r.get(i + 1))) {
                            retTypeDeclared = true;
                            break;
                        }
                    }
                    if (!retTypeDeclared)
                        r.error("Generic return Type should be declared in parameters", node);
                }
            });

        R.rule(node, "type")
        .using(dependencies)
        .by (r -> {
            Type[] paramTypes = new Type[node.parameters.size()];
            for (int i = 0; i < paramTypes.length; ++i)
                paramTypes[i] = r.get(i + 1);
            r.set(0, new FunType(r.get(0), paramTypes));
        });

        R.rule()
        .using(node.block.attr("returns"), node.returnType.attr("value"))
        .by(r -> {
            boolean returns = r.get(0);
            Type returnType = r.get(1);
            if (!returns && !(returnType instanceof VoidType))
                r.error("Missing return in function.", node);
            // NOTE: The returned value presence & type is checked in returnStmt().
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void structDecl (StructDeclarationNode node) {
        scope.declare(node.name, node);
        R.set(node, "type", TypeType.INSTANCE);
        R.set(node, "declared", new StructType(node));
    }

    // endregion
    // =============================================================================================
    // region [Other Statements]
    // =============================================================================================

    private void ifStmt (IfNode node) {
        R.rule()
        .using(node.condition, "type")
        .by(r -> {
            Type type = r.get(0);
            if (!(type instanceof BoolType)) {
                r.error("If statement with a non-boolean condition of type: " + type,
                    node.condition);
            }
        });

        Attribute[] deps = getReturnsDependencies(list(node.trueStatement, node.falseStatement));
        R.rule(node, "returns")
        .using(deps)
        .by(r -> r.set(0, deps.length == 2 && Arrays.stream(deps).allMatch(r::get)));
    }

    // ---------------------------------------------------------------------------------------------

    private void whileStmt (WhileNode node) {
        R.rule()
            .using(node.condition, "type")
            .by(r -> {
                Type type = r.get(0);
                if (!(type instanceof BoolType)) {
                    r.error("While statement with a non-boolean condition of type: " + type,
                        node.condition);
                }
            });
    }

    // ---------------------------------------------------------------------------------------------

    private void caseStmt (CaseNode node) {
        this.inferenceContext = node;

        scope = new Scope(node, scope);
        R.set(node, "scope", scope);
        SymbolicVarDeclarationNode decl = new SymbolicVarDeclarationNode(node.span);
        scope.declare("_", decl);
        R.set(decl, "type", SymbolicType.INSTANCE);

        if (node.element instanceof ReferenceNode && ((ReferenceNode) node.element).name.equals("_")){
            R.error(new SemanticError("Cannot use case on variable named '_'", null, node));
            return;
        }

        Attribute[] dependencies = new Attribute[node.body.size()+1];
        dependencies[0] = new Attribute(node.element, "type");
        forEachIndexed(node.body, (i, param) ->
            dependencies[i+1] = param.pattern.attr("type"));

        R.rule(node, "type")
            .using(dependencies)
            .by (r -> {
                Type ref = r.get(0);
                for (int i = 0; i < node.body.size(); ++i){
                    if (!r.get(i+1).equals(ref)) {
                        r.errorFor(format("Cannot compare %s and %s", ref.name(), r.get(i + 1).toString()), node);
                    }
                }
                r.set(0, r.get(0));
            });

    }

    // ---------------------------------------------------------------------------------------------

    private void returnStmt (ReturnNode node)
    {
        R.set(node, "returns", true);

        FunDeclarationNode function = currentFunction();
        if (function == null) // top-level return
            return;

        if (node.expression == null)
            R.rule()
            .using(function.returnType, "value")
            .by(r -> {
               Type returnType = r.get(0);
               if (!(returnType instanceof VoidType))
                   r.error("Return without value in a function with a return type.", node);
            });
        else
            R.rule()
            .using(function.returnType.attr("value"), node.expression.attr("type"))
            .by(r -> {
                Type formal = r.get(0);
                Type actual = r.get(1);
                if (formal instanceof VoidType)
                    r.error("Return with value in a Void function.", node);
                else if (!isAssignableTo(actual, formal)) {
                    r.errorFor(format(
                        "Incompatible return type, expected %s but got %s", formal, actual),
                        node.expression);
                }
            });
    }

    // ---------------------------------------------------------------------------------------------

    private FunDeclarationNode currentFunction()
    {
        Scope scope = this.scope;
        while (scope != null) {
            SighNode node = scope.node;
            if (node instanceof FunDeclarationNode)
                return (FunDeclarationNode) node;
            scope = scope.parent;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private boolean isReturnContainer (SighNode node) {
        return node instanceof BlockNode
            || node instanceof IfNode
            || node instanceof ReturnNode;
    }

    // ---------------------------------------------------------------------------------------------

    /** Get the depedencies necessary to compute the "returns" attribute of the parent. */
    private Attribute[] getReturnsDependencies (List<? extends SighNode> children) {
        return children.stream()
            .filter(Objects::nonNull)
            .filter(this::isReturnContainer)
            .map(it -> it.attr("returns"))
            .toArray(Attribute[]::new);
    }

    // endregion
    // =============================================================================================
}