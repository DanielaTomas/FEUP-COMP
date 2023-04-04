package pt.up.fe.comp2023;

import org.specs.comp.ollir.*;

import java.util.HashMap;
import java.util.Map;

import static org.specs.comp.ollir.OperationType.*;

public class JasminGenerator {
    private ClassUnit classUnit;
    private int CounterStack;
    private int CounterMax;
    private int ConditionInt;

    public JasminGenerator(ClassUnit classUnit) {
        this.classUnit = classUnit;
    }

    public String dealWithClass() {
        StringBuilder stringBuilder = new StringBuilder("");

        // class declaration
        stringBuilder.append(".class ").append(classUnit.getClassName()).append("\n");

        // extends declaration
        if (classUnit.getSuperClass() != null) {
            stringBuilder.append(".super ").append(classUnit.getSuperClass()).append("\n");
        } else {
            stringBuilder.append(".super java/lang/Object\n");
        }

        // fields declaration
        for (Field f : classUnit.getFields()) {
            stringBuilder.append(".field '").append(f.getFieldName()).append("' ").append(this.convertType(f.getFieldType())).append("\n");
        }

        for (Method method : classUnit.getMethods()) {
            this.CounterStack = 0;
            this.CounterMax = 0;

            stringBuilder.append(this.dealWithMethodHeader(method));
            String instructions = this.dealtWithMethodIntructions(method);
            if (!method.isConstructMethod()) {
                stringBuilder.append(this.dealWithMethodLimits(method));
                stringBuilder.append(instructions);
            }
        }

        return stringBuilder.toString();
    }

    private String dealWithMethodHeader(Method method) {
        if (method.isConstructMethod()) {
            String classSuper = "java/lang/Object";
            if (classUnit.getSuperClass() != null) {
                classSuper = classUnit.getSuperClass();
            }
            return "\n.method public <init>()V\naload_0\ninvokespecial " + classSuper +  ".<init>()V\nreturn\n.end method\n";
        }
        StringBuilder stringBuilder = new StringBuilder("\n.method").append(" ").append(method.getMethodAccessModifier().name().toLowerCase()).append(" ");
        if (method.isStaticMethod()) {
            stringBuilder.append("static ");
        }
        else if (method.isFinalMethod()) {
            stringBuilder.append("final ");
        }
        // Parameters type
        stringBuilder.append(method.getMethodName()).append("(");
        for (Element element: method.getParams()) {
            stringBuilder.append(convertType(element.getType()));
        }
        // Return type
        stringBuilder.append(")").append(this.convertType(method.getReturnType())).append("\n");
        return stringBuilder.toString();
    }

    private String dealWithMethodLimits(Method method) {
        StringBuilder stringBuilder = new StringBuilder();
        int localCount = method.getVarTable().size();
        if (!method.isStaticMethod()) {
            localCount++;
        }
        stringBuilder.append(".limit locals ").append(localCount).append("\n");
        stringBuilder.append(".limit stack ").append(CounterMax).append("\n");
        return stringBuilder.toString();
    }

    private String dealtWithMethodIntructions(Method method) {
        StringBuilder BuilderOfStrings = new StringBuilder();
        method.getVarTable();
        for (Instruction instruction : method.getInstructions()) {
            BuilderOfStrings.append(dealWithInstruction(instruction, method.getVarTable(), method.getLabels()));
            if (instruction instanceof CallInstruction && ((CallInstruction) instruction).getReturnType().getTypeOfElement() != ElementType.VOID) {
                BuilderOfStrings.append("pop\n");
                this.decrementStackCounter(1);
            }
        }
        BuilderOfStrings.append("\n.end method\n");
        return BuilderOfStrings.toString();
    }

    private String dealWithInstruction(Instruction instruction, HashMap<String, Descriptor> varTable, HashMap<String, Instruction> labels) {
        StringBuilder BuilderOfStrings = new StringBuilder();
        for (Map.Entry<String, Instruction> entry : labels.entrySet()) {
            if (entry.getValue().equals(instruction)) {
                BuilderOfStrings.append(entry.getKey()).append(":\n");
            }
        }
        return switch (instruction.getInstType()) {
            case ASSIGN ->
                    BuilderOfStrings.append(dealWithAssignment((AssignInstruction) instruction, varTable)).toString();
            case NOPER ->
                    BuilderOfStrings.append(dealWithSingleOpInstruction((SingleOpInstruction) instruction, varTable)).toString();
            case BINARYOPER ->
                    BuilderOfStrings.append(dealWithBinaryOpInstruction((BinaryOpInstruction) instruction, varTable)).toString();
            case UNARYOPER -> "Deal with '!' in correct form";
            default -> "Error in Instructions";
        };
    }

