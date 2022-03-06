package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class SlicingAccessNode extends ExpressionNode
{
    public final ExpressionNode array;
    public final ExpressionNode startIndex;
    public final ExpressionNode endIndex;

    public SlicingAccessNode (Span span, Object array) {  // array[:]
        super(span);
        this.array = Util.cast(array, ExpressionNode.class);
        this.startIndex = new IntLiteralNode(span, 0);
        this.endIndex = new IntLiteralNode(span, -1);
    }

    public SlicingAccessNode (Span span, Object array, Object startIndex) {  // array[x:]
        super(span);
        this.array = Util.cast(array, ExpressionNode.class);
        this.startIndex = Util.cast(startIndex, ExpressionNode.class);
        this.endIndex = new IntLiteralNode(span, -1);
    }

    public SlicingAccessNode (Span span, Object array, Object startIndex, Object endIndex) {  // array[x:y] or array[:y]
        super(span);
        this.array = Util.cast(array, ExpressionNode.class);
        this.endIndex = Util.cast(endIndex, ExpressionNode.class);
        if (startIndex == null)
            this.startIndex = new IntLiteralNode(span, 0);
        else
            this.startIndex = Util.cast(startIndex, ExpressionNode.class);
    }

    @Override public String contents() {
//        if (startIndex.contents().equals("0") && startIndex.contents().equals("-1"))
//            return String.format("%s[:]", array.contents());
//        else if (startIndex.contents().equals("-1"))
//            return String.format("%s[%s:]", array.contents(), startIndex.contents());
//        else if (startIndex.contents().equals("0"))
//            return String.format("%s[:%s]", array.contents(), endIndex.contents());
//        else
            return String.format("%s[%s:%s]", array.contents(), startIndex.contents(), endIndex.contents());
    }
}
