package com.java2umltext;

import java.util.Optional;
import java.util.stream.Collectors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import com.java2umltext.Client.Config;
import com.java2umltext.model.ClassWrapper;
import com.java2umltext.model.Document;
import com.java2umltext.model.FieldWrapper;
import com.java2umltext.model.MethodWrapper;
import com.java2umltext.model.Relationship;
import com.java2umltext.model.UML;
import com.java2umltext.model.Visibility;

public class ASTVisitor extends VoidVisitorAdapter<UML> {

    private final String packageName;
    private final Config config;

    public ASTVisitor(String packageName, Config config) {
        this.packageName = packageName;
        this.config = config;
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration cid, UML el) {
        if (!(el instanceof Document)) {
            super.visit(cid, el);
            return;
        }
        parseClassLike(cid, (Document) el);
        super.visit(cid, el);
    }

    @Override
    public void visit(EnumDeclaration ed, UML el) {
        if (!(el instanceof Document)) {
            super.visit(ed, el);
            return;
        }
        parseClassLike(ed, (Document) el);
        super.visit(ed, el);
    }

    @Override
    public void visit(RecordDeclaration rd, UML el) {
        if (!(el instanceof Document)) {
            super.visit(rd, el);
            return;
        }
        parseClassLike(rd, (Document) el);
        super.visit(rd, el);
    }

    @Override
    public void visit(EnumConstantDeclaration ecd, UML el) {
        if (!(el instanceof ClassWrapper)) {
            super.visit(ecd, el);
            return;
        }
        ClassWrapper cw = (ClassWrapper) el;
        cw.fields().add(new FieldWrapper(Visibility.PUBLIC, true, "", ecd.getNameAsString()));
    }

    @Override
    public void visit(Parameter p, UML el) {
        if (!(el instanceof ClassWrapper)) {
            super.visit(p, el);
            return;
        }
        ClassWrapper cw = (ClassWrapper) el;
        if (cw.type().equals("record")) {
            cw.fields().add(new FieldWrapper(Visibility.PUBLIC, false, p.getTypeAsString(), p.getNameAsString()));
        }
    }

    @Override
    public void visit(FieldDeclaration field, UML el) {
        if (!(el instanceof ClassWrapper)) {
            super.visit(field, el);
            return;
        }
        ClassWrapper cw = (ClassWrapper) el;
        Visibility v = getVisibility(field);
        if (config.fieldModifiers.contains(v)) {
            boolean isStatic = field.isStatic();
            String type = field.getVariables().getFirst().get().getTypeAsString();
            String name = field.getVariables().getFirst().get().getNameAsString();
            cw.fields().add(new FieldWrapper(v, isStatic, type, name));

            if (config.showFieldRelationships) {
                addRelationship("--", field.getElementType(), cw);
            }
        }
    }

    @Override
    public void visit(ConstructorDeclaration cd, UML el) {
        if (!(el instanceof ClassWrapper)) {
            super.visit(cd, el);
            return;
        }
        if (!config.showConstructors) {
            return;
        }

        ClassWrapper cw = (ClassWrapper) el;
        Visibility v = getVisibility(cd);

        if (config.methodModifiers.contains(v)) {
            MethodWrapper mw = new MethodWrapper(
                    v, cd.isStatic(), cd.isAbstract(), cw.name(), cd.getNameAsString());
            cw.methods().add(mw);

            for (Parameter parameter : cd.getParameters()) {
                mw.parameters().add(parameter.getTypeAsString());
            }
        }
    }

    @Override
    public void visit(MethodDeclaration md, UML el) {
        if (!(el instanceof ClassWrapper)) {
            super.visit(md, el);
            return;
        }

        ClassWrapper cw = (ClassWrapper) el;
        Visibility v = getVisibility(md);

        if (config.methodModifiers.contains(v)) {
            MethodWrapper mw = new MethodWrapper(
                    v, md.isStatic(), md.isAbstract(), md.getTypeAsString(), md.getNameAsString());
            cw.methods().add(mw);

            for (Parameter parameter : md.getParameters()) {
                mw.parameters().add(parameter.getTypeAsString());
            }

            if (config.showMethodRelationships) {
                addRelationship("..", md.getType(), cw);
                for (Parameter parameter : md.getParameters()) {
                    addRelationship("..", parameter.getType(), cw);
                }
            }
        }
    }

    private void parseClassLike(TypeDeclaration<?> td, Document doc) {
        String pkg = config.showPackage ? packageName : "";
        String name = td.getNameAsString();

        // 如果是类或接口，添加泛型参数
        if (td instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) td;
            if (!cid.getTypeParameters().isEmpty()) {
                name += cid.getTypeParameters().stream()
                        .map(tp -> tp.getNameAsString())
                        .collect(Collectors.joining(", ", "<", ">"));
            }
        }

