package norswap.sigh.ast;

public enum SymbolicValue {

    MATCH_ELEM("_");

    public final String string;

    SymbolicValue(String string){
        this.string = string;
    }

    public String contents(){
        return string;
    }

}
