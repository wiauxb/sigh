package norswap.sigh.interpreter;

import norswap.sigh.ast.*;
import norswap.sigh.scopes.DeclarationKind;
import norswap.sigh.scopes.RootScope;
import norswap.sigh.scopes.Scope;
import norswap.sigh.scopes.SyntheticDeclarationNode;
import norswap.sigh.types.*;
import norswap.uranium.Reactor;
import norswap.uranium.SemanticError;
import norswap.utils.Util;
import norswap.utils.exceptions.Exceptions;
import norswap.utils.exceptions.NoStackException;
import norswap.utils.visitors.ValuedVisitor;
import javax.management.openmbean.SimpleType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static norswap.utils.Util.cast;
import static norswap.utils.Vanilla.*;

/**
 * Implements a simple but inefficient interpreter for Sigh.
 *
 * <h2>Limitations</h2>
 * <ul>
 *     <li>The compiled code currently doesn't support closures (using variables in functions that
 *     are declared in some surroudning scopes outside the function). The top scope is supported.
 *     </li>
 * </ul>
 *
 * <p>Runtime value representation:
 * <ul>
 *     <li>{@code Int}, {@code Float}, {@code Bool}: {@link Long}, {@link Double}, {@link Boolean}</li>
 *     <li>{@code String}: {@link String}</li>
 *     <li>{@code null}: {@link Null#INSTANCE}</li>
 *     <li>Arrays: {@code Object[]}</li>
 *     <li>Structs: {@code HashMap<String, Object>}</li>
 *     <li>Functions: the corresponding {@link DeclarationNode} ({@link FunDeclarationNode} or
 *     {@link SyntheticDeclarationNode}), excepted structure constructors, which are
 *     represented by {@link Constructor}</li>
 *     <li>Types: the corresponding {@link StructDeclarationNode}</li>
 * </ul>
 */
public final class Interpreter
{
    // ---------------------------------------------------------------------------------------------

    private final ValuedVisitor<SighNode, Object> visitor = new ValuedVisitor<>();
    private final Reactor reactor;
    private ScopeStorage storage = null;
    private RootScope rootScope;
    private ScopeStorage rootStorage;

    // ---------------------------------------------------------------------------------------------

    public Interpreter (Reactor reactor) {
        this.reactor = reactor;

        // expressions
        visitor.register(IntLiteralNode.class,           this::intLiteral);
        visitor.register(FloatLiteralNode.class,         this::floatLiteral);
        visitor.register(StringLiteralNode.class,        this::stringLiteral);
        visitor.register(ReferenceNode.class,            this::reference);
        visitor.register(ConstructorNode.class,          this::constructor);
        visitor.register(ArrayLiteralNode.class,         this::arrayLiteral);
        visitor.register(ParenthesizedNode.class,        this::parenthesized);
        visitor.register(FieldAccessNode.class,          this::fieldAccess);
        visitor.register(ArrayAccessNode.class,          this::arrayAccess);
        visitor.register(SlicingAccessNode.class,        this::slicingAccess);
        visitor.register(FunCallNode.class,              this::funCall);
        visitor.register(UnaryExpressionNode.class,      this::unaryExpression);
        visitor.register(BinaryExpressionNode.class,     this::binaryExpression);
        visitor.register(AssignmentNode.class,           this::assignment);
        visitor.register(MatrixLiteralNode.class,        this::matrixLiteral);
        visitor.register(MatrixGeneratorNode.class,      this::matrixGenerator);

        // statement groups & declarations
        visitor.register(RootNode.class,                 this::root);
        visitor.register(BlockNode.class,                this::block);
        visitor.register(VarDeclarationNode.class,       this::varDecl);
        // no need to visitor other declarations! (use fallback)

        // statements
        visitor.register(ExpressionStatementNode.class,  this::expressionStmt);
        visitor.register(IfNode.class,                   this::ifStmt);
        visitor.register(WhileNode.class,                this::whileStmt);
        visitor.register(CaseNode.class,                 this::caseStmt);
        visitor.register(ReturnNode.class,               this::returnStmt);

        visitor.registerFallback(node -> null);
    }

    // ---------------------------------------------------------------------------------------------

    public Object interpret (SighNode root) {
        try {
            return run(root);
        } catch (PassthroughException e) {
            throw Exceptions.runtime(e.getCause());
        }
    }

    // ---------------------------------------------------------------------------------------------

