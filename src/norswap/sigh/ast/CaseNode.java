package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import java.util.ArrayList;
import java.util.List;

import static norswap.utils.Util.cast;

public class CaseNode extends StatementNode{

    ExpressionNode element;
    List<CaseBodyNode> body;
    BlockNode defaultBlock;

    @SuppressWarnings("unchecked")
    public CaseNode (Span span, Object element, Object body, Object defaultBlock) {
        super(span);
        this.element = cast(element, ExpressionNode.class);
        this.body = cast(body, List.class);
        if (defaultBlock != null)
            this.defaultBlock = cast(defaultBlock, BlockNode.class);
        else
            this.defaultBlock = new BlockNode(span, new ArrayList<>());
    }

    @Override
    public String contents () {
        StringBuilder s = new StringBuilder("case " + this.element.contents() + "{\n");
        for (CaseBodyNode caseBodyNode : this.body) {
            s.append(caseBodyNode.contents()).append(",\n");
        }
        s.append(this.defaultBlock.contents());
        s.append("\n}");
        return s.toString();
    }
}
