package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

import java.util.List;

import static java.lang.String.format;
import static norswap.utils.Util.cast;

public class MatrixGeneratorNode extends ExpressionNode
{
    public final ExpressionNode filler;
//    public final ExpressionNode shape1, shape2;
    public List<ExpressionNode> shape;

    @SuppressWarnings("unchecked")
    public MatrixGeneratorNode (Span span, Object filler, Object shape) {
        super(span);
        this.filler = cast(filler, ExpressionNode.class);
        this.shape = cast(shape, List.class);
        if (this.shape.size() == 1){
            this.shape = List.of(new ExpressionNode[]{new IntLiteralNode(this.shape.get(0).span, 1), this.shape.get(0)});
        //.add(0, new IntLiteralNode(this.shape.get(0).span, 1));
        }
//        Object[] shapes = (Object[]) shape;
//        if (shapes.length == 1){
//            this.shape2 = Util.cast(shapes[0], ExpressionNode.class);
//            this.shape1 = new IntLiteralNode(this.shape2.span, 1);
//        }
//        else {
//            this.shape1 = Util.cast(shapes[0], ExpressionNode.class);
//            this.shape2 = Util.cast(shapes[1], ExpressionNode.class);
//        }
    }

    @Override public String contents () {
        return format("[%s](%s, %s)", filler.contents(), shape.get(0).contents(), shape.get(1).contents());
    }
}
