package pt.up.fe.comp2023.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OllirVisitor extends AJmmVisitor<List<Object>, List<Object>> {
    private final SymbolTable table;
    private JmmMethod currentMethod;
    private final List<Report> reports;
    private String scope;
    private final Set<JmmNode> visited = new HashSet<>();

    private int temp_sequence = 1;

    private int if_sequence = 1;

    private int while_sequence = 1;

    public OllirVisitor(SymbolTable table,List<Report> reports){
        this.table = table;
        this.reports = reports;
    }

    @Override
    protected void buildVisitor() {
        this.addVisit("Program",this::dealWithProgram);

        this.addVisit("ClassDeclaration",this::dealWithClass);
        this.addVisit("MethodDeclaration", this::dealWithMethodDeclaration);
        this.addVisit("ReturnDeclaration", this::dealWithReturn);

        this.addVisit("ExprStmt",this::dealWithExpression);

        this.addVisit("VarDeclaration", this::dealWithVarDeclaration);
        this.addVisit("Identifier", this::dealWithVariable);
        this.addVisit("This", this::dealWithThis);
        this.addVisit("Assignment", this::dealWithAssignment);
        this.addVisit("ArrayAssignment", this::dealWithAssignment);
        this.addVisit("Integer", this::dealWithType);
        this.addVisit("Boolean", this::dealWithType);
        this.addVisit("GeneralDeclaration", this::dealWithObjectInit);
        this.addVisit("IntArrayDeclaration",this::dealWithArrayDeclaration);

        this.addVisit("BinaryOp", this::dealWithBinaryOperation);
        this.addVisit("MethodCall", this::dealWithMethodCall);
        this.addVisit("LengthOp",this::dealWithMethodCall);
        this.addVisit("IfElseStmt", this::dealWithIfStatement);
        this.addVisit("WhileStmt",this::dealWithWhileStatement);

        this.addVisit("ArrayAccess", this::dealWithMethodCall);
        this.addVisit("Parenthesis", this::dealWithParenthesis);

        this.addVisit("UnaryOp",this::dealWithUnaryOperation);



        setDefaultVisit(this::defaultVisit);
    }

    private List<Object> defaultVisit(JmmNode node, List<Object> data) {
        StringBuilder ollir = new StringBuilder(node.getKind());
        ollir.append(" DEFAULT_VISIT 1");
        return Collections.singletonList(ollir.toString());
    }

    private List<Object> dealWithUnaryOperation(JmmNode node,List<Object> data){
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 20");
        visited.add(node);

        StringBuilder ollir = new StringBuilder();
        List<Object> visitResult  = visit(node.getChildren().get(0), Collections.singletonList("UNARY"));
        String result = (String) visitResult.get(0);



        String[] resultParts = result.split("\n");

        if(node.getJmmChild(0).getKind().equals("Parenthesis")){
            String temp = "temporary" + temp_sequence++ + ".bool";
            ollir.append(String.format("%s :=.bool %s;\n", temp, resultParts[0]));
            resultParts[0]  = temp;
        }

        String temp = "temporary" + temp_sequence++ + ".bool";//TODO: PROBLEM IS SOMEWHERE AROUND HERE

        for(int i = 0; i < resultParts.length; i++){
            if (resultParts[i].contains(":=")) ollir.append(resultParts[i]).append("\n");
        }

        ollir.append(String.format("%s :=.bool !.bool %s;\n", temp, resultParts[resultParts.length - 1]));

        ollir.append(temp);


        if(data.get(0).equals("BINARY")){//TODO: DEAL WITH THIS CASE b = (a && !a);
            Arrays.asList("Test");
        }
        return Arrays.asList(ollir.toString(),temp);
    }

    private List<Object> dealWithParenthesis(JmmNode node,List<Object> data){
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 20");
        visited.add(node);

        StringBuilder ollir = new StringBuilder();
        List<Object> visitResult = visit(node.getChildren().get(0), Collections.singletonList("Parenthesis"));
        String ollirChild = (String) visitResult.get(0);


        ollir.append(ollirChild);

        if( visitResult.size() > 1) return Arrays.asList(ollir.toString(),visitResult.get(1));

        return Collections.singletonList(ollir.toString());
    }

    private List<Object> dealWithProgram(JmmNode node,List<Object> data){
        StringBuilder ollir = new StringBuilder();
        for (JmmNode child : node.getChildren()){
            if (child.getKind().equals("ImportDeclaration") ) continue;
            String ollirChild = (String) visit(child, Collections.singletonList("PROGRAM")).get(0);
            ollir.append(ollirChild);
        }
        return Collections.singletonList(ollir.toString());
    }

    private List<Object> dealWithClass(JmmNode node,List<Object> data){
        scope = "CLASS";


        List<String> fields = new ArrayList<>();
        List<String> classBody = new ArrayList<>();

        StringBuilder ollir = new StringBuilder();

        for (String importStmt : this.table.getImports()){
            ollir.append(String.format("import %s;\n",importStmt));
        }
        ollir.append("\n");

        for(JmmNode child : node.getChildren()){
            String ollirChild = (String) visit(child, Collections.singletonList("CLASS")).get(0);

            if (ollirChild != null && !ollirChild.equals("DEFAULT_VISIT")) {
                if (child.getKind().equals("VarDeclaration")) {
                    fields.add(ollirChild);
                } else {
                    classBody.add(ollirChild);
                }
            }
        }

        ollir.append(OllirTemplates.classTemplate(table.getClassName(), table.getSuper()));

        ollir.append(String.join("\n", fields)).append("\n\n");
        ollir.append(OllirTemplates.constructor(table.getClassName())).append("\n\n");
        ollir.append(String.join("\n\n", classBody));

        ollir.append(OllirTemplates.closeBrackets());

        return Collections.singletonList(ollir.toString());
    }


    private List<Object> dealWithMethodDeclaration(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 2");
        visited.add(node);

        scope = "METHOD";

        try {
            if (node.getKind().equals("MainMethod")){
                currentMethod = table.getMethod("main");
            }else{
                currentMethod = table.getMethod(node.getJmmChild(0).get("name"));//method attributes stored in first child
            }


        } catch (Exception e) {
            currentMethod = null;
            e.printStackTrace();
        }

        StringBuilder builder;

        if (node.getKind().equals("MainMethod"))
            builder = new StringBuilder(OllirTemplates.method(
                    "main",
                    currentMethod.parametersToOllir(),
                    OllirTemplates.type(currentMethod.getReturnType()),
                    true));
        else
            builder = new StringBuilder(OllirTemplates.method(
                    currentMethod.getName(),
                    currentMethod.parametersToOllir(),
                    OllirTemplates.type(currentMethod.getReturnType())));


        List<String> body = new ArrayList<>();

        for (JmmNode child : node.getChildren()) {
            if (child.getKind().equals("Type")) continue;
            String ollirChild = (String) visit(child, Collections.singletonList("METHOD")).get(0);
            if (ollirChild != null && !ollirChild.equals("DEFAULT_VISIT"))
                if (ollirChild.equals("")) continue;
            body.add(ollirChild);
        }

        builder.append(String.join("\n", body));

        if (node.getKind().equals("MainMethod")) builder.append("\n").append(OllirTemplates.ret(currentMethod.getReturnType(),""));

        builder.append(OllirTemplates.closeBrackets());

        return Collections.singletonList(builder.toString());
    }

    private List<Object> dealWithVarDeclaration(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 3");
        visited.add(node);

        if ("CLASS".equals(data.get(0))) {
            Map.Entry<Symbol, Boolean> variable = table.getField(node.getJmmChild(0).get("name"));
            return Arrays.asList(OllirTemplates.field(variable.getKey()));
        }

        return Arrays.asList("");
    }

    private List<Object> dealWithAssignment(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 5");
        visited.add(node);

        Map.Entry<Symbol, Boolean> variable;
        boolean classField = false;

        if ((variable = currentMethod.getField(node.get("name"))) == null) {
            variable = table.getField(node.get("name"));
            classField = true;
        }
        String name = !classField ? currentMethod.isParameter(variable.getKey()) : null;

        String ollirVariable;
        String ollirType;

        StringBuilder ollir = new StringBuilder();

        ollirVariable = OllirTemplates.variable(variable.getKey(), name);
        ollirType = OllirTemplates.type(variable.getKey().getType());


        List<Object> visitResult;


        // ARRAY ACCESS
        if ( node.getKind().equals("ArrayAssignment")) {
            JmmNode indexNode = node.getChildren().get(0);
            Map.Entry<Symbol, Boolean> indexVariable;
            boolean indexClassField = false;

            if ((indexVariable = currentMethod.getField(node.get("name"))) == null) {
                indexVariable = table.getField(node.get("name"));
                indexClassField = true;
            }
            String indexName = !classField ? currentMethod.isParameter(indexVariable.getKey()) : null;
            List<Object> indexVisitResult = visit(indexNode, Arrays.asList(indexClassField ? "FIELD" : "ASSIGNMENT", indexVariable.getKey(), "ARRAY_ACCESS"));

            String indexResult = (String) indexVisitResult.get(0);

            visitResult = visit(node.getChildren().get(1), Arrays.asList(classField ? "FIELD" : "ASSIGNMENT", variable.getKey(), "ARRAY_ACCESS"));
            String target = (String) visitResult.get(0);



            if (!classField) {

                String temp = "temporary" + temp_sequence++ + ".i32";
                ollir.append(String.format("%s :=.i32 %s;\n", temp, indexVisitResult.get(0)));

                ollir.append(String.format("%s :=%s %s;\n",
                        OllirTemplates.arrayaccess(new Symbol(new Type("int", true),node.get("name")),name,temp),
                        OllirTemplates.type(new Type(variable.getKey().getType().getName(), false)),
                        target));

                /*ollir.append(String.format("%s :=%s %s;\n",
                        OllirTemplates.arrayaccess(new Symbol(new Type("int", true), temp), null, indexResult),
                        OllirTemplates.type(new Type(variable.getKey().getType().getName(), false)),
                        target));*/
            } else {
                String temp = "temporary" + temp_sequence++;

                ollir.append(String.format("%s :=%s %s;\n", temp + ollirType, ollirType, OllirTemplates.getfield(variable.getKey())));

                ollir.append(String.format("%s :=%s %s;\n",
                        OllirTemplates.arrayaccess(new Symbol(new Type("int", true), temp), null, indexResult),
                        OllirTemplates.type(new Type(variable.getKey().getType().getName(), false)),
                        indexResult));
            }

        } else {
            visitResult = visit(node.getChildren().get(0), Arrays.asList(classField ? "FIELD" : "ASSIGNMENT", variable.getKey(), "SIMPLE"));

            String result = (String) visitResult.get(0);
            String[] parts = result.split("\n");

            if (parts.length > 1) {
                for (int i = 0; i < parts.length - 1; i++) {
                    ollir.append(parts[i]).append("\n");
                }
                if (!classField) {
                    ollir.append(String.format("%s :=%s %s;", ollirVariable, ollirType, parts[parts.length - 1]));
                } else {
                    if (visitResult.size() > 1 && (visitResult.get(1).equals("ARRAY_INIT") || visitResult.get(1).equals("OBJECT_INIT"))) {
                        String temp = "temporary" + temp_sequence++ + ollirType;
                        ollir.append(String.format("%s :=%s %s;\n", temp, ollirType, parts[parts.length - 1]));
                        ollir.append(OllirTemplates.putfield(ollirVariable, temp));
                    } else {
                        ollir.append(OllirTemplates.putfield(ollirVariable, parts[parts.length - 1]));
                    }
                    ollir.append(";");
                }
            } else {
                if (!classField) {
                    ollir.append(String.format("%s :=%s %s;", ollirVariable, ollirType, result));
                } else {
                    if (visitResult.size() > 1 && (visitResult.get(1).equals("ARRAY_INIT") || visitResult.get(1).equals("OBJECT_INIT"))) {
                        String temp = "temporary" + temp_sequence++ + ollirType;
                        ollir.append(String.format("%s :=%s %s;\n", temp, ollirType, result));
                        ollir.append(OllirTemplates.putfield(ollirVariable, temp));
                    } else {
                        ollir.append(OllirTemplates.putfield(ollirVariable, result));
                    }
                    ollir.append(";");
                }
            }
        }


        if (visitResult.size() > 1 && visitResult.get(1).equals("OBJECT_INIT")) {
            ollir.append("\n").append(OllirTemplates.objectinstance(variable.getKey()));
        }


        return Collections.singletonList(ollir.toString());
    }

    private List<Object> dealWithObjectInit(JmmNode node, List<Object> data){
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 6");
        visited.add(node);

        String toReturn;
        if(data.get(0).equals("RETURN") || data.get(0).equals("PARAM")){
            StringBuilder builder = new StringBuilder();
            Type type = new Type(node.get("name"), false);
            Symbol auxiliary = new Symbol(type, "temporary" + temp_sequence++);
            builder.append(String.format("%s :=%s %s;\n", OllirTemplates.variable(auxiliary), OllirTemplates.type(type), OllirTemplates.objectinit(type.getName())));
            builder.append(OllirTemplates.objectinstance(auxiliary)).append("\n");
            builder.append(auxiliary.getName()).append(".").append(type.getName()).append("\n");

            toReturn = builder.toString();
        }else{
            toReturn = OllirTemplates.objectinit(node.get("name"));
        }



        if (data.get(0).equals("METHOD")) {
            toReturn += ";";
        }
        return Arrays.asList(toReturn, "OBJECT_INIT", node.get("name"));

    }

    private List<Object> dealWithType(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 20");
        visited.add(node);

        String value;
        String type;

        if (node.getKind().equals("Integer")){
            value = node.get("value") + ".i32";
            type = ".i32";
        }else if(node.getKind().equals("Boolean")){
            value = (node.get("value").equals("true") ? "1" : "0") + ".bool";
            type = ".bool";
        }else{
            value = "";
            type = "";
        }


        if (data.get(0).equals("RETURN")) {
            String temp = "temporary" + temp_sequence++ + type;
            value = String.format("%s :=%s %s;\n%s", temp, type, value, temp);
        } else if (data.get(0).equals("CONDITION") && type.equals(".bool")) {
            value = String.format("%s ==.bool 1.bool\n", value);
        }

        return Collections.singletonList(value);
    }


    private List<Object> dealWithBinaryOperation(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 7");
        visited.add(node);

        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        String leftReturn = (String) visit(left, Collections.singletonList("BINARY")).get(0);
        String rightReturn = (String) visit(right, Collections.singletonList("BINARY")).get(0);

        String[] leftStmts = leftReturn.split("\n");
        String[] rightStmts = rightReturn.split("\n");

        StringBuilder ollir = new StringBuilder();

        String leftSide;
        String rightSide;

        Type opType;

        if( node.get("op").equals("&&")  || node.get("op").equals("<") ){
            opType = new Type("boolean", false);
        }else{
            opType = new Type("int", false);
        }

        leftSide = binaryOperations(leftStmts, ollir, opType);
        rightSide = binaryOperations(rightStmts, ollir, opType);


        if (data == null) {
            return Arrays.asList("DEFAULT_VISIT 8");
        }
        if (data.get(0).equals("RETURN") || data.get(0).equals("FIELD")) {
            Symbol variable = new Symbol( opType, "temporary" + temp_sequence++);
            ollir.append(String.format("%s :=%s %s %s%s %s;\n", OllirTemplates.variable(variable),OllirTemplates.type(opType) , leftSide, node.get("op"), OllirTemplates.type(opType), rightSide));
            ollir.append(OllirTemplates.variable(variable));

        } else {

            ollir.append(String.format("%s %s%s %s", leftSide, node.get("op"),OllirTemplates.type(opType) , rightSide));

        }

        return Arrays.asList(ollir.toString(),opType);
    }


    private List<Object> dealWithVariable(JmmNode node, List<Object> data) {
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 9");
        visited.add(node);

        Map.Entry<Symbol, Boolean> field = null;


        boolean classField = false;
        if (scope.equals("CLASS")) {
            classField = true;
            field = table.getField(node.get("name"));
        } else if (scope.equals("METHOD") && currentMethod != null) {
            field = currentMethod.getField(node.get("value"));
            if (field == null) {
                classField = true;
                field = table.getField(node.get("value"));
            }
        }

        StringBuilder superiorOllir = null;
        if (data.get(0).equals("ACCESS")) {
            superiorOllir = (StringBuilder) data.get(1);
        }

        if (field != null) {
            String name =null; //TODO: this line seems to cause issues for... reasons currentMethod.isParameter(field.getKey());
            if (classField && !scope.equals("CLASS")) {
                StringBuilder ollir = new StringBuilder();
                Symbol variable = new Symbol(field.getKey().getType(), "temporary" + temp_sequence++);
                if (data.get(0).equals("CONDITION")) {
                    ollir.append(String.format("%s :=%s %s;\n",
                            OllirTemplates.variable(variable),
                            OllirTemplates.type(variable.getType()),
                            OllirTemplates.getfield(field.getKey())));
                    ollir.append(String.format("%s ==.bool 1.bool", OllirTemplates.variable(variable)));
                    return Arrays.asList(ollir.toString(), variable, name);
                } else {
                    Objects.requireNonNullElse(superiorOllir, ollir).append(String.format("%s :=%s %s;\n",
                            OllirTemplates.variable(variable),
                            OllirTemplates.type(variable.getType()),
                            OllirTemplates.getfield(field.getKey())));

                    ollir.append(OllirTemplates.variable(variable));
                    return Arrays.asList(ollir.toString(), variable, name);
                }
            } else {
                if (data.get(0).equals("CONDITION")) {
                    return Arrays.asList(String.format("%s ==.bool 1.bool", OllirTemplates.variable(field.getKey(), name)), field.getKey(), name);
                }

                return Arrays.asList(OllirTemplates.variable(field.getKey(), name), field.getKey(), name);
            }
        }
        return Arrays.asList("ACCESS", node.get("value"));
    }

    private List<Object> dealWithReturn(JmmNode node, List<Object> data) {//TODO FIX RETURN ARRAY OR NEW TEST()
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 10");
        visited.add(node);

        StringBuilder ollir = new StringBuilder();

        if(node.getNumChildren() == 1 ){
            if(node.getChildren().get(0).getKind().equals("This")){
                ollir.append(OllirTemplates.ret(currentMethod.getReturnType(), ("this." + currentMethod.getReturnType().getName() )));
                return Collections.singletonList(ollir.toString());
            }
        }

        List<Object> visit = visit(node.getChildren().get(0), Arrays.asList("RETURN"));

        String result = (String) visit.get(0);
        String[] parts = result.split("\n");
        if (parts.length > 1) {
            for (int i = 0; i < parts.length - 1; i++) {
                ollir.append(parts[i]).append("\n");
            }
            ollir.append(OllirTemplates.ret(currentMethod.getReturnType(), parts[parts.length - 1]));
        } else {
            ollir.append(OllirTemplates.ret(currentMethod.getReturnType(), result));
        }

        return Collections.singletonList(ollir.toString());
    }

    private List<Object> dealWithThis(JmmNode node, List<Object> data){
        return Arrays.asList("ACCESS", "this");
    }

    private  List<Object> dealWithExpression(JmmNode node, List<Object> data){
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 11");
        visited.add(node);

        StringBuilder ollir = new StringBuilder();
        for (JmmNode child : node.getChildren()){
            String ollirChild = (String) visit(child, Collections.singletonList("EXPR_STMT")).get(0);
            ollir.append(ollirChild);
        }
        return Collections.singletonList(ollir.toString());
    }

    private List<Object> dealWithMethodCall(JmmNode node, List<Object> data) {//TODO: fix when first child is general declaration :)))))
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 12");
        visited.add(node);


        String methodClass;
        StringBuilder ollir = new StringBuilder();
        JmmNode targetNode = node.getChildren().get(0);
        JmmNode methodNode = node;

        List<Object> targetReturn = visit(targetNode, Arrays.asList("ACCESS", ollir));
        List<JmmNode> children = node.getChildren();

        children.remove(0);//remove first node as it isnt a parameter

        Map.Entry<List<Type>, String> params = getParametersList(children, ollir);


        String methodString;
        if ( methodNode.getKind().equals("LengthOp") || methodNode.getKind().equals("ArrayAccess") ) methodString = "";
        else methodString = methodNode.get("value");

        if (params.getKey().size() > 0) {
            for (Type param : params.getKey()) {
                methodString += "::" + param.getName() + ":" + (param.isArray() ? "true" : "false");
            }
        }
        Type returnType = table.getReturnType(methodString);
        JmmMethod method;

        //ver se identifier é  ou classe do proprio ou objeto da classe proprio
        String targetName;

        if (targetNode.getKind().equals("GeneralDeclaration")){
            targetName = targetNode.get("name");
        }else if (targetNode.getKind().equals("This")){
            targetName = table.getClassName();
        }else{
            targetName = targetNode.get("value");
        }

        if (targetNode.getKind().equals("MethodCall")){
            //ollir.append(targetReturn.get(3))
            Symbol tempVar =  (Symbol) targetReturn.get(1);
            String[] resultParts = ((String) targetReturn.get(0)).split("\n");
            for(int i = 0; i < resultParts.length - 1; i++) ollir.append(resultParts[i]).append("\n");
            ollir.append(String.format("%s :=%s %s;\n", OllirTemplates.variable(tempVar ), OllirTemplates.type(tempVar.getType()), resultParts[resultParts.length - 1]));
        }

        if (methodNode.getKind().equals("LengthOp")  || methodNode.getKind().equals("ArrayAccess")) method = null;
        else method = table.getMethod(methodNode.get("value"));

        methodClass = "class_method";

        String identifierType = "";//this might not be safe
        if (currentMethod.fieldExists(targetName) || targetName.equals("This")){
            identifierType = currentMethod.getField(targetName).getKey().getType().getName();
        }

        for( var importName : table.getImports() ){
            if(targetName.equals("This")) break;
            if (!targetName.equals(importName) && !identifierType.equals(importName)) continue;
            if(targetName.equals(table.getClassName())) break;
            methodClass = "method";
        }

        if (methodNode.getKind().equals("LengthOp")) methodClass = "length";
        //###########################################################

        Symbol assignment = (data.get(0).equals("ASSIGNMENT")) ? (Symbol) data.get(1) : null;

        String ollirExpression = null;
        Type expectedType = (data.get(0).equals("BINARY") || (data.size() > 2 && data.get(2).equals("ARRAY_ACCESS"))) ? new Type("int", false) : null;


        if (targetReturn.get(0).equals("ACCESS")) {
            // Static Imported Methods
            if (!targetReturn.get(1).equals("this")) {

                String targetVariable = (String) targetReturn.get(1);
                if (assignment != null) {
                    if (data.get(2).equals("ARRAY_ACCESS")) {
                        ollirExpression = OllirTemplates.invokestatic(targetVariable, methodNode.get("value"), new Type(assignment.getType().getName(), false), params.getValue());
                        expectedType = new Type(assignment.getType().getName(), false);
                    } else {
                        ollirExpression = OllirTemplates.invokestatic(targetVariable,  methodNode.get("value"), assignment.getType(),  params.getValue());
                        expectedType = assignment.getType();
                    }
                } else {
                    if(expectedType == null){
                        JmmNode parentNode =methodNode.getJmmParent();
                        if(parentNode.getKind().equals("MethodCall") && table.methodExists(parentNode.get("value"))){

                            expectedType = table.getMethod(parentNode.get("value")).getParameters().get(methodNode.getIndexOfSelf() - 1).getType();
                        }else expectedType = new Type("void", false);
                    }
                    expectedType = (expectedType == null) ? new Type("void", false) : expectedType;
                    ollirExpression = OllirTemplates.invokestatic(targetVariable,  node.get("value"), expectedType,  params.getValue());
                }
            } else {
                // imported method called on "this"
                if (methodClass.equals("method")) {
                    if (assignment != null) {
                        ollirExpression = OllirTemplates.invokespecial( node.get("value"), assignment.getType(),  params.getValue());
                        expectedType = assignment.getType();
                    } else {
                        expectedType = (expectedType == null) ? new Type("void", false) : expectedType;
                        ollirExpression = OllirTemplates.invokespecial( node.get("value"), expectedType,  params.getValue());
                    }
                } else {
                    // Declared method called on "this
                    ollirExpression = OllirTemplates.invokevirtual(method.getName(), method.getReturnType(),  params.getValue());
                    expectedType = method.getReturnType();
                }
            }
        } else if (methodNode.getKind().equals("ArrayAccess")) {
            Symbol array = (Symbol) targetReturn.get(1);

            ollirExpression = OllirTemplates.arrayaccess(array, (String) targetReturn.get(2), params.getValue());
            expectedType = new Type(array.getType().getName(), false);
        } else {
            if (targetReturn.get(1).equals("OBJECT_INIT")) {
                Type type = new Type((String) targetReturn.get(2), false);
                Symbol auxiliary = new Symbol(type, "temporary" + temp_sequence++);
                ollir.append(String.format("%s :=%s %s;\n", OllirTemplates.variable(auxiliary), OllirTemplates.type(type), targetReturn.get(0)));
                ollir.append(OllirTemplates.objectinstance(auxiliary)).append("\n");

                if (methodClass.equals("method")) {
                    if (assignment != null) {
                        ollirExpression = OllirTemplates.invokespecial(
                                OllirTemplates.variable(auxiliary),
                                methodNode.get("value"),
                                assignment.getType(),
                                params.getValue()
                        );
                        expectedType = assignment.getType();
                    } else {
                        expectedType = (expectedType == null) ? new Type("void", false) : expectedType;
                        ollirExpression = OllirTemplates.invokespecial(
                                OllirTemplates.variable(auxiliary),
                                methodNode.get("value"),
                                expectedType,
                                params.getValue()
                        );
                    }

                } else {
                    // Declared method called on "this"
                    ollirExpression = OllirTemplates.invokevirtual(OllirTemplates.variable(auxiliary), method.getName(), method.getReturnType(), params.getValue());
                    expectedType = method.getReturnType();
                }
            } else {

                if (methodClass.equals("method")) {

                    if (assignment != null) {
                        ollirExpression = OllirTemplates.invokevirtual(OllirTemplates.variable((Symbol) targetReturn.get(1)),  methodNode.get("value"), assignment.getType(), params.getValue());
                        expectedType = assignment.getType();
                    } else {
                        expectedType = (expectedType == null) ? new Type("void", false) : expectedType;
                        ollirExpression = OllirTemplates.invokevirtual(OllirTemplates.variable((Symbol) targetReturn.get(1)), params.getValue(), expectedType,  params.getValue());
                    }
                }else if (node.getKind().equals("LengthOp")){
                    ollirExpression = OllirTemplates.arraylength(OllirTemplates.variable((Symbol) targetReturn.get(1), (String) targetReturn.get(2)));
                    expectedType = new Type("int", false);
                }
                else if(method == null) {
                    Symbol targetVariable = (Symbol) targetReturn.get(1);
                    ollirExpression = OllirTemplates.invokevirtual(OllirTemplates.variable(targetVariable), node.get("value"), targetVariable.getType(),  params.getValue());//OllirTemplates.invokevirtual(OllirTemplates.variable(targetVariable), method.getName(), method.getReturnType(), params.getValue());
                    //expectedType = assignment.getType();
                    expectedType = (expectedType == null) ? new Type("void", false) : expectedType;
                } else if (!methodClass.equals("length")) {
                    Symbol targetVariable = (Symbol) targetReturn.get(1);
                    ollirExpression = OllirTemplates.invokevirtual(OllirTemplates.variable(targetVariable), method.getName(), method.getReturnType(), params.getValue());
                    expectedType = method.getReturnType();
                }
            }
        }

        if ((data.get(0).equals("CONDITION") || data.get(0).equals("BINARY") || data.get(0).equals("FIELD") || data.get(0).equals("PARAM") || data.get(0).equals("RETURN")) && expectedType != null && ollirExpression != null) {
            Symbol auxiliary = new Symbol(expectedType, "temporary" + temp_sequence++);
            ollir.append(String.format("%s :=%s %s;\n", OllirTemplates.variable(auxiliary), OllirTemplates.type(expectedType), ollirExpression));//TODO: problema parece estar aqui

            if (data.get(0).equals("CONDITION")) {
                ollir.append(String.format("%s ==.bool 1.bool", OllirTemplates.variable(auxiliary)));
            }else if (data.get(0).equals("BINARY") || data.get(0).equals("FIELD") || data.get(0).equals("PARAM") || data.get(0).equals("RETURN")) {

                if (methodNode.getJmmParent().getKind().equals("MethodCall")){
                    //ollir.append(ollirExpression);

                } else ollir.append(String.format("%s", OllirTemplates.variable(auxiliary)));
            }
        }else {
            ollir.append(ollirExpression);
        }


        if (data.get(0).equals("EXPR_STMT")||data.get(0).equals("METHOD") || data.get(0).equals("IF") || data.get(0).equals("ELSE") || data.get(0).equals("WHILE")) {
            ollir.append(";");
        }

        if (methodNode.getJmmParent().getKind().equals("MethodCall") || methodNode.getJmmParent().getKind().equals("ArrayAccess")  && data.get(0).equals("PARAM")){
            Symbol tempVar = new Symbol(expectedType, "temporary" + temp_sequence++);
            return Arrays.asList(ollir.toString(), tempVar,tempVar.getName(),"PARAM");
        }
        return Arrays.asList(ollir.toString(), expectedType);


    }

    private List<Object> dealWithArrayDeclaration(JmmNode node, List<Object> data){
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 13");
        visited.add(node);

        StringBuilder ollir = new StringBuilder();

        String size = (String) visit(node.getChildren().get(0), Collections.singletonList("RETURN")).get(0);

        String[] sizeParts = size.split("\n");
        if (sizeParts.length > 1) {
            for (int i = 0; i < sizeParts.length - 1; i++) {
                ollir.append(sizeParts[i]).append("\n");
            }
        }

        Symbol aux = new Symbol(new Type("int", true), "temporary" + temp_sequence++);

        ollir.append(String.format("%s :=%s %s;\n", OllirTemplates.variable(aux), OllirTemplates.type(aux.getType()), OllirTemplates.arrayinit(sizeParts[sizeParts.length - 1])));
        ollir.append(OllirTemplates.variable(aux));

        return Arrays.asList(ollir.toString(), "ARRAY_INIT");
    }


    private List<Object> dealWithIfStatement(JmmNode node, List<Object> data){
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 14");
        visited.add(node);

        StringBuilder ollir = new StringBuilder();

        JmmNode ifConditionNode = node.getChildren().get(0);
        JmmNode ifCodeNode = node.getChildren().get(1);
        JmmNode elseCodeNode = node.getChildren().get(2);

        int count = if_sequence++;

        String ifCondition = (String) visit(ifConditionNode, Collections.singletonList("CONDITION")).get(0);

        String[] ifConditionParts = ifCondition.split("\n");
        if (ifConditionParts.length > 1) {
            for (int i = 0; i < ifConditionParts.length - 1; i++) {
                ollir.append(ifConditionParts[i]).append("\n");
            }
        }

        if (ifConditionParts[ifConditionParts.length - 1].contains("==.bool 1.bool")) {
            String condition = ifConditionParts[ifConditionParts.length - 1].split(" ==.bool ")[0];
            ollir.append(String.format("if (%s !.bool %s) goto else%d;\n", condition, condition, count));
        } else {
            Symbol aux = new Symbol(new Type("boolean", false), "temporary" + temp_sequence++);
            ollir.append(String.format("%s :=.bool %s;\n", OllirTemplates.variable(aux), ifConditionParts[ifConditionParts.length - 1]));
            ollir.append(String.format("if (%s !.bool %s) goto else%d;\n", OllirTemplates.variable(aux), OllirTemplates.variable(aux), count));
        }

        List<String> ifBody = new ArrayList<>();

        for (JmmNode child : ifCodeNode.getChildren()){
            ifBody.add((String) visit(child, Collections.singletonList("IF")).get(0));
        }
        ollir.append(String.join("\n", ifBody)).append("\n");
        ollir.append(String.format("goto endif%d;\n", count));

        ollir.append(String.format("else%d:\n", count));

        List<String> elseBody = new ArrayList<>();

        for (JmmNode child : elseCodeNode.getChildren()){
            elseBody.add((String) visit(child, Collections.singletonList("IF")).get(0));
        }

        ollir.append(String.join("\n", elseBody)).append("\n");

        ollir.append(String.format("endif%d:", count));

        return Collections.singletonList(ollir.toString());
    }

    private List<Object> dealWithWhileStatement(JmmNode node, List<Object> data){
        if (visited.contains(node)) return Collections.singletonList("DEFAULT_VISIT 14");
        visited.add(node);

        StringBuilder ollir = new StringBuilder();

        JmmNode ifConditionNode = node.getChildren().get(0);
        JmmNode ifCodeNode = node.getChildren().get(1);

        int count = if_sequence++;

        String ifCondition = (String) visit(ifConditionNode, Collections.singletonList("CONDITION")).get(0);

        String[] ifConditionParts = ifCondition.split("\n");
        if (ifConditionParts.length > 1) {
            for (int i = 0; i < ifConditionParts.length - 1; i++) {
                ollir.append(ifConditionParts[i]).append("\n");
            }
        }

        if (ifConditionParts[ifConditionParts.length - 1].contains("==.bool 1.bool")) {
            String condition = ifConditionParts[ifConditionParts.length - 1].split(" ==.bool ")[0];
            ollir.append(String.format("if (!.bool %s) goto endloop%d;\n", condition, condition, count));
        } else {
            Symbol aux = new Symbol(new Type("boolean", false), "temporary" + temp_sequence++);
            ollir.append(String.format("%s :=.bool %s;\n", OllirTemplates.variable(aux), ifConditionParts[ifConditionParts.length - 1]));
            ollir.append(String.format("if (!.bool %s) goto endloop%d;\n", OllirTemplates.variable(aux), count));
        }

        ollir.append(String.format("loop%d:\n", count));

        List<String> ifBody = new ArrayList<>();

        for (JmmNode child : ifCodeNode.getChildren()){
            ifBody.add((String) visit(child, Collections.singletonList("IF")).get(0));
        }
        ollir.append(String.join("\n", ifBody)).append("\n");


        if (ifConditionParts[ifConditionParts.length - 1].contains("==.bool 1.bool")) {
            String condition = ifConditionParts[ifConditionParts.length - 1].split(" ==.bool ")[0];
            ollir.append(String.format("if ( %s) goto body%d;\n", condition, condition, count));
        } else {
            Symbol aux = new Symbol(new Type("boolean", false), "temporary" + temp_sequence++);
            ollir.append(String.format("%s :=.bool %s;\n", OllirTemplates.variable(aux), ifConditionParts[ifConditionParts.length - 1]));
            ollir.append(String.format("if ( %s) goto body%d;\n", OllirTemplates.variable(aux), count));
        }

        ollir.append(String.format("endloop%d:\n", count));


        return Collections.singletonList(ollir.toString());
    }

    private Map.Entry<List<Type>, String> getParametersList(List<JmmNode> children, StringBuilder ollir) {

        List<Type> params = new ArrayList<>();
        List<String> paramsOllir = new ArrayList<>();

        for (JmmNode child : children) {
            Type type;
            String var;
            String[] varParts;
            String[] statements;
            String result;
            List<Object> visitResult;
            String tempVar;
            switch (child.getKind()) {
                case "This":
                    visitResult = visit(child, Arrays.asList("PARAM"));
                    var =  String.format("%s.%s", visitResult.get(1), table.getClassName());

                    paramsOllir.add(var);
                    break;
                case "IntArrayDeclaration":
                case "GeneralDeclaration":
                    visitResult = visit(child, Arrays.asList("PARAM"));
                    var = (String) visitResult.get(0);
                    varParts = var.split("\n");
                    tempVar = varParts[varParts.length - 1];

                    ollir.append(varParts[0]).append("\n");
                    for( int i = 1; i < varParts.length - 1; i++){
                        ollir.append(varParts[i]).append("\n");
                    }

                    paramsOllir.add(tempVar);

                    break;
                case "Parenthesis"://TODO: needs fixing for this case  a=io.teste( (2 * (1 + 2 ) ) );
                    visitResult = visit(child, Arrays.asList("PARAM"));
                    var = (String) visitResult.get(0);
                    varParts = var.split("\n");
                    tempVar = varParts[varParts.length - 1];
                    type = (Type) visitResult.get(1);

                    ollir.append(varParts[0]).append("\n");
                    for( int i = 1; i < varParts.length - 1; i++){
                        ollir.append(varParts[i]).append("\n");
                    }

                    if (tempVar.split(" ").length >1){
                        tempVar = String.format("temporary%d", temp_sequence++);
                        ollir.append( String.format("%s%s :=%s %s;", tempVar,OllirTemplates.type(type), OllirTemplates.type(type) ,varParts[varParts.length - 1]) ).append("\n");//TODO: FALTA TIPO
                    }

                    paramsOllir.add(String.format("%s%s", tempVar,OllirTemplates.type(type)));

                    break;
                case "UnaryOp":
                    visitResult = visit(child, Arrays.asList("PARAM"));
                    var = (String) visitResult.get(1);
                    tempVar = (String) visitResult.get(0);

                    statements = var.split("\n");

                    ollir.append(tempVar.split("\n")[0]).append("\n");
                    result = binaryOperations(statements, ollir, new Type("boolean", false));
                    params.add(new Type("boolean", false));



                    paramsOllir.add(result);
                    break;
                case "Integer":
                    type = new Type("int", false);
                    paramsOllir.add(String.format("%s%s", child.get("value"), OllirTemplates.type(type)));
                    params.add(type);
                    break;
                case "Boolean":
                    type = new Type("boolean", false);
                    paramsOllir.add(String.format("%s%s", child.get("value"), OllirTemplates.type(type)));
                    params.add(type);
                    break;
                case "Identifier":
                    List<Object> variable = visit(child, Arrays.asList("PARAM"));

                    statements = ((String) variable.get(0)).split("\n");
                    if (statements.length > 1) {
                        for (int i = 0; i < statements.length - 1; i++) {
                            ollir.append(statements[i]).append("\n");
                        }
                    }

                    params.add(((Symbol) variable.get(1)).getType());
                    paramsOllir.add(statements[statements.length - 1]);
                    break;
                case "BinaryOp":
                    var = (String) visit(child, Arrays.asList("PARAM")).get(0);
                    statements = var.split("\n");
                    if ( child.get("op").equals("<")  || child.get("op").equals("&&") ){
                        result = binaryOperations(statements, ollir, new Type("boolean", false));
                        params.add(new Type("boolean", false));
                    }else{
                        result = binaryOperations(statements, ollir, new Type("int", false));
                        params.add(new Type("int", false));
                    }


                    paramsOllir.add(result);
                    break;
                case "ArrayAccess":
                case "MethodCall":
                case "LengthOp":
                    List<Object> methodCallVisitResult = visit(child, Collections.singletonList("PARAM"));

                    ollir.append((String) methodCallVisitResult.get(0));
                    paramsOllir.add( methodCallVisitResult.get(2) + OllirTemplates.type( ( (Symbol) methodCallVisitResult.get(1) ).getType()));//adicionar variavel temp com o respetivo tipo a lista de param
                    params.add(( (Symbol) methodCallVisitResult.get(1) ).getType());

                    break;
                default:
                    break;
            }
        }
        return Map.entry(params, String.join(", ", paramsOllir));
    }

    private String binaryOperations(String[] statements, StringBuilder ollir, Type type) {
        String finalStmt;
        if (statements.length > 1) {
            for (int i = 0; i < statements.length - 1; i++) {
                ollir.append(statements[i]).append("\n");
            }
            String last = statements[statements.length - 1];
            if (last.split("(/|\\+|-|\\*|&&|<|!|>=)(\\.i32|\\.bool)").length == 2) {
                Pattern p = Pattern.compile("(/|\\+|-|\\*|&&|<|!|>=)(\\.i32|\\.bool)");
                Matcher m = p.matcher(last);

                m.find();

                ollir.append(String.format("%s :=%s %s;\n", OllirTemplates.variable(new Symbol(type, String.format("temporary%d", temp_sequence))), OllirTemplates.assignmentType(m.group(1)), last));
                finalStmt = OllirTemplates.variable(new Symbol(type, String.format("temporary%d", temp_sequence++)));
            } else {
                finalStmt = last;
            }
        } else {
            if (statements[0].split("(/|\\+|-|\\*|&&|<|!|>=)(\\.i32|\\.bool)").length == 2) {
                Pattern p = Pattern.compile("(/|\\+|-|\\*|&&|<|!|>=)(\\.i32|\\.bool)");
                Matcher m = p.matcher(statements[0]);
                m.find();

                ollir.append(String.format("%s :=%s %s;\n", OllirTemplates.variable(new Symbol(type, String.format("temporary%d", temp_sequence))), OllirTemplates.assignmentType(m.group(1)), statements[0]));
                finalStmt = OllirTemplates.variable(new Symbol(type, String.format("temporary%d", temp_sequence++)));
            } else {
                finalStmt = statements[0];
            }
        }
        return finalStmt;
    }
}
