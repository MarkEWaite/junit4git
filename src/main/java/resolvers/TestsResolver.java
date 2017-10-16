package resolvers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Ignore;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.*;

public abstract class TestsResolver {

    protected Set<String> getTestsToIgnore(InputStream is) throws IOException, GitAPIException {
        JsonArray tests =  new JsonParser().parse(new InputStreamReader(is)).getAsJsonArray();
        return testsToIgnore(runGitStatus(), tests);
    }

    private boolean isModified(String testClassName, Set<String> status) {
        Iterator<String> it = status.iterator();
        boolean found = false;
        while(it.hasNext() && !found) {
            String fileName = it.next();
            found = fileName.endsWith(testClassName +".java");
        }
        return found;
    }

    private Set<String> testsToIgnore(Set<String> status, JsonArray tests) {
        Set<String> testsToIgnore = new HashSet<>();
        Iterator<JsonElement> it = tests.iterator();
        while(it.hasNext()) {
            JsonObject json = it.next().getAsJsonObject();
            String testClass = json.get("test").getAsString();
            String testMethod = json.get("method").getAsString();
            if (!isModified(testClass, status)) {
                JsonArray classes = json.get("classes").getAsJsonArray();
                Iterator<JsonElement> itClasses = classes.iterator();
                boolean found = false;
                while (itClasses.hasNext() && !found) {
                    found = isModified(itClasses.next().getAsString(), status);
                }
                if (!found) {
                    testsToIgnore.add(testClass + "#" + testMethod);
                }
            }
        }
        return testsToIgnore;
    }

    protected Set<String> runGitStatus() throws IOException, GitAPIException {
        Set<String> changed = new LinkedHashSet<>();
        Git git = Git.open(new File(".").getCanonicalFile());
        try {

            Status status = git.status().call();
            changed.addAll(status.getModified());
        } finally {
            git.close();
        }
        return changed;
    }

    private Map<String, List<String>> testsByClass() throws Exception{
        Set<String> tests = getTestsToIgnore();
        Iterator<String> it = tests.iterator();
        Map<String, List<String>> result = new HashMap<>();
        while(it.hasNext()) {
            String next = it.next();
            String[] parts = next.split("#");
            List<String> aux = result.get(parts[0]);
            if (aux == null) {
                aux = new LinkedList<>();
                result.put(parts[0], aux);
            }
            aux.add(parts[1]);
        }
        return result;
    }

    public void ignoreTests(Instrumentation inst) throws Exception {
        ClassPool pool = ClassPool.getDefault();

        Map<String, List<String>> testsToMap = testsByClass();

        Iterator<String> it = testsToMap.keySet().iterator();

        while (it.hasNext()) {
            String className = it.next();
            CtClass clazz = pool.get(className);
            ClassFile ccFile = clazz.getClassFile();
            ConstPool constpool = ccFile.getConstPool();
            List<String> methods = testsToMap.get(className);
            methods.forEach(md -> {
                try {
                    CtMethod method = clazz.getDeclaredMethod(md);

                    AnnotationsAttribute attr = (AnnotationsAttribute) method.getMethodInfo().getAttribute(AnnotationsAttribute.visibleTag);

                    attr.addAnnotation(new Annotation(Ignore.class.getName(), constpool));
                    method.getMethodInfo().addAttribute(attr);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            inst.redefineClasses(new ClassDefinition(Class.forName(className), clazz.toBytecode()));

        }
    }

    public abstract Set<String> getTestsToIgnore() throws Exception;
}
