/**
 *  Copyright 2012 Douglas Campos <qmx@qmx.me>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.qmx.jitescript;

import static me.qmx.jitescript.CodeBlock.newCodeBlock;
import static me.qmx.jitescript.util.CodegenUtils.p;
import static me.qmx.jitescript.util.CodegenUtils.sig;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/**
 * Represents a Java Class
 *
 * @author qmx
 */
public class JiteClass implements Opcodes {

    public static final String[] INTERFACES = new String[]{};
    private final List<MethodDefinition> methods = new ArrayList<>();
    private final List<FieldDefinition> fields = new ArrayList<>();
    private final List<String> interfaces = new ArrayList<>();
    private final List<VisibleAnnotation> annotations = new ArrayList<>();
    private final List<ChildEntry> childClasses = new ArrayList<>();
    private final String className;
    private final String superClassName;
    private String sourceFile;
    private String sourceDebug;
    private int access = ACC_PUBLIC | ACC_SUPER;
    private String parentClassName;

    /**
     * Creates a new class representation
     *
     * @param className the desired class name
     */
    public JiteClass(String className) {
        this(className, INTERFACES);
    }

    /**
     * Creates a new class representation
     *
     * @param className  the desired class name
     * @param interfaces the desired java interfaces this class will implement
     */
    public JiteClass(String className, String[] interfaces) {
        this(className, p(Object.class), interfaces);
    }

    /**
     * Creates a new class representation
     *
     * @param className      the desired class name
     * @param superClassName the desired parent class
     * @param interfaces     the desired java interfaces this class will implement
     */
    public JiteClass(String className, String superClassName, String[] interfaces) {
        this.className = className;
        this.superClassName = superClassName;
        for (String anInterface : interfaces) {
            this.interfaces.add(anInterface);
        }
    }

    public int getAccess() {
        return access;
    }

    public String getClassName() {
        return className;
    }

    public String getSuperClassName() {
        return superClassName;
    }

    public String getParentClassName() {
        return parentClassName;
    }

    public List<MethodDefinition> getMethods() {
        return methods;
    }