        // 处理内部类路径（保持原有逻辑，但需要调整名称拼接）
        Node node = td.getParentNode().get();
        while (node instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration parentDecl = (ClassOrInterfaceDeclaration) node;
            String parentName = parentDecl.getNameAsString();

            // 处理父类的泛型参数
            if (!parentDecl.getTypeParameters().isEmpty()) {
                parentName += parentDecl.getTypeParameters().stream()
                        .map(tp -> tp.getNameAsString())
                        .collect(Collectors.joining(", ", "<", ">"));
            }

            name = parentName + "." + name;
            node = node.getParentNode().get();
            if (node instanceof CompilationUnit) {
                doc.addRelationship(new Relationship(
                        "+..",
                        ClassWrapper.pkgPrefix(pkg) + name,
                        ClassWrapper.pkgPrefix(pkg) + name.substring(0, name.lastIndexOf("."))));
            }
        }

        String type = getDeclarationType(td);
        ClassWrapper cw = doc.addClass(pkg, type, name);

        // add imports
        if (node instanceof CompilationUnit) {
            for (ImportDeclaration id : ((CompilationUnit) node).getImports()) {
                cw.imports().put(id.getName().getIdentifier(), id.getName().toString());
            }
        }

        // add inheritance & interfaces
        for (ClassOrInterfaceType cit : ((NodeWithImplements<?>) td).getImplementedTypes()) {
            addRelationship("<|..", cit, cw);
        }
        if (td instanceof ClassOrInterfaceDeclaration) {
            for (ClassOrInterfaceType cit : ((ClassOrInterfaceDeclaration) td).getExtendedTypes()) {
                addRelationship("<|--", cit, cw);
            }
        }

        // process subelements
        td.getFields().forEach(f -> f.accept(this, cw));
        td.getConstructors().forEach(c -> c.accept(this, cw));
        td.getMethods().forEach(m -> m.accept(this, cw));
        if (td instanceof EnumDeclaration) {
            ((EnumDeclaration) td).getEntries().forEach(e -> e.accept(this, cw));
        }
        if (td instanceof RecordDeclaration) {
            ((RecordDeclaration) td).getParameters().forEach(p -> p.accept(this, cw));
        }
    }

    private void addRelationship(String relationship, Type type, ClassWrapper cw) {
        String typeString = type.toString(); // 直接使用原始类型字符串
        String source = cw.pkgPrefix() + typeString;

        // 处理泛型类型参数（如将java.util.List<T>转换为List<T>）
        if (type instanceof ClassOrInterfaceType) {
            ClassOrInterfaceType cit = (ClassOrInterfaceType) type;
            typeString = cit.getNameAsString();

            // 添加泛型参数
            if (cit.getTypeArguments().isPresent()) {
                typeString += cit.getTypeArguments().get().stream()
                        .map(Type::toString)
                        .collect(Collectors.joining(", ", "<", ">"));
            }

            // 处理导入映射
            if (cw.imports().containsKey(cit.getNameAsString())) { // 注意这里使用原始名称匹配
                String fullName = cw.imports().get(cit.getNameAsString());
                source = config.showPackage ? fullName : cit.getNameAsString();

                // 如果存在泛型参数则追加
                if (cit.getTypeArguments().isPresent()) {
                    source += cit.getTypeArguments().get().stream()
                            .map(Type::toString)
                            .collect(Collectors.joining(", ", "<", ">"));
                }
            }
        }

        Relationship newRelationship = new Relationship(
                relationship,
                source,
                cw.pkgPrefix() + cw.name());
        cw.document().addRelationship(newRelationship);
    }

    private static String getDeclarationType(TypeDeclaration<?> td) {
        if (td instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) td;
            if (cid.isInterface()) {
                return "interface";
            } else {
                return (cid.getModifiers().stream()
                        .map(Modifier::toString)
                        .filter(m -> m.contains("abstract"))
                        .findAny().orElse("").trim() + " class").trim();
            }
        } else if (td instanceof EnumDeclaration) {
            return "enum";
        } else if (td instanceof RecordDeclaration) {
            return "record";
        }
        return "";
    }

    private static <T extends Node> Visibility getVisibility(NodeWithModifiers<T> node) {
        return node.getModifiers().stream()
                .map(Modifier::toString)
                .map(Visibility::fromString)
                .findFirst().orElse(Visibility.DEFAULT);
    }
}