    private String dealWithAssignment(AssignInstruction instruction, HashMap<String, Descriptor> varTable) {
        String BuilderOfString = "";
        Operand operand = (Operand) instruction.getDest();
        if (operand instanceof ArrayOperand) {
            ArrayOperand aoperand = (ArrayOperand) operand;

            // Load array
            BuilderOfString += String.format("aload%s\n", this.getVirtualReg(aoperand.getName(), varTable));
            this.incrementStackCounter(1);

            // Load index
            BuilderOfString += loadElement(aoperand.getIndexOperands().get(0), varTable);
        }

        BuilderOfString += dealWithInstruction(instruction.getRhs(), varTable, new HashMap<String, Instruction>());
        if(!(operand.getType().getTypeOfElement().equals(ElementType.OBJECTREF) && instruction.getRhs() instanceof CallInstruction)) { //if its a new object call does not store yet
            BuilderOfString += this.storeElement(operand, varTable);
        }

        return BuilderOfString;
    }

    private String dealWithBinaryOpInstruction(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        switch (instruction.getUnaryOperation().getOpType()) {
            case ADD:
            case SUB:
            case MUL:
            case DIV:
                return this.dealWithIntOperation(instruction, varTable);
            case LTH:
            case GTE:
            case ANDB:
            case NOTB:
                return this.dealWithBooleanOperation(instruction, varTable);
            default:
                return "Error in BinaryOpInstruction";
        }
    }

    private String dealWithBooleanOperation(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        OperationType ot = instruction.getUnaryOperation().getOpType();
        StringBuilder BuilderOfStrings = new StringBuilder();
        switch (instruction.getUnaryOperation().getOpType()) {
            case LTH, GTE -> {
                // ..., value1, value2 →
                // ...
                String leftOperand = loadElement(instruction.getLeftOperand(), varTable);
                String rightOperand = loadElement(instruction.getRightOperand(), varTable);
                BuilderOfStrings.append(leftOperand)
                        .append(rightOperand)
                        .append(this.dealWithRelationalOperation(ot, this.getTrueLabel()))
                        .append("iconst_1\n")
                        .append("goto ").append(this.getEndIfLabel()).append("\n")
                        .append(this.getTrueLabel()).append(":\n")
                        .append("iconst_0\n")
                        .append(this.getEndIfLabel()).append(":\n");
                // if_icmp decrements 2, iconst increments 1
                this.decrementStackCounter(1);
            }
            case ANDB -> {
                // ..., value →
                // ...
                String ifeq = "ifeq " + this.getTrueLabel() + "\n";
                // Compare left operand
                BuilderOfStrings.append(loadElement(instruction.getLeftOperand(), varTable)).append(ifeq);
                this.decrementStackCounter(1);
                // Compare right operand
                BuilderOfStrings.append(loadElement(instruction.getRightOperand(), varTable)).append(ifeq);
                this.decrementStackCounter(1);
                BuilderOfStrings.append("iconst_1\n")
                        .append("goto ").append(this.getEndIfLabel()).append("\n")
                        .append(this.getTrueLabel()).append(":\n")
                        .append("iconst_0\n")
                        .append(this.getEndIfLabel()).append(":\n");
                // iconst
                this.incrementStackCounter(1);
            }
            case NOTB -> {
                String operand = loadElement(instruction.getLeftOperand(), varTable);
                BuilderOfStrings.append(operand)
                        .append("ifne ").append(this.getTrueLabel()).append("\n")
                        .append("iconst_1\n")
                        .append("goto ").append(this.getEndIfLabel()).append("\n")
                        .append(this.getTrueLabel()).append(":\n")
                        .append("iconst_0\n")
                        .append(this.getEndIfLabel()).append(":\n");
                // No need to change stack, load increments 1, ifne would dec.1 and iconst would inc.1
            }
            default -> {
                return "Error in BooleansOperations\n";
            }
        }
        this.ConditionInt++;
        return BuilderOfStrings.toString();
    }

    private String dealWithRelationalOperation(OperationType ot, String trueLabel) {
        return switch (ot) {
            case LTH -> String.format("if_icmpge %s\n", trueLabel);
            case GTE -> String.format("if_icmplt %s\n", trueLabel);
            default -> "Error in RelationalOperations\n";
        };
    }

    private String dealWithIntOperation(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        String leftOperand = loadElement(instruction.getLeftOperand(), varTable);
        String rightOperand = loadElement(instruction.getRightOperand(), varTable);
        String operation;
        switch (instruction.getUnaryOperation().getOpType()) {
            // ..., value1, value2 →
            // ..., result
            case ADD -> operation = "iadd\n";
            case SUB -> operation = "isub\n";
            case MUL -> operation = "imul\n";
            case DIV -> operation = "idiv\n";
            default -> {
                return "Error in IntOperation\n";
            }
        }
        this.decrementStackCounter(1);
        return leftOperand + rightOperand + operation;
    }


