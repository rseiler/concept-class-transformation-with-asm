package at.rseiler.concept;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.commons.AdviceAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import static jdk.internal.org.objectweb.asm.Opcodes.ASM5;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESTATIC;

/**
 * A demo how to do byte code transformation with ASM.
 * <p/>
 * The program will load the HelloWorld class file and manipulate the byte code:
 * <ul>
 * <li>1. Wraps static {@link Logger} into the {@link LoggerWrapper#logger(Logger)}</li>
 * <li>2. Adds at the beginning of each method a call to {@link MethodLogger#log(String, Object...)}</li>
 * </ul>
 * <p/>
 * 1.
 * private static final Logger logger1 = Logger.getLogger(HelloWorld.class.getName());
 * will be transformed into:
 * private static final Logger logger1 = LoggerWrapper.logger(Logger.getLogger(HelloWorld.class.getName()));
 * <p/>
 * 2.
 * public String foo(String arg) {
 *  return bar("foo", arg);
 * }
 * will be transformed into:
 * public String foo(String arg) {
 *  MethodLogger.log("foo", arg);
 *  return bar("foo", arg);
 * }
 * <p/>
 * <p/>
 * You shouldn't relay on the ASM version packed into the jdk for production code!
 * Because if a new Java version will be shipped than it could contain a new version of AMS (or remove ASM) which will break your code.
 * Therefor you must repackage ASM into your own namespace, to prevent version conflicts, and ship it with your library.
 * <p/>
 * Because this is non production code and I am lazy I didn't do it.
 * <p/>
 * IMPORTANT: If you try to run the program on a JMV other than the JDK8 it will probably fail.
 *
 * @author reinhard.seiler@gmail.com
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // creates the ASM ClassReader which will read the class file
        ClassReader classReader = new ClassReader(new FileInputStream(new File("HelloWorld.class")));
        // creates the ASM ClassWriter which will create the transformed class
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        // creates the ClassVisitor to do the byte code transformations
        ClassVisitor classVisitor = new MyClassVisitor(ASM5, classWriter);
        // reads the class file and apply the transformations which will be written into the ClassWriter
        classReader.accept(classVisitor, 0);

        // gets the bytes from the transformed class
        byte[] bytes = classWriter.toByteArray();
        // writes the transformed class to the file system - to analyse it (e.g. javap -verbose)
        new FileOutputStream(new File("HelloWorld$$Transformed.class")).write(bytes);

        // inject the transformed class into the current class loader
        ClassLoader classLoader = Main.class.getClassLoader();
        Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
        defineClass.setAccessible(true);
        Class<?> helloWorldClass = (Class<?>) defineClass.invoke(classLoader, null, bytes, 0, bytes.length);

        // creates an instance of the transformed class
        Object helloWorld = helloWorldClass.newInstance();
        Method hello = helloWorldClass.getMethod("hello");
        // class the hello method
        hello.invoke(helloWorld);
    }

    private static class MyClassVisitor extends ClassVisitor {

        public MyClassVisitor(int i, ClassVisitor classVisitor) {
            super(i, classVisitor);
        }

        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if (cv == null) {
                return null;
            }

            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            // <clinit> defines the static block in which the assignment of static variables happens.
            // E.g. private static final Logger logger = Logger.getLogger(HelloWorld.class.getName());
            // The assignment of the logger variable happens in <clinit>.
            if ("<clinit>".equals(name)) {
                return new StaticBlockMethodVisitor(mv);
            } else {
                // all other methods (static and none static)
                return new MethodLogger(mv, access, name, desc);
            }
        }

        class StaticBlockMethodVisitor extends MethodVisitor {
            StaticBlockMethodVisitor(MethodVisitor mv) {
                super(ASM5, mv);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                // checks for: putstatic // Field *:Ljava/util/logging/Logger;
                if ("Ljava/util/logging/Logger;".equals(desc)) {
                    // adds before the putstatic opcode the call to LoggerWrapper#logger(Logger) to wrap the logger instance
                    super.visitMethodInsn(INVOKESTATIC, "at/rseiler/concept/LoggerWrapper", "logger", "(Ljava/util/logging/Logger;)Ljava/util/logging/Logger;", false);
                }
                // do the default behaviour: add the putstatic opcode to the byte code
                super.visitFieldInsn(opcode, owner, name, desc);
            }
        }

        class MethodLogger extends AdviceAdapter {

            private final int access;
            private final String name;
            private final String desc;

            protected MethodLogger(MethodVisitor mv, int access, String name, String desc) {
                super(ASM5, mv, access, name, desc);
                this.access = access;
                this.name = name;
                this.desc = desc;
            }

            @Override
            protected void onMethodEnter() {
                // checks if the method is static.
                // The difference is that "this" is stored in ALOAD_0 and the arguments are stored in ALOAD_1, ALOAD_2, ...
                // But there is no "this" for a static method call. Therefor the arguments are stored in ALOAD_0, ALOAD_1 ,...
                // If we want to access the arguments we need to differentiate between static and non static method calls.
                boolean isStatic = (access & ACC_STATIC) > 0;

                int length = Type.getArgumentTypes(desc).length;

                // pushes the method name on the stack
                super.visitLdcInsn(name);
                // pushes the count of arguments on the stack
                // could be optimized if we would use iconst_0, iconst_1, ..., iconst_5 for 0 to 5.
                super.visitIntInsn(BIPUSH, length);
                // creates an object array with the count of arguments
                super.visitTypeInsn(ANEWARRAY, "java/lang/Object");

                // stores the arguments in the array
                for (int i = 0; i < length; i++) {
                    // duplicates the reference to the array. Because the AASTORE opcode consumes the stack element with the reference to the array.
                    super.visitInsn(DUP);
                    // could be optimized
                    super.visitIntInsn(BIPUSH, i);
                    // puts the value of the current argument on the stack
                    super.visitVarInsn(ALOAD, i + (isStatic ? 0 : 1));
                    // stores the value of the current argument in the array
                    super.visitInsn(AASTORE);
                }

                // calls the MethodLogger#log(String, Object...) method with the corresponding arguments - which we created just before
                super.visitMethodInsn(INVOKESTATIC, "at/rseiler/concept/MethodLogger", "log", "(Ljava/lang/String;[Ljava/lang/Object;)V", false);
            }
        }

    }

}