    private Object run (SighNode node) {
        try {
            return visitor.apply(node);
        } catch (InterpreterException | Return | PassthroughException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InterpreterException("exception while executing " + node, e);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Used to implement the control flow of the return statement.
     */
    private static class Return extends NoStackException {
        final Object value;
        private Return (Object value) {
            this.value = value;
        }
    }

    // ---------------------------------------------------------------------------------------------

    private <T> T get(SighNode node) {
        return cast(run(node));
    }

    // ---------------------------------------------------------------------------------------------

    private Long intLiteral (IntLiteralNode node) {
        return node.value;
    }

    private Double floatLiteral (FloatLiteralNode node) {
        return node.value;
    }

    private String stringLiteral (StringLiteralNode node) {
        return node.value;
    }

    // ---------------------------------------------------------------------------------------------

    private Object parenthesized (ParenthesizedNode node) {
        return get(node.expression);
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] arrayLiteral (ArrayLiteralNode node) {
        return map(node.components, new Object[0], visitor);
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] matrixLiteral (MatrixLiteralNode node) {
        return map(node.components, new Object[0][0], visitor);
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] matrixGenerator (MatrixGeneratorNode node) {
        int shape1 = ((Long) get(node.shape.get(0))).intValue();
        int shape2 = ((Long) get(node.shape.get(1))).intValue();

        if (shape1 <= 0 || shape2 <= 0){
            throw new InterpreterException(format("Invalid shape argument when initializing a matrix : [%d, %d]",
                shape1, shape2), null);
        }

        Object[][] result = new Object[shape1][shape2];

        for (int i = 0; i < shape1; i++) {
            for (int j = 0; j < shape2; j++) {
                result[i][j] = get(node.filler);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------------------------------------

    private Object binaryExpression (BinaryExpressionNode node)
    {
        // Cases where both operands should not be evaluated.
        switch (node.operator) {
            case OR:  return booleanOp(node, false);
            case AND: return booleanOp(node, true);
        }

        Object left  = get(node.left);
        Object right = get(node.right);

        Type leftType  = reactor.get(node.left, "type");
        Type rightType = reactor.get(node.right, "type");

        if (leftType instanceof GenericType) leftType = ((GenericType) leftType).resolution;
        if (rightType instanceof GenericType) rightType = ((GenericType) rightType).resolution;

        if (node.operator == BinaryOperator.ADD
                && (leftType instanceof StringType || rightType instanceof StringType)){
            return convertToString(left) + convertToString(right);}

        boolean floating = leftType instanceof FloatType || rightType instanceof FloatType;
        boolean numeric  = floating || leftType instanceof IntType || rightType instanceof IntType ;
        boolean arraylike = leftType instanceof MatType || leftType instanceof ArrayType
            || rightType instanceof MatType || rightType instanceof ArrayType;

        Type[] insideType = new Type[]{leftType, rightType};
        if (leftType instanceof MatType)
            insideType[0] = ((MatType) leftType).componentType;
        else if (leftType instanceof ArrayType)
            insideType[0] = ((ArrayType) leftType).componentType;
        if (rightType instanceof MatType)
            insideType[1] = ((MatType) rightType).componentType;
        else if (rightType instanceof ArrayType)
            insideType[1] = ((ArrayType) rightType).componentType;

        if (numeric && !arraylike)
            return numericOp(node, floating, (Number) left, (Number) right);
        if (arraylike && !numeric)
            return arrayLikeOp(node, insideType, left, right);
        if (numeric && arraylike)
            return mixedOp(node, insideType, left, right);

        switch (node.operator) {
            case EQUALITY:
                return  leftType.isPrimitive() ? left.equals(right) : left == right;
            case NOT_EQUALS:
                return  leftType.isPrimitive() ? !left.equals(right) : left != right;
        }
        throw new Error("should not reach here");
    }

    // ---------------------------------------------------------------------------------------------

    private boolean booleanOp (BinaryExpressionNode node, boolean isAnd)
    {
        boolean left = get(node.left);
        return isAnd
                ? left && (boolean) get(node.right)
                : left || (boolean) get(node.right);
    }

    // ---------------------------------------------------------------------------------------------

    private Object numericOp
            (BinaryExpressionNode node, boolean floating, Number left, Number right)
    {
        long ileft, iright;
        double fleft, fright;

        if (floating) {
            fleft  = left.doubleValue();
            fright = right.doubleValue();
            ileft = iright = 0;
        } else {
            ileft  = left.longValue();
            iright = right.longValue();
            fleft = fright = 0;
        }

        Object result;
        if (floating)
            switch (node.operator) {
                case MULTIPLY:      return fleft *  fright;
                case DIVIDE:        return fleft /  fright;
                case REMAINDER:     return fleft %  fright;
                case ADD:           return fleft +  fright;
                case SUBTRACT:      return fleft -  fright;
                case GREATER:       return fleft >  fright;
                case LOWER:         return fleft <  fright;
                case GREATER_EQUAL: return fleft >= fright;
                case LOWER_EQUAL:   return fleft <= fright;
                case EQUALITY:      return fleft == fright;
                case NOT_EQUALS:    return fleft != fright;
                default:
                    throw new Error("should not reach here");
            }
        else
            switch (node.operator) {
                case MULTIPLY:      return ileft *  iright;
                case DIVIDE:        return ileft /  iright;
                case REMAINDER:     return ileft %  iright;
                case ADD:           return ileft +  iright;
                case SUBTRACT:      return ileft -  iright;
                case GREATER:       return ileft >  iright;
                case LOWER:         return ileft <  iright;
                case GREATER_EQUAL: return ileft >= iright;
                case LOWER_EQUAL:   return ileft <= iright;
                case EQUALITY:      return ileft == iright;
                case NOT_EQUALS:    return ileft != iright;
                default:
                    throw new Error("should not reach here");
            }
    }

    // ---------------------------------------------------------------------------------------------

    private Object arrayLikeOp (BinaryExpressionNode node, Type[] insideTypes, Object left, Object right)
    {

        if (!(left instanceof Object[]) || !(right instanceof Object[]))
            throw new Error("should not reach here");

        switch (node.operator) {
            case DOT_PRODUCT:
            case MULTIPLY:
            case DIVIDE:
            case REMAINDER:
            case ADD:
            case SUBTRACT:
                return applyOperationForAll(node.operator, insideTypes, (Object[]) left, (Object[]) right);

            case GREATER:
            case LOWER:
            case GREATER_EQUAL:
            case LOWER_EQUAL:
            case EQUALITY:
            case NOT_EQUALS:
                throw new InterpreterException(format("%s is not a valid operator for array like variables.", node.operator.string), null);

            case M_ALL_EQUAL:
            case M_ALL_NOT_EQUAL:
            case M_ALL_LOWER:
            case M_ALL_LOWER_EQUAL:
            case M_ALL_GREATER:
            case M_ALL_GREATER_EQUAL:
                return applyComparaisonForAll(node.operator, insideTypes[0], (Object[]) left, (Object[]) right);
            case M_ONE_EQUAL:
            case M_ONE_NOT_EQUAL:
            case M_ONE_LOWER:
            case M_ONE_LOWER_EQUAL:
            case M_ONE_GREATER:
            case M_ONE_GREATER_EQUAL:
                return applyComparaisonForOne(node.operator, insideTypes[0], (Object[]) left, (Object[]) right);
            default:
                throw new Error("should not reach here");
        }
    }

    // ---------------------------------------------------------------------------------------------

    private Object mixedOp (BinaryExpressionNode node, Type[] insideType, Object left, Object right)
    {

        if (left instanceof Long || left instanceof Double) {
            if (!(right instanceof Object[]))
                throw new Error("should not reach here");
            //left = Num et rght = Mat
            int[] shape = getArrayLikeShape((Object[]) right);
            Object[][] new_mat = (left instanceof Long) ? new Long[shape[0]][shape[1]] : new Double[shape[0]][shape[1]];
            for (int i = 0; i < shape[0]; i++) {
                for (int j = 0; j < shape[1]; j++) {
                    new_mat[i][j] = left;
                }
            }
           return arrayLikeOp(node, insideType, new_mat, right);
        }
        else {
            if (!(left instanceof Object[]))
                throw new Error("should not reach here");
            // left = mat et right = Num
            int[] shape = getArrayLikeShape((Object[]) left);
            Object[][] new_mat = (right instanceof Long) ? new Long[shape[0]][shape[1]] : new Double[shape[0]][shape[1]];
            for (int i = 0; i < shape[0]; i++) {
                for (int j = 0; j < shape[1]; j++) {
                    new_mat[i][j] = right;
                }
            }
            return arrayLikeOp(node, insideType, left, new_mat);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private int[] getArrayLikeShape(Object[] array)
    {
        int[] res = new int[2];
        if (array instanceof Object[][]) {
            res[0] = array.length;
            res[1] = ((Object[][]) array)[0].length;
        }
        else{
            res[0] = 1;
            res[1] = array.length;
        }
        return res;
    }

    // ---------------------------------------------------------------------------------------------

    private Object[][] arrayToMat(Object[] array){
        if (array instanceof Object[][]) return (Object[][]) array;
        return new Object[][]{array};
    }

    // ---------------------------------------------------------------------------------------------

    private Number getWithType(Object num, Type type){
        if (type.equals(IntType.INSTANCE))
            return (Long) num;
        else if (type.equals(FloatType.INSTANCE))
            return (Double) num;
        else if (type.equals(StringType.INSTANCE))
            throw new Error("String comparaison is not yet implemented");
        else
            throw new Error("should not reach here");
    }

    // ---------------------------------------------------------------------------------------------

    private Object applyOperationForAll(BinaryOperator operator, Type[] insideTypes, Object[] left, Object[] right){

        int[] shape1 = getArrayLikeShape(left);
        int[] shape2 = getArrayLikeShape(right);

        if (!Arrays.equals(shape1, shape2) && operator != BinaryOperator.DOT_PRODUCT)
            throw new Error(format("Operand must be same sizes: %s != %s", Arrays.toString(shape1), Arrays.toString(shape2)));
        else if (operator == BinaryOperator.DOT_PRODUCT && shape1[1] != shape2[0])
            throw new Error(format("Invalid shape for dot product: %s and %s", Arrays.toString(shape1), Arrays.toString(shape2)));

        Object[][] tleft = (left instanceof Object[][]) ? (Object[][]) left : arrayToMat(left);
        Object[][] tright = (right instanceof Object[][]) ? (Object[][]) right : arrayToMat(right);


        Object[][] rep = new Object[shape1[0]][shape2[1]];

        for (int i = 0; i < shape1[0]; i++) {
            for (int j = 0; j < shape2[1]; j++) {

                long ileft, iright;
                double fleft, fright;

                if (tleft[i][j] instanceof Double) {
                    fleft  = (Double) tleft[i][j];
                    ileft = 0;
                } else {
                    ileft  = (Long) tleft[i][j];
                    fleft =  ((Long) tleft[i][j]).doubleValue();
                }

                if (tright[i][j] instanceof Double) {
                    fright  = (Double) tright[i][j];
                    iright = 0;
                } else {
                    iright  = (Long) tright[i][j];
                    fright = ((Long) tright[i][j]).doubleValue();
                }

                switch (operator) {
                    case MULTIPLY:
                        if (insideTypes[0] instanceof IntType)
                            if (insideTypes[1] instanceof IntType)
                                rep[i][j] = ileft * iright;
                            else
                                rep[i][j] = ileft * fright;
                        else
                            if (insideTypes[1] instanceof IntType)
                                rep[i][j] = fleft * iright;
                            else
                                rep[i][j] = fleft * fright;
                        break;
                    case DIVIDE:
                        if (insideTypes[0] instanceof IntType)
                            if (insideTypes[1] instanceof IntType)
                                rep[i][j] = ileft / iright;
                            else
                                rep[i][j] = ileft / fright;
                        else
                            if (insideTypes[1] instanceof IntType)
                                rep[i][j] = fleft / iright;
                            else
                                rep[i][j] = fleft / fright;
                        break;
                    case REMAINDER:
                        if (insideTypes[0] instanceof IntType)
                            if (insideTypes[1] instanceof IntType)
                                rep[i][j] = ileft % iright;
                            else
                                rep[i][j] = ileft % fright;
                        else
                            if (insideTypes[1] instanceof IntType)
                                rep[i][j] = fleft % iright;
                            else
                                rep[i][j] = fleft % fright;
                        break;
                    case ADD:
                        if (insideTypes[0] instanceof IntType)
                            if (insideTypes[1] instanceof IntType)
                                rep[i][j] = ileft + iright;
                            else
                                rep[i][j] = ileft + fright;
                        else
                            if (insideTypes[1] instanceof IntType)
                                rep[i][j] = fleft + iright;
                            else
                                rep[i][j] = fleft + fright;
                        break;
                    case SUBTRACT:
                        if (insideTypes[0] instanceof IntType)
                            if (insideTypes[1] instanceof IntType)
                                rep[i][j] = ileft - iright;
                            else
                                rep[i][j] = ileft - fright;
                        else
                            if (insideTypes[1] instanceof IntType)
                                rep[i][j] = fleft - iright;
                            else
                                rep[i][j] = fleft - fright;
                        break;
                    case DOT_PRODUCT:
                        double res = 0.;
                        for (int k = 0; k < shape1[1]; k++) {
                            if (insideTypes[0] instanceof IntType)
                                if (insideTypes[1] instanceof IntType)
                                    res += ((Long) tleft[i][k]) * ((Long) tright[k][j]);
                                else
                                    res += ((Long) tleft[i][k]) * ((Double) tright[k][j]);
                            else
                                if (insideTypes[1] instanceof IntType)
                                    res += ((Double) tleft[i][k]) * ((Long) tright[k][j]);
                                else
                                    res += ((Double) tleft[i][k]) * ((Double) tright[k][j]);
                        }
                        if (insideTypes[0] instanceof IntType &&
                            insideTypes[1] instanceof IntType) rep[i][j] = (long) res;
                        else rep[i][j] = res;
                        break;
                    default:
                        throw new Error("should not reach here");
                }
            }
        }
        return rep;
    }

    // ---------------------------------------------------------------------------------------------

    private Object applyComparaisonForOne(BinaryOperator operator, Type insideType, Object[] left, Object[] right)
    {
        int[] shape1 = getArrayLikeShape(left);
        int[] shape2 = getArrayLikeShape(right);

        if (!Arrays.equals(shape1, shape2))
            throw new Error(format("Operand must be same sizes: %s != %s", Arrays.toString(shape1), Arrays.toString(shape2)));

        Object[][] tleft = (left instanceof Object[][]) ? (Object[][]) left : arrayToMat(left);
        Object[][] tright = (right instanceof Object[][]) ? (Object[][]) right : arrayToMat(right);

        for (int i = 0; i < shape1[0]; i++) {
            for (int j = 0; j < shape1[1]; j++) {
                Number nleft = getWithType(tleft[i][j], insideType);
                Number nright = getWithType(tright[i][j], insideType);
                switch(operator) {
                    case M_ONE_EQUAL:
                        if (nleft.equals(nright)) return true;
                        break;
                    case M_ONE_NOT_EQUAL:
                        if (!(nleft.equals(nright))) return true;
                        break;
                    case M_ONE_LOWER:
                        if (insideType.equals(IntType.INSTANCE) && nleft.longValue() < nright.longValue()) return true;
                        else if (insideType.equals(FloatType.INSTANCE) && nleft.floatValue() < nright.floatValue()) return true;
                        break;
                    case M_ONE_LOWER_EQUAL:
                        if (insideType.equals(IntType.INSTANCE) && nleft.longValue() <= nright.longValue()) return true;
                        else if (insideType.equals(FloatType.INSTANCE) && nleft.floatValue() <= nright.floatValue()) return true;
                        break;
                    case M_ONE_GREATER:
                        if (insideType.equals(IntType.INSTANCE) && nleft.longValue() > nright.longValue()) return true;
                        else if (insideType.equals(FloatType.INSTANCE) && nleft.floatValue() > nright.floatValue()) return true;
                        break;
                    case M_ONE_GREATER_EQUAL:
                        if (insideType.equals(IntType.INSTANCE) && nleft.longValue() >= nright.longValue()) return true;
                        else if (insideType.equals(FloatType.INSTANCE) && nleft.floatValue() >= nright.floatValue()) return true;
                        break;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------

    private Object applyComparaisonForAll(BinaryOperator operator, Type insideType, Object[] left, Object[] right)
    {
        int[] shape1 = getArrayLikeShape(left);
        int[] shape2 = getArrayLikeShape(right);

        if (!Arrays.equals(shape1, shape2))
            throw new Error(format("Operand must be same sizes: %s != %s", Arrays.toString(shape1), Arrays.toString(shape2)));

        Object[][] tleft = (left instanceof Object[][]) ? (Object[][]) left : arrayToMat(left);
        Object[][] tright = (right instanceof Object[][]) ? (Object[][]) right : arrayToMat(right);

        for (int i = 0; i < shape1[0]; i++) {
            for (int j = 0; j < shape1[1]; j++) {
                Number nleft = getWithType(tleft[i][j], insideType);
                Number nright = getWithType(tright[i][j], insideType);
                switch(operator) {
                    case M_ALL_EQUAL:
                        if (!(nleft.equals(nright))) return false;
                        break;
                    case M_ALL_NOT_EQUAL:
                        if (nleft.equals(nright)) return false;
                        break;
                    case M_ALL_LOWER:
                        if (insideType.equals(IntType.INSTANCE) && nleft.longValue() >= nright.longValue()) return false;
                        else if (insideType.equals(FloatType.INSTANCE) && nleft.floatValue() >= nright.floatValue()) return false;
                        break;
                    case M_ALL_LOWER_EQUAL:
                        if (insideType.equals(IntType.INSTANCE) && nleft.longValue() > nright.longValue()) return false;
                        else if (insideType.equals(FloatType.INSTANCE) && nleft.floatValue() > nright.floatValue()) return false;
                        break;
                    case M_ALL_GREATER:
                        if (insideType.equals(IntType.INSTANCE) && nleft.longValue() <= nright.longValue()) return false;
                        else if (insideType.equals(FloatType.INSTANCE) && nleft.floatValue() <= nright.floatValue()) return false;
                        break;
                    case M_ALL_GREATER_EQUAL:
                        if (insideType.equals(IntType.INSTANCE) && nleft.longValue() < nright.longValue()) return false;
                        else if (insideType.equals(FloatType.INSTANCE) && nleft.floatValue() < nright.floatValue()) return false;
                        break;
                }
            }
        }
        return true;
    }
    // ---------------------------------------------------------------------------------------------

    public Object assignment (AssignmentNode node)
    {
        if (node.left instanceof ReferenceNode) {
            Scope scope = reactor.get(node.left, "scope");
            String name = ((ReferenceNode) node.left).name;
            Object rvalue = get(node.right);
            assign(scope, name, rvalue, reactor.get(node, "type"));
            return rvalue;
        }

        if (node.left instanceof ArrayAccessNode) {
            ArrayAccessNode arrayAccess = (ArrayAccessNode) node.left;
            Object[] array = getNonNullArray(arrayAccess.array);
            int index = getIndex(arrayAccess.index);
            try {
                return array[index] = get(node.right);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PassthroughException(e);
            }
        }

        if (node.left instanceof SlicingAccessNode) {
            SlicingAccessNode slicingAccess = (SlicingAccessNode) node.left;

            int startIndex = getIndex(slicingAccess.startIndex);
            int endIndex = getEndIndex(slicingAccess.endIndex);

            if (endIndex != -1 && startIndex > endIndex)
                throw new InterpreterException(format("index %d should be smaller than %d", startIndex, endIndex), null);

            Object[] array = getNonNullArray(slicingAccess.array);
            if (endIndex == -1) endIndex = array.length;
            try {
                Object[] right = getNonNullArray(node.right);
                System.arraycopy(right, 0, array, startIndex, endIndex - startIndex);
                return array;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PassthroughException(e);
            }
        }

        if (node.left instanceof FieldAccessNode) {
            FieldAccessNode fieldAccess = (FieldAccessNode) node.left;
            Object object = get(fieldAccess.stem);
            if (object == Null.INSTANCE)
                throw new PassthroughException(
                    new NullPointerException("accessing field of null object"));
            Map<String, Object> struct = cast(object);
            Object right = get(node.right);
            struct.put(fieldAccess.fieldName, right);
            return right;
        }

        throw new Error("should not reach here");
    }

    // ---------------------------------------------------------------------------------------------

    private int getEndIndex (ExpressionNode node) {
        long index = get(node);
        if (index == -1)
            return -1;
        if (index < 0)
            throw new ArrayIndexOutOfBoundsException("Negative index: " + index);
        if (index >= Integer.MAX_VALUE - 1)
            throw new ArrayIndexOutOfBoundsException("Index exceeds max array index (2ˆ31 - 2): " + index);
        return (int) index;
    }

    private int getIndex (ExpressionNode node)
    {
        long index = get(node);
        if (index < 0)
            throw new ArrayIndexOutOfBoundsException("Negative index: " + index);
        if (index >= Integer.MAX_VALUE - 1)
            throw new ArrayIndexOutOfBoundsException("Index exceeds max array index (2ˆ31 - 2): " + index);
        return (int) index;
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] getNonNullArray (ExpressionNode node)
    {
        Object object = get(node);
        if (object == Null.INSTANCE)
            throw new PassthroughException(new NullPointerException("indexing null array"));
        return (Object[]) object;
    }

    // ---------------------------------------------------------------------------------------------

    private Object[][] getNonNullMatrix (ExpressionNode node) {
        Object object = get(node);
        if (object == Null.INSTANCE)
            throw new PassthroughException(new NullPointerException("indexing null matrix"));
        return (Object[][]) object;
    }

    // ---------------------------------------------------------------------------------------------

    private Object unaryExpression (UnaryExpressionNode node)
    {
        // there is only NOT
        assert node.operator == UnaryOperator.NOT;
        return ! (boolean) get(node.operand);
    }

    // ---------------------------------------------------------------------------------------------

    private Object arrayAccess (ArrayAccessNode node)
    {
        Object[] array = getNonNullArray(node.array);
        try {
            return array[getIndex(node.index)];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new PassthroughException(e);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private Object slicingAccess (SlicingAccessNode node)
    {

        int startIndex = getIndex(node.startIndex);
        int endIndex = getEndIndex(node.endIndex);

        if (endIndex != -1 && startIndex > endIndex)
            throw new InterpreterException(format("index %d should be smaller than %d", startIndex, endIndex), null);

        Object arr = get(node.array);
        if (arr == Null.INSTANCE)
            throw new PassthroughException(new NullPointerException("indexing null array"));

        if (arr instanceof Object[][]) {
            Object[][] matrix = getNonNullMatrix(node.array);
            if (endIndex > matrix.length)
                throw new InterpreterException(format("index %d should be smaller than number of line in the matrix : %d", endIndex, matrix.length), null);
            if (endIndex == -1) endIndex = matrix.length;

            try {
                Object[][] res = new Object[endIndex - startIndex][matrix[0].length];
                for (int i = startIndex; i < endIndex; i++) {
                    res[i - startIndex] = matrix[i].clone();
                }
                return res;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PassthroughException(e);
            }
        }
        else if (arr instanceof Object[]){
            Object[] array = getNonNullArray(node.array);
            if (endIndex > array.length)
                throw new InterpreterException(format("index %d should be smaller than the array length : %d", endIndex, array.length), null);
            if (endIndex == -1) endIndex = array.length;

            try {
                Object[] res = new Object[endIndex - startIndex];
                System.arraycopy(array, startIndex, res, 0, endIndex - startIndex);
                return res;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PassthroughException(e);
            }
        }
        else
            throw new InterpreterException("Tried to slice an invalid type (" + node.array.getClass()+")", null);
    }

    // ---------------------------------------------------------------------------------------------

    private Object root (RootNode node)
    {
        assert storage == null;
        rootScope = reactor.get(node, "scope");
        storage = rootStorage = new ScopeStorage(rootScope, null);
        storage.initRoot(rootScope);

        try {
            node.statements.forEach(this::run);
        } catch (Return r) {
            return r.value;
            // allow returning from the main script
        } finally {
            storage = null;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void block (BlockNode node) {
        Scope scope = reactor.get(node, "scope");
        storage = new ScopeStorage(scope, storage);
        node.statements.forEach(this::run);
        storage = storage.parent;
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Constructor constructor (ConstructorNode node) {
        // guaranteed safe by semantic analysis
        return new Constructor(get(node.ref));
    }

    // ---------------------------------------------------------------------------------------------

    private Object expressionStmt (ExpressionStatementNode node) {
        get(node.expression);
        return null;  // discard value
    }

    // ---------------------------------------------------------------------------------------------

    private Object fieldAccess (FieldAccessNode node)
    {
        Object stem = get(node.stem);
        if (stem == Null.INSTANCE)
            throw new PassthroughException(
                new NullPointerException("accessing field of null object"));
        if (stem instanceof Map)
            return Util.<Map<String, Object>>cast(stem).get(node.fieldName);
        else if (stem instanceof Object[][]) {
            Object[][] matrix = (Object[][]) stem;
            Object[] res = new Long[2];
            res[0] = (long) matrix.length;
            res[1] = (long) matrix[0].length;
            return res;
        }
        else {
            return (long) ((Object[]) stem).length;
        }


//        return stem instanceof Map
//                ? Util.<Map<String, Object>>cast(stem).get(node.fieldName)
//                : (long) ((Object[]) stem).length; // only field on arrays
    }

    // ---------------------------------------------------------------------------------------------

    private Object funCall (FunCallNode node)
    {
        Object decl = get(node.function);
        node.arguments.forEach(this::run);
        Object[] args = map(node.arguments, new Object[0], visitor);
        Type[] argType = map(node.arguments, new Type[0], arg -> reactor.get(arg, "type"));

        if (decl == Null.INSTANCE)
            throw new PassthroughException(new NullPointerException("calling a null function"));

        if (decl instanceof SyntheticDeclarationNode)
            return builtin(((SyntheticDeclarationNode) decl).name(), args);

        if (decl instanceof Constructor)
            return buildStruct(((Constructor) decl).declaration, args);

        FunDeclarationNode funDecl = (FunDeclarationNode) decl;

        Type[] paramType = map(funDecl.parameters, new Type[0], param -> reactor.get(param.type, "value"));

        for (Type type : paramType) {
            if (type instanceof GenericType){
                ((GenericType) type).reset();
            }
        }

        forEachIndexed(paramType, (i, type) -> {
            if (type instanceof GenericType){
                if (!((GenericType) type).solve(argType[i]) &&
                    ((GenericType) type).resolution != argType[i]){
                    throw new InterpreterException(format("GenericType should be %s : got %s", type.name(), argType[i].name()), null);
                }
            }
        });

        boolean vectorized = false;
        int[] shape = new int[2];

        for (int i = 0; i < args.length; i++) {
            if (isVectorized(args[i], paramType[i])){
                vectorized = true;
                shape = getArrayLikeShape((Object[]) args[i]);
            }
        }

        return (vectorized) ? vectorizedFunExec(args, shape, funDecl) : FunExec(args, funDecl);
    }

    // ---------------------------------------------------------------------------------------------

    private boolean isVectorized (Object parameter, Type type){
        if (type instanceof GenericType) return false;
        else if (parameter instanceof Object[][] && !type.isArrayLike()) return true;
        else return parameter instanceof Object[] && !type.isArrayLike();
    }

    // ---------------------------------------------------------------------------------------------

    private Object FunExec (Object[] args, FunDeclarationNode funDecl){
        ScopeStorage oldStorage = storage;
        Scope scope = reactor.get(funDecl, "scope");
        storage = new ScopeStorage(scope, storage);

        coIterate(args, funDecl.parameters,
                (arg, param) -> storage.set(scope, param.name, arg));

        try {
            get(funDecl.block);
        } catch (Return r) {
            return r.value;
        } finally {
            storage = oldStorage;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Object vectorizeArg(Object arg, int[] shape){

            if (arg instanceof Object[]){
                if(!Arrays.equals(getArrayLikeShape((Object[]) arg), shape))
                    throw new RuntimeException(
                        format("Arguments of vectorized function should be of same shape: %s != %s",
                            Arrays.toString(getArrayLikeShape((Object[]) arg)),
                            Arrays.toString(shape)));
                return arrayToMat((Object[]) arg);
            }
            else {
                Object[][] new_arg = new Object[shape[0]][shape[1]];
                for (int i = 0; i < shape[0]; i++) {
                    for (int j = 0; j < shape[1]; j++) {
                        new_arg[i][j] = arg;
                    }
                }
                return new_arg;
            }
    }

    private Object vectorizedFunExec (Object[] args, int[] shape, FunDeclarationNode funDecl){

        args = map(args, new Object[0], (arg) -> vectorizeArg(arg, shape));

        Object[][] result = new Object[shape[0]][shape[1]];

        Scope scope = reactor.get(funDecl, "scope");

        for (int i = 0; i < shape[0]; i++) {
            for (int j = 0; j < shape[1]; j++) {

                ScopeStorage oldStorage = storage;
                storage = new ScopeStorage(scope, storage);

                int finalI = i;
                int finalJ = j;
                coIterate(map(args, new Object[0], (arg) -> ((Object[][]) arg)[finalI][finalJ]), funDecl.parameters,
                    (arg, param) -> storage.set(scope, param.name, arg));

                try {
                    get(funDecl.block);
                } catch (Return r) {
                    result[i][j] = r.value;
                } finally {
                    storage = oldStorage;
                }
            }
        }
        return result;
    }

    // ---------------------------------------------------------------------------------------------

    private Object builtin (String name, Object[] args)
    {
        assert name.equals("print"); // only one at the moment
        String out = convertToString(args[0]);
        System.out.println(out);
        return out;
    }

    // ---------------------------------------------------------------------------------------------

    private String convertToString (Object arg)
    {
        if (arg == Null.INSTANCE)
            return "null";
        else if (arg instanceof Object[])
            return Arrays.deepToString((Object[]) arg);
        else if (arg instanceof FunDeclarationNode)
            return ((FunDeclarationNode) arg).name;
        else if (arg instanceof StructDeclarationNode)
            return ((StructDeclarationNode) arg).name;
        else if (arg instanceof Constructor)
            return "$" + ((Constructor) arg).declaration.name;
        else if (arg == SymbolicValue.INSTANCE)
            return Character.toString('\f');
        else
            return arg.toString();
    }

    // ---------------------------------------------------------------------------------------------

    private HashMap<String, Object> buildStruct (StructDeclarationNode node, Object[] args)
    {
        HashMap<String, Object> struct = new HashMap<>();
        for (int i = 0; i < node.fields.size(); ++i)
            struct.put(node.fields.get(i).name, args[i]);
        return struct;
    }

    // ---------------------------------------------------------------------------------------------

    private Void ifStmt (IfNode node)
    {
        if (get(node.condition))
            get(node.trueStatement);
        else if (node.falseStatement != null)
            get(node.falseStatement);
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void whileStmt (WhileNode node)
    {
        while (get(node.condition))
            get(node.body);
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void caseStmt (CaseNode node){
        Scope scope = reactor.get(node, "scope");
        storage = new ScopeStorage(scope, storage);
        storage.set(scope, "_", SymbolicValue.INSTANCE);

        Object elem = get(node.element);

        for (CaseBodyNode bodyNode: node.body) {
            Object pattern = get(bodyNode.pattern);
            if(checkPattern(pattern, elem)) {
                get(bodyNode.statements);
                storage = storage.parent;
                return null;
            }
        }
        get(node.defaultBlock);
        storage = storage.parent;
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private boolean checkPattern(Object pattern, Object element){
        if (element instanceof Object[]){
            if (! (pattern instanceof Object[]))
                throw new Error("should not reach here");
            return matchArray((Object[]) pattern, (Object[]) element);
        }
        else if (element instanceof String){
            if (! (pattern instanceof String))
                throw new Error("should not reach here");
            return matchString((String) pattern, (String) element);
        }
        else
            return pattern.equals(element);
    }

    // ---------------------------------------------------------------------------------------------

    //Adapted from https://www.geeksforgeeks.org/wildcard-character-matching/
    private boolean matchArray(Object[] first, Object[] second){
        // If we reach at the end of both strings,
        // we are done
        if (first.length == 0 && second.length == 0)
            return true;

        // Make sure that the characters after '_'
        // are present in second string.
        // This function assumes that the first
        // string will not contain two consecutive '_'
        if (first.length > 1 && first[0] instanceof SymbolicValue &&
            second.length == 0)
            return false;

        // If current characters of both strings match
        if (first.length != 0 && second.length != 0)
            if (first[0] instanceof Object[] && second[0] instanceof Object[] &&
                matchArray((Object[]) first[0], (Object[]) second[0]))
                    return matchArray(Arrays.copyOfRange(first, 1, first.length),
                                        Arrays.copyOfRange(second, 1, second.length));
            else if (first[0] == second[0])
                return matchArray(Arrays.copyOfRange(first, 1, first.length),
                                    Arrays.copyOfRange(second, 1, second.length));

        // If there is _, then there are two possibilities
        // a) We consider current character of second string
        // b) We ignore current character of second string.
        if (first.length > 0 && first[0] instanceof SymbolicValue)
            return matchArray(Arrays.copyOfRange(first, 1, first.length), second) ||
                matchArray(first, Arrays.copyOfRange(second, 1, second.length));
        return false;
    }

    // ---------------------------------------------------------------------------------------------

    //Adapted from https://www.geeksforgeeks.org/wildcard-character-matching/
    private boolean matchString(String first, String second){
        // If we reach at the end of both strings,
        // we are done
        if (first.length() == 0 && second.length() == 0)
            return true;

        // Make sure that the characters after '_'
        // are present in second string.
        // This function assumes that the first
        // string will not contain two consecutive '_'
        if (first.length() > 1 && first.charAt(0) == '\f' &&
            second.length() == 0)
            return false;

        // If current characters of both strings match
        if (first.length() != 0 && second.length() != 0 && first.charAt(0) == second.charAt(0))
            return matchString(first.substring(1), second.substring(1));

        // If there is _, then there are two possibilities
        // a) We consider current character of second string
        // b) We ignore current character of second string.
        if (first.length() > 0 && first.charAt(0) == '\f')
            return matchString(first.substring(1), second) ||
                matchString(first, second.substring(1));
        return false;
    }

    // ---------------------------------------------------------------------------------------------

    private Object reference (ReferenceNode node)
    {
        Scope scope = reactor.get(node, "scope");
        DeclarationNode decl = reactor.get(node, "decl");

        if (decl instanceof VarDeclarationNode
        || decl instanceof SyntheticDeclarationNode
                && ((SyntheticDeclarationNode) decl).kind() == DeclarationKind.VARIABLE)
            return scope == rootScope
                ? rootStorage.get(scope, node.name)
                : storage.get(scope, node.name);
        else if (decl instanceof ParameterNode){
            if (scope == rootScope) {
                return rootStorage.get(scope, node.name);
            } else {
                Object ref = storage.get(scope, node.name);
                return ref;
            }
        }
        else if (decl instanceof SymbolicVarDeclarationNode)
            return storage.get(scope, SymbolicVarDeclarationNode.name);

        return decl; // structure or function
    }

    // ---------------------------------------------------------------------------------------------

    private Void returnStmt (ReturnNode node) {
        throw new Return(node.expression == null ? null : get(node.expression));
    }

    // ---------------------------------------------------------------------------------------------

    private Void varDecl (VarDeclarationNode node)
    {
        Scope scope = reactor.get(node, "scope");
        assign(scope, node.name, get(node.initializer), reactor.get(node, "type"));
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private void assign (Scope scope, String name, Object value, Type targetType)
    {
        if (targetType.isArrayLike() && ((ArrayLikeType) targetType).componentType instanceof FloatType){
            if (value instanceof Object[][]){
                value = Arrays.stream((Object[][]) value)
                    .map(line -> Arrays.stream(line)
                        .map(o -> {
                            if (o instanceof Double)
                                return (Double) o;
                            else
                                return ((Long) o).doubleValue();
                        })
                        .toArray(Double[]::new))
                    .toArray(Double[][]::new);
            }
            else if (value instanceof Object[]){
                value = Arrays.stream((Object[]) value)
                    .map(o -> {
                        if (o instanceof Double)
                            return (Double) o;
                        else
                            return ((Long) o).doubleValue();
                    })
                    .toArray(Double[]::new);
            }

        }
        else if (value instanceof Long && targetType instanceof FloatType)
            value = ((Long) value).doubleValue();
        storage.set(scope, name, value);
    }

    // ---------------------------------------------------------------------------------------------
}
