package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import java.util.List;

import static norswap.utils.Util.cast;

public class CaseBodyNode extends StatementNode{

    public final List<Object> pattern;
    public final BlockNode statements;

    @SuppressWarnings("unchecked")
    public CaseBodyNode(Span span, Object pattern, Object statements){
        super(span);
        this.pattern = cast(pattern, List.class);
        this.statements = cast(statements, BlockNode.class);
    }


    @Override
    public String contents () {
        StringBuilder string = new StringBuilder("[");

        for (Object o : pattern) {
            if (o instanceof SymbolicValue) string.append(((SymbolicValue) o).string);
            else string.append(((ExpressionNode) o).contents());
            string.append(",");
        }
        if (pattern.size() > 0) string.deleteCharAt(string.length()-1);
        string.append("] : ");
        string.append(statements.contents());
        return string.toString();
    }
}
