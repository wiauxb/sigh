package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class SymbolicVarDeclarationNode extends DeclarationNode
{
    public final String name = "_";

    public SymbolicVarDeclarationNode (Span span) {
        super(span);
    }

    @Override public String name () {
        return name;
    }

    @Override public String contents () {
        return "symvar " + name;
    }

    @Override public String declaredThing () {
        return "variable";
    }
}
