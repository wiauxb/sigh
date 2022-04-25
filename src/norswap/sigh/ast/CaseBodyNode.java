package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import java.beans.Expression;
import java.util.List;

import static norswap.utils.Util.cast;

public class CaseBodyNode extends StatementNode{

    public final ExpressionNode pattern;
    public final BlockNode statements;

    @SuppressWarnings("unchecked")
    public CaseBodyNode(Span span, Object pattern, Object statements){
        super(span);
        this.pattern = cast(pattern, ExpressionNode.class);
        this.statements = cast(statements, BlockNode.class);
    }


    @Override
    public String contents () {
        return pattern.contents() + " : " +
            statements.contents();
    }
}
