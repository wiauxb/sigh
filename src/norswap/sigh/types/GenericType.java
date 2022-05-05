package norswap.sigh.types;


import static java.lang.String.format;

public final class GenericType extends Type
{
    public static final GenericType UNKNOWN = new GenericType("unknown");

    public String name;
    public Type resolution;

    public GenericType (String name) {
        this.name = name;
    }

    public GenericType (String name, Type resolution) {
        this.name = name;
        this.resolution = resolution;
    }

    @Override public String name() {
        return name+" " + format("(%s)", (resolution == null) ? "Generic" : resolution.toString());
    }

    public boolean solve(Type res){
        if (resolution == null) {
            resolution = res;
            return true;
        }
        return false;
    }

    public void reset(){
        resolution = null;
    }

    @Override
    public boolean equals (Object obj) {
        return obj instanceof GenericType
            && name.equals(((GenericType) obj).name);
    }
}