    public List<FieldDefinition> getFields() {
        return fields;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public void addInterface(String iface) {
        this.interfaces.add(iface);
    }

    public List<VisibleAnnotation> getAnnotations() {
        return annotations;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public String getSourceDebug() {
        return sourceDebug;
    }

    public void setAccess(int access) {
        this.access = access;
        if ((this.access & ACC_INTERFACE) == ACC_INTERFACE) {
            // spec: interfaces must have the abstract flag
            this.access |= ACC_ABSTRACT;
            // spec: interfaces must not have final, super or enum flags
            if (   (this.access & ACC_FINAL) != 0
                || (this.access & ACC_SUPER) != 0
                || (this.access & ACC_ENUM) != 0) {
                throw new IllegalArgumentException("Interfaces must not have ACC_FINAL, ACC_SUPER or ACC_ENUM flags.");
            }
        } else {
            // spec: classes must have super flag
            this.access |= ACC_SUPER;
        }
    }

    public void setParentClassName(String parentClassName) {
        this.parentClassName = parentClassName;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public void setSourceDebug(String sourceDebug) {
        this.sourceDebug = sourceDebug;
    }

    public void addChildClass(JiteClass child) {
        String childName = child.getClassName();
        if (childName.contains("$")) {
            childName = childName.substring(childName.lastIndexOf('$') + 1);
        } else {
            childName = childName.substring(childName.lastIndexOf('/') + 1);
        }
        addChildClass(childName.substring(childName.lastIndexOf('$') + 1), child);
    }

    public void addChildClass(String innerName, JiteClass child) {
        child.setParentClassName(getClassName());
        childClasses.add(new ChildEntry(innerName, child));
    }

    public List<JiteClass> getChildClasses() {
        List<JiteClass> childClasses = new ArrayList<>();
        for (ChildEntry child : this.childClasses) {
            childClasses.add(child.getJiteClass());
        }
        return childClasses;
    }

    /**
     * Defines a new method on the target class
     *
     * @param methodName the method name
     * @param modifiers  the modifier bitmask, made by OR'ing constants from ASM's {@link Opcodes} interface
     * @param signature  the method signature, on standard JVM notation
     * @param methodBody the method body
     */
    public MethodDefinition defineMethod(String methodName, int modifiers, String signature, CodeBlock methodBody) {
        MethodDefinition methodDefinition = new MethodDefinition(methodName, modifiers, signature, methodBody);
        this.methods.add(methodDefinition);
        return methodDefinition;
    }

    /**
     * Defines a new field on the target class
     *
     * @param fieldName the field name
     * @param modifiers  the modifier bitmask, made by OR'ing constants from ASM's {@link Opcodes} interface
     * @param signature  the field signature, on standard JVM notation
     * @param value the default value (null for JVM default)
     * @return the new field definition for further modification
     */
    public FieldDefinition defineField(String fieldName, int modifiers, String signature, Object value) {
        FieldDefinition field = new FieldDefinition(fieldName, modifiers, signature, value);
        this.fields.add(field);
        return field;
    }

    /**
     * Defines a default constructor on the target class
     */
    public MethodDefinition defineDefaultConstructor() {
        return defineDefaultConstructor(ACC_PUBLIC);
    }

    public MethodDefinition defineDefaultConstructor(int access) {
        return defineMethod("<init>", access, sig(void.class), newCodeBlock().aload(0)
                .invokespecial(superClassName, "<init>", sig(void.class)).voidreturn());
    }

    public MethodDefinition defineAllFieldsConstructor() {
        return defineAllFieldsConstructor(ACC_PUBLIC);
    }

    public MethodDefinition defineAllFieldsConstructor(int access) {
        CodeBlock init = newCodeBlock();
        init.aload(0);
        init.invokespecial(superClassName, "<init>", sig(void.class));

        StringBuilder params = new StringBuilder();
        for (FieldDefinition fieldDcl : fields) {
            FieldNode fieldNode = fieldDcl.getFieldNode();
            init.aload(0);
            init.putfield(className, fieldNode.name, fieldNode.signature);
            params.append(fieldNode.desc);
        }
        init.voidreturn();

        return defineMethod("<init>", access, "(" + params + ")V", init);
    }

    /**
     * Convert this class representation to JDK bytecode
     *
     * @return the bytecode representation
     */
    public byte[] toBytes() {
        return toBytes(JDKVersion.V1_6);
    }

    public void addAnnotation(VisibleAnnotation annotation) {
        annotations.add(annotation);
    }

    /**
     * Convert this class representation to JDK bytecode
     *
     * @param version the desired JDK version
     * @return the bytecode representation of this class
     */
    public byte[] toBytes(JDKVersion version) {
        ClassNode node = new ClassNode();
        node.version = version.getVer();
        node.access = this.access;
        node.name = this.className;
        node.superName = this.superClassName;
        node.sourceFile = this.sourceFile;
        node.sourceDebug = this.sourceDebug;

        if (parentClassName != null) {
            node.visitOuterClass(parentClassName, null, null);
        }

        for (ChildEntry child : childClasses) {
            node.visitInnerClass(child.getClassName(), className, child.getInnerName(), child.getAccess());
        }

        if (!this.interfaces.isEmpty()) {
            node.interfaces.addAll(this.interfaces);
        }

        for (MethodDefinition def : methods) {
            node.methods.add(def.getMethodNode());
        }

        for (FieldDefinition def : fields) {
            node.fields.add(def.getFieldNode());
        }

        if (node.visibleAnnotations == null) {
            node.visibleAnnotations = new ArrayList<AnnotationNode>();
        }

        for (VisibleAnnotation a : annotations) {
            node.visibleAnnotations.add(a.getNode());
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static final class ChildEntry {

        public final String innerName;
        public final JiteClass jiteClass;

        public ChildEntry(String innerName, JiteClass jiteClass) {
            this.innerName = innerName;
            this.jiteClass = jiteClass;
        }

        public int getAccess() {
            return jiteClass.getAccess();
        }

        public String getClassName() {
            return jiteClass.getClassName();
        }

        public String getInnerName() {
            return innerName;
        }

        public JiteClass getJiteClass() {
            return jiteClass;
        }
    }
}
