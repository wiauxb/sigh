package norswap.sigh.types;

public final class SymbolicType extends Type
{
    public static final SymbolicType INSTANCE = new SymbolicType();
    private SymbolicType () {}

    @Override public boolean isPrimitive () {
        return true;
    }

    @Override public String name() {
        return "Sym";
    }

    @Override
    public boolean equals (Object obj) {
        return obj instanceof Type ;//&& ((Type) obj).isPrimitive();
    }
}
