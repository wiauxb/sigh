package norswap.sigh.types;

public abstract class ArrayLikeType extends Type{

    public final Type componentType;

    public ArrayLikeType (Type componentType) {
        this.componentType = componentType;
    }

    @Override public int hashCode () {
        return componentType.hashCode();
    }

    @Override
    public boolean isArrayLike () {
        return true;
    }
}
