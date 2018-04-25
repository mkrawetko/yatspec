package com.googlecode.yatspec.parsing;

import com.googlecode.totallylazy.Callable1;
import com.googlecode.totallylazy.Option;
import com.googlecode.totallylazy.Predicate;
import com.googlecode.totallylazy.Sequence;
import com.googlecode.yatspec.state.TestMethod;
import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.Annotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaMethod;
import org.junit.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.googlecode.totallylazy.Files.*;
import static com.googlecode.totallylazy.Methods.annotation;
import static com.googlecode.totallylazy.Option.none;
import static com.googlecode.totallylazy.Option.option;
import static com.googlecode.totallylazy.Predicates.*;
import static com.googlecode.totallylazy.Sequences.empty;
import static com.googlecode.totallylazy.Sequences.sequence;
import static com.googlecode.totallylazy.Strings.endsWith;
import static com.googlecode.totallylazy.URLs.toURL;
import static com.googlecode.yatspec.parsing.Files.*;

public class TestParser {

    private static final Option<URL> NO_URL = none(URL.class);

    public static List<TestMethod> parseTestMethods(Class aClass) {
        final Sequence<java.lang.reflect.Method> methods = getMethods(aClass);
        return collectTestMethods(aClass, methods).toList();
    }

    private static Sequence<TestMethod> collectTestMethods(Class aClass, Sequence<java.lang.reflect.Method> methods) {
//        final Option<JavaClass> javaClass = getJavaClass(aClass);
        Option<KotlinClass> kotlinClass = getKotlinClass(aClass);
        if (kotlinClass.isEmpty()) {
            return empty();
        }

        Map<String, Method> sourceMethodsByName = new HashMap<String, Method>();
        for (int i = 0; i < methods.size(); i++) {
            if (methods.get(i).isAnnotationPresent(Test.class)) {
                sourceMethodsByName.put(methods.get(i).getName(), methods.get(i));
            }
        }

        Map<String, KotlinClass.Method> sourceKMethodsByName = new HashMap<String, KotlinClass.Method>();

        for (Map.Entry<String, Method> testMethod : sourceMethodsByName.entrySet()) {
            for (int j = 0; j < kotlinClass.get().methods.size(); j++) {
                KotlinClass.Method candidate = kotlinClass.get().methods.get(j);
                if (testMethod.getKey().equals(candidate.name)) {
                    sourceKMethodsByName.put(candidate.name, candidate);
                }
            }
        }

//        Map<String, List<KotlinClass.Method>> sourcekMethodsByName = getMethods(kotlinClass.get()).toMap(kSourceMethodName());
        Map<String, List<java.lang.reflect.Method>> reflectionMethodsByName = methods.toMap(reflectionMethodName());

        List<TestMethod> testMethods = new ArrayList<TestMethod>();
        TestMethodExtractor extractor = new TestMethodExtractor();
        for (String name : sourceMethodsByName.keySet()) {
            Method sourceMethods = sourceMethodsByName.get(name);
            KotlinClass.Method kmethod = sourceKMethodsByName.get(name);
            List<java.lang.reflect.Method> reflectionMethods = reflectionMethodsByName.get(name);
            testMethods.add(extractor.toTestMethod(aClass, sourceMethods, kmethod));
            // TODO: If people overload test methods we will have to use the full name rather than the short name
        }

        Sequence<TestMethod> myTestMethods = sequence(testMethods);
        Sequence<TestMethod> parentTestMethods = collectTestMethods(aClass.getSuperclass(), methods);

        return myTestMethods.join(parentTestMethods);
    }

    private static Callable1<? super java.lang.reflect.Method, String> reflectionMethodName() {
        return new Callable1<java.lang.reflect.Method, String>() {
            @Override
            public String call(java.lang.reflect.Method method) {
                return method.getName();
            }
        };
    }

    private static Callable1<JavaMethod, String> sourceMethodName() {
        return new Callable1<JavaMethod, String>() {
            @Override
            public String call(JavaMethod javaMethod) {
                return javaMethod.getName();
            }
        };
    }

    private static Callable1<KotlinClass.Method, String> kSourceMethodName() {
        return new Callable1<KotlinClass.Method, String>() {
            @Override
            public String call(KotlinClass.Method method) {
                return method.name;
            }
        };
    }

    private static Option<JavaClass> getJavaClass(final Class aClass) {
        Option<URL> option = getJavaSourceFromClassPath(aClass);
        option = !option.isEmpty() ? option : getJavaSourceFromFileSystem(aClass);
        return option.map(asAJavaClass(aClass));
    }

    private static Option<KotlinClass> getKotlinClass(final Class aClass) {
        Option<URL> option = getKotlinSourceFromClassPath(aClass);
        option = !option.isEmpty() ? option : getKotlinSourceFromFileSystem(aClass);
        return option.map(asAKotlinClass(aClass));
    }

    private static Callable1<URL, JavaClass> asAJavaClass(final Class aClass) {
        return new Callable1<URL, JavaClass>() {
            @Override
            public JavaClass call(URL url) throws Exception {
                JavaDocBuilder builder = new JavaDocBuilder();
                builder.addSource(url);
                return builder.getClassByName(aClass.getName());
            }
        };
    }