    private String dealWithSingleOpInstruction(SingleOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        return loadElement(instruction.getSingleOperand(), varTable);
    }

    private String loadElement(Element element, HashMap<String, Descriptor> varTable) {
        if (element instanceof LiteralElement) {
            String num = ((LiteralElement) element).getLiteral();
            this.incrementStackCounter(1);
            return this.selectConstType(num) + "\n";
        }
        else if (element instanceof ArrayOperand) {
            ArrayOperand OperandofArray = (ArrayOperand) element;
            // Load array
            String stringBuilder = String.format("aload%s\n", this.getVirtualReg(OperandofArray.getName(), varTable));
            this.incrementStackCounter(1);
            // Load index
            stringBuilder += loadElement(OperandofArray.getIndexOperands().get(0), varTable);
            // ..., arrayref, index →
            // ..., value
            this.decrementStackCounter(1);
            return stringBuilder + "iaload\n";
        }
        else if (element instanceof Operand) {
            Operand OperandofArray = (Operand) element;
            switch (OperandofArray.getType().getTypeOfElement()) {
                case THIS -> {
                    this.incrementStackCounter(1);
                    return "aload_0\n";
                }
                case INT32, BOOLEAN -> {
                    this.incrementStackCounter(1);
                    return String.format("iload%s\n", this.getVirtualReg(OperandofArray.getName(), varTable));
                }
                case OBJECTREF, ARRAYREF -> {
                    this.incrementStackCounter(1);
                    return String.format("aload%s\n", this.getVirtualReg(OperandofArray.getName(), varTable));
                }
                case CLASS -> { //TODO deal with class
                    return "";
                }
                default -> {
                    return "Error in operand loadElements\n";
                }
            }
        }
        System.out.println(element);
        return "Error in loadElements\n";
    }

    private String storeElement(Operand operand, HashMap<String, Descriptor> varTable) {
        if (operand instanceof ArrayOperand) {
            // ..., arrayref, index, value →
            this.decrementStackCounter(3);
            return "iastore\n";
        }
        switch (operand.getType().getTypeOfElement()) {
            case INT32:
            case BOOLEAN: {
                // ..., value →
                this.decrementStackCounter(1);
                return String.format("istore%s\n", this.getVirtualReg(operand.getName(), varTable));
            }
            case OBJECTREF:
            case ARRAYREF: {
                // ..., objectref →
                this.decrementStackCounter(1);
                return String.format("astore%s\n", this.getVirtualReg(operand.getName(), varTable));
            }
            default:
                return "Error in storeElements";
        }
    }

    private String getVirtualReg(String varName, HashMap<String, Descriptor> varTable) {
        int virtualReg = varTable.get(varName).getVirtualReg();
        if (virtualReg > 3) {
            return " " + virtualReg;
        }
        return "_" + virtualReg;
    }

    private String convertType(Type fieldType) {
        ElementType elementType = fieldType.getTypeOfElement();
        String stringBuilder = "";

        if (elementType == ElementType.ARRAYREF) {
            elementType = ((ArrayType) fieldType).getTypeOfElements();
            stringBuilder += "[";
        }

        switch (elementType) {
            case INT32:
                return stringBuilder + "I";
            case BOOLEAN:
                return stringBuilder + "Z";
            case STRING:
                return stringBuilder + "Ljava/lang/String;";
            case OBJECTREF:
                String className = ((ClassType) fieldType).getName();
                return stringBuilder + "L" + this.getOjectClassName(className) + ";";
            case CLASS:
                return "CLASS";
            case VOID:
                return "V";
            default:
                return "Error converting ElementType";
        }
    }

    private String getTrueLabel() {
        return "myTrue" + this.ConditionInt;
    }

    private String getEndIfLabel() {
        return "myEndIf" + this.ConditionInt;
    }

    private String getOjectClassName(String className) {
        for (String _import : classUnit.getImports()) {
            if (_import.endsWith("." + className)) {
                return _import.replaceAll("\\.", "/");
            }
        }
        return className;
    }

    private String selectConstType(String literal){
        return Integer.parseInt(literal) < -1 || Integer.parseInt(literal) > 5 ?
                Integer.parseInt(literal) < -128 || Integer.parseInt(literal) > 127 ?
                        Integer.parseInt(literal) < -32768 || Integer.parseInt(literal) > 32767 ?
                                "ldc " + literal :
                                "sipush " + literal :
                        "bipush " + literal :
                "iconst_" + literal;
    }

    private void decrementStackCounter(int i) {
        this.CounterStack -= i;
    }

    private void incrementStackCounter(int i) {
        this.CounterStack += i;
        if (this.CounterStack > this.CounterMax) {
            this.CounterMax = CounterStack;
        }
    }
}
