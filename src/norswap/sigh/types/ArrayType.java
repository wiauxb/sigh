package norswap.sigh.types;

public final class ArrayType extends ArrayLikeType
{

    public ArrayType (Type componentType) {
        super(componentType);
    }

    @Override public String name() {
        return componentType.toString() + "[]";
    }

    @Override public boolean equals (Object o) {
        return this == o || o instanceof ArrayType && componentType.equals(((ArrayType)o).componentType)
            || o instanceof MatType && componentType instanceof ArrayType &&
            ((ArrayType)componentType).componentType.equals(((MatType) o).componentType);
    }

}