    private static Callable1<URL, KotlinClass> asAKotlinClass(final Class aClass) {
        return new Callable1<URL, KotlinClass>() {
            @Override
            public KotlinClass call(URL url) {
                List<KotlinClass.Method> methods = new ArrayList<KotlinClass.Method>();

                MyKotlinParser.ParsedFile parsedFile = new MyKotlinParser().parse(url.getPath());
                for (MyKotlinParser.Method parsedMethod : parsedFile.methods) {
                    Method javaMethod = getMethodBody(aClass.getDeclaredMethods(), parsedMethod.name);
                    if (javaMethod != null) {
                        List<KotlinClass.Annotation> annotations = new ArrayList<KotlinClass.Annotation>();
                        for (java.lang.annotation.Annotation a : javaMethod.getDeclaredAnnotations()) {
                            annotations.add(new KotlinClass.Annotation(a.getClass().getName()));
                        }

                        methods.add(new KotlinClass.Method(javaMethod.getName(), annotations, parsedMethod.body, parsedMethod.paremeters));
                    }
                }
                return new KotlinClass(aClass.getName(), methods);
            }

            private Method getMethodBody(Method[] javaMethods, String name) {
                for (Method javaMethod : javaMethods) {
                    if (name.equals(javaMethod.getName())) {
                        return javaMethod;
                    }
                }
                return null;
            }
        };
    }

    private static Option<URL> getKotlinSourceFromClassPath(Class aClass) {
        return isObject(aClass) ? NO_URL : option(aClass.getClassLoader().getResource(toKotlinResourcePath(aClass)));
    }


    private static Sequence<java.lang.reflect.Method> getMethods(Class aClass) {
        return sequence(aClass.getMethods()).filter(where(annotation(Test.class), notNullValue()));
    }

    @SuppressWarnings("unchecked")
    private static Sequence<JavaMethod> getMethods(JavaClass javaClass) {
        return sequence(javaClass.getMethods()).filter(where(annotations(), contains(Test.class)));
    }

    @SuppressWarnings("unchecked")
    private static Sequence<KotlinClass.Method> getMethods(KotlinClass clazz) {
        return sequence(clazz.methods).filter(where(kAnnotations(), kContains(Test.class)));
    }

    private static Option<URL> getJavaSourceFromClassPath(Class aClass) {
        return isObject(aClass) ? NO_URL : option(aClass.getClassLoader().getResource(toJavaResourcePath(aClass)));
    }

    private static Option<URL> getKotlinSourceFromFileSystem(Class aClass) {
        return isObject(aClass) ? NO_URL : recursiveFiles(workingDirectory()).find(where(path(), endsWith(toKotlinPath(aClass)))).map(toURL());
    }

    private static Option<URL> getJavaSourceFromFileSystem(Class aClass) {
        return isObject(aClass) ? NO_URL : recursiveFiles(workingDirectory()).find(where(path(), endsWith(toJavaPath(aClass)))).map(toURL());
    }

    public static Callable1<? super Annotation, String> name() {
        return new Callable1<Annotation, String>() {
            @Override
            public String call(Annotation annotation) {
                return annotation.getType().getFullyQualifiedName();
            }
        };
    }

    public static Callable1<? super KotlinClass.Annotation, String> kname() {
        return new Callable1<KotlinClass.Annotation, String>() {
            @Override
            public String call(KotlinClass.Annotation annotation) {
                return annotation.name;
            }
        };
    }

    private static boolean isObject(Class aClass) {
        return aClass.equals(Object.class);
    }

    private static Predicate<? super Sequence<Annotation>> contains(final Class aClass) {
        return new Predicate<Sequence<Annotation>>() {
            @Override
            public boolean matches(Sequence<Annotation> annotations) {
                return annotations.exists(where(name(), is(aClass.getName())));
            }
        };
    }

    private static Predicate<? super Sequence<KotlinClass.Annotation>> kContains(final Class aClass) {
        return new Predicate<Sequence<KotlinClass.Annotation>>() {
            @Override
            public boolean matches(Sequence<KotlinClass.Annotation> annotations) {
                return annotations.exists(where(kname(), is(aClass.getName())));
            }
        };
    }

    public static Callable1<? super JavaMethod, Sequence<Annotation>> annotations() {
        return new Callable1<JavaMethod, Sequence<Annotation>>() {
            @Override
            public Sequence<Annotation> call(JavaMethod javaMethod) {
                return sequence(javaMethod.getAnnotations());
            }
        };
    }

    public static Callable1<KotlinClass.Method, Sequence<KotlinClass.Annotation>> kAnnotations() {
        return new Callable1<KotlinClass.Method, Sequence<KotlinClass.Annotation>>() {
            @Override
            public Sequence<KotlinClass.Annotation> call(KotlinClass.Method method) {
                return sequence(method.annotations);
            }
        };
    }

    public static class KotlinClass {

        public final String name;
        public List<Method> methods;

        public KotlinClass(String name, List<Method> methods) {
            this.name = name;
            this.methods = methods;
        }

        public static class Method {

            public final String name;
            public List<Annotation> annotations;
            public String sourceCode;
            private final List<String> parameters;

            public Method(String name, List<Annotation> annotations, String body, List<String> parameters) {
                this.name = name;
                this.annotations = annotations;
                this.sourceCode = body;
                this.parameters = parameters;
            }

            public List<String> getParameters() {
                return parameters;
            }
        }

        public static class Annotation {

            public final String name;

            public Annotation(String name) {
                this.name = name;
            }
        }
    }


}
