package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

import static java.lang.String.format;

public class MatrixGeneratorNode extends ExpressionNode
{
    public final ExpressionNode filler;
    public final IntLiteralNode shape1, shape2;

    public MatrixGeneratorNode (Span span, Object filler, Object shape) {
        super(span);
        this.filler = Util.cast(filler, ExpressionNode.class);
        this.shape2 = Util.cast(shape, IntLiteralNode.class);
        this.shape1 = new IntLiteralNode(this.shape2.span, 1);
    }

    public MatrixGeneratorNode (Span span, Object filler, Object shape1, Object shape2) {
        super(span);
        this.filler = Util.cast(filler, ExpressionNode.class);
        this.shape1 = Util.cast(shape1, IntLiteralNode.class);
        this.shape2 = Util.cast(shape2, IntLiteralNode.class);
    }

    @Override public String contents () {
        return format("[%s](%s, %s)", filler.contents(), shape1.contents(), (shape2 != null) ? shape2.contents() : "null");
    }
}