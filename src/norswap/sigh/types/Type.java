package norswap.sigh.types;

public abstract class Type
{
    /**
     * Return the name of the type such as it may be written in a program.
     */
    public abstract String name();

    /**
     * Whether this is a primitive (non-pointer) type.
     * A primitive type can't also be a reference type.
     */
    public boolean isPrimitive() {
        return false;
    }

    /**
     * Whether this is a reference ("pointer") type.
     * A reference type can't also be a primitive type.
     */
    public boolean isReference() {
        return !isPrimitive();
    }

    /**
     * Whether this is a Matrix or ArrayType.
     */
    public boolean isArrayLike() {
        return false;
    }

    @Override public String toString() {
        return name();
    }
}
