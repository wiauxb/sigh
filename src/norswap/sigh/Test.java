package norswap.sigh;

import norswap.autumn.Autumn;
import norswap.autumn.ParseOptions;
import norswap.autumn.ParseResult;
import norswap.autumn.positions.LineMap;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.ast.SighNode;
import norswap.sigh.interpreter.Interpreter;
import norswap.uranium.AttributeTreeFormatter;
import norswap.uranium.Reactor;
import norswap.utils.IO;
import norswap.utils.visitors.Walker;
import java.nio.file.Paths;

import static norswap.utils.Util.cast;

public final class Test
{
    public static void main (String[] args) {
//         String file = "fizzbuzz.si";
//        String file = "kitchensink.si";
//        String file = "test.si";
        String file = "example_file.si";
//        String file = "integration_test.si";
//        String file = "tests_tordus.si";
//        String file = "case.si";
        String path = Paths.get("examples/", file).toAbsolutePath().toString();
        String src = IO.slurp(path);
        SighGrammar grammar = new SighGrammar();
        ParseOptions options = ParseOptions.builder().recordCallStack(true).get();
        ParseResult result = Autumn.parse(grammar.root, src, options);
        LineMap lineMap = new LineMapString(path, src);
        System.out.println(result.toString(lineMap, false));

        if (!result.fullMatch)
            return;

        SighNode tree = cast(result.topValue());
        Reactor reactor = new Reactor();
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        walker.walk(tree);

        reactor.run();

//        System.out.println(
//            AttributeTreeFormatter.formatWalkFields(tree, reactor, SighNode.class));

        if (!reactor.errors().isEmpty()) {
            System.out.println(reactor.reportErrors(it ->
                it.toString() + " (" + ((SighNode) it).span.startString(lineMap) + ")"));
            return;
        }

//        System.out.println(
//            AttributeTreeFormatter.formatWalkFields(tree, reactor, SighNode.class));


        Interpreter interpreter = new Interpreter(reactor);
        interpreter.interpret(tree);
        System.out.println("success");
    }
}
