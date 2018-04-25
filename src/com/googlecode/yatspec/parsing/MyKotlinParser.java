package com.googlecode.yatspec.parsing;

import com.github.sarahbuisson.kotlinparser.KotlinLexer;
import com.github.sarahbuisson.kotlinparser.KotlinParser;
import com.github.sarahbuisson.kotlinparser.KotlinParserBaseListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class MyKotlinParser {


    public ParsedFile parse(String path) {
        try {
            KotlinLexer KotlinLexer = new KotlinLexer(CharStreams.fromStream(new FileInputStream(new File(path))));
//            KotlinLexer KotlinLexer = new KotlinLexer(CharStreams.fromStream(MyKotlinParser.class.getResourceAsStream(path)));

            CommonTokenStream commonTokenStream = new CommonTokenStream(KotlinLexer);
            KotlinParser kotlinParser = new KotlinParser(commonTokenStream);

            ParseTree tree = kotlinParser.kotlinFile();
            ParseTreeWalker walker = new ParseTreeWalker();


            //WHEN
            MethodSourceParser parser = new MethodSourceParser();
            walker.walk(parser, tree);
            return new ParsedFile(parser.methods);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static class ParsedFile {
        List<Method> methods;


        ParsedFile(List<Method> methods) {
            this.methods = methods;
        }
    }

    public static class Method {
        public final String name;
        public final List<String> paremeters;
        public final String body;

        public Method(String name, List<String> parsedParameters, String body) {
            this.name = name;
            this.paremeters = parsedParameters;
            this.body = body;
        }
    }

    class MethodSourceParser extends KotlinParserBaseListener {

        List<Method> methods = new ArrayList<Method>();

        @Override
        public void enterFunctionDeclaration(KotlinParser.FunctionDeclarationContext ctx) {
            List<KotlinParser.FunctionValueParameterContext> parameters = ctx.functionValueParameters().functionValueParameter();
            List<String> parsedParameters = new ArrayList<String>();
            for (int i = 0; i < parameters.size(); i++) {
                parsedParameters.add(parameters.get(i).parameter().simpleIdentifier().getText());
            }
//            parameters.get(0).parameter().simpleIdentifier().getText()
            methods.add(new Method(ctx.identifier().getText(), parsedParameters, ctx.functionBody().getText()));
        }
    }


}
