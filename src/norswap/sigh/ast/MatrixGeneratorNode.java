package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import java.util.Arrays;
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
            this.shape = Arrays.asList(new IntLiteralNode(this.shape.get(0).span, 1), this.shape.get(0));
        }
    }

    @Override public String contents () {
        return format("[%s](%s, %s)", filler.contents(), shape.get(0).contents(), shape.get(1).contents());
    }
}
