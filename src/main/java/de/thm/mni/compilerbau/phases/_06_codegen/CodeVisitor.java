package de.thm.mni.compilerbau.phases._06_codegen;

import de.thm.mni.compilerbau.absyn.*;
import de.thm.mni.compilerbau.absyn.visitor.DoNothingVisitor;
import de.thm.mni.compilerbau.table.ProcedureEntry;
import de.thm.mni.compilerbau.table.SymbolTable;
import de.thm.mni.compilerbau.table.VariableEntry;
import de.thm.mni.compilerbau.types.ArrayType;

import java.util.Stack;

public class CodeVisitor extends DoNothingVisitor {

    private static final Register FRAMEPOINTER = new Register(25);
    private static final Register STACKPOINTER = new Register(29);
    private static final Register RETURNADDRESS = new Register(31);
    private static final Register zero = new Register(0);
    private final SymbolTable table;
    private final CodePrinter output;
    private final RegisterStack registerStack;
    private int lableNr = 0;


    public CodeVisitor(SymbolTable table, CodePrinter output){
        this.table = table;
        this.registerStack = new RegisterStack();
        this.output = output;

    }

    public CodeVisitor(SymbolTable table, CodePrinter output, RegisterStack stack){
        this.table = table;
        this.output = output;
        this.registerStack = stack;
    }

    @Override
    public void visit(Program program) {
        program.declarations.forEach(dec -> dec.accept(this));
    }

    @Override
    public void visit(ProcedureDeclaration procDec) {
        // Proc Entry
        ProcedureEntry procEntry =(ProcedureEntry) table.lookup(procDec.name);

        // Proc Frame Allocation
        output.emitExport(procDec.name.toString());
        output.emitLabel(procDec.name.toString());
        output.emitInstruction("sub",STACKPOINTER,STACKPOINTER,procEntry.stackLayout.frameSize(),"allocate frame");
        output.emitInstruction("stw",FRAMEPOINTER,STACKPOINTER,procEntry.stackLayout.oldFramePointerOffset(),"save old frame pointer");
        output.emitInstruction("add",FRAMEPOINTER,STACKPOINTER,procEntry.stackLayout.frameSize(),"setup new frame pointer");
        if (!procEntry.stackLayout.isLeafProcedure())
            output.emitInstruction("stw",RETURNADDRESS,FRAMEPOINTER,procEntry.stackLayout.oldReturnAddressOffset(),"save return register");

        // Proc body execution
        CodeVisitor cv = new CodeVisitor(procEntry.localTable,output,registerStack);
        procDec.body.forEach(statement -> statement.accept(cv));

        // Proc Frame Release
        if (!procEntry.stackLayout.isLeafProcedure())
            output.emitInstruction("ldw",RETURNADDRESS,FRAMEPOINTER,procEntry.stackLayout.oldReturnAddressOffset(),"restore return register");
        output.emitInstruction("ldw",FRAMEPOINTER,STACKPOINTER,procEntry.stackLayout.oldFramePointerOffset(),"restore old frame pointer");
        output.emitInstruction("add",STACKPOINTER,STACKPOINTER,procEntry.stackLayout.frameSize(),"release frame");
        output.emitInstruction("jr",RETURNADDRESS,"return");
    }

    @Override
    public void visit(IntLiteral intLiteral) {
        output.emitInstruction("add",registerStack.pushR(),zero,intLiteral.value);
    }

    @Override
    public void visit(ArrayAccess arrayAccess) {
        arrayAccess.array.accept(this);
        arrayAccess.index.accept(this);
        output.emitInstruction("add",registerStack.pushR(),zero,((ArrayType) arrayAccess.array.dataType).arraySize);
        Register indexOutOfBoundCheck = registerStack.popR();
        output.emitInstruction("bgeu", indexOutOfBoundCheck.previous(),indexOutOfBoundCheck,"_indexError","Array Index Check");
        Register oldAddress = registerStack.popR();
        output.emitInstruction("mul",registerStack.pushR(),oldAddress,arrayAccess.dataType.byteSize);
        Register indexByteSize = registerStack.popR(),arrayStart = registerStack.popR();
        output.emitInstruction("add",registerStack.pushR(),arrayStart,indexByteSize);
    }

    @Override
    public void visit(IfStatement ifStatement) {
        boolean noElse = ifStatement.elsePart instanceof EmptyStatement;
        ifStatement.condition.accept(this);
        BinaryExpression.Operator compareOP = ((BinaryExpression) ifStatement.condition).flipComparison();
        String lableThen = getLable();
        branch(compareOP,lableThen);

        if (noElse){
            ifStatement.thenPart.accept(this);
            output.emitLabel(lableThen);
        } else {
            String lableElse = getLable();
            ifStatement.thenPart.accept(this);
            output.emitInstruction("j",lableElse,"true statement end skip else");
            output.emitLabel(lableThen);
            ifStatement.elsePart.accept(this);
            output.emitLabel(lableElse);
        }
    }

    @Override
    public void visit(CallStatement callStatement) {
        ProcedureEntry procEntry = (ProcedureEntry) table.lookup(callStatement.procedureName);
        for (int i = 0; i < callStatement.arguments.size(); i++) {
            boolean isRef = procEntry.parameterTypes.get(i).isReference;
            if (callStatement.arguments.get(i) instanceof VariableExpression && isRef) {
                VariableExpression ve = (VariableExpression) callStatement.arguments.get(i);
                ve.variable.accept(this);
            } else {
                callStatement.arguments.get(i).accept(this);
            }
            output.emitInstruction("stw", registerStack.popR(), STACKPOINTER, i * 4, String.format("store argument #%d", i));
        }
        output.emitInstruction("jal", callStatement.procedureName.toString());
    }

    @Override
    public void visit(NamedVariable namedVariable) {
        VariableEntry var = (VariableEntry) table.lookup(namedVariable.name);
        output.emitInstruction("add",registerStack.pushR(),FRAMEPOINTER,var.offset);
        if (var.isReference) {
            Register varAddress = registerStack.popR();
            output.emitInstruction("ldw",registerStack.pushR(),varAddress,0,"load ref param");
        }
    }

    @Override
    public void visit(WhileStatement whileStatement) {
        String whilestartLable  = getLable();
        String whileskip = getLable();
        output.emitLabel(whilestartLable);
        whileStatement.condition.accept(this);
        BinaryExpression.Operator compareOP = ((BinaryExpression) whileStatement.condition).flipComparison();
        branch(compareOP,whileskip);
        whileStatement.body.accept(this);
        output.emitInstruction("j",whilestartLable);
        output.emitLabel(whileskip);
    }

    @Override
    public void visit(AssignStatement assignStatement) {
        assignStatement.target.accept(this);
        assignStatement.value.accept(this);

        output.emitInstruction("stw",registerStack.popR(),registerStack.popR(),0,"AssignStatement"); // TODO check java pop reinfolge
    }

    @Override
    public void visit(BinaryExpression binaryExpression) {
        binaryExpression.leftOperand.accept(this);
        binaryExpression.rightOperand.accept(this);
        if (binaryExpression.isArithmetic()) {
            String op = binaryExpression.operator.name().toLowerCase();
            Register right = registerStack.popR(), left = registerStack.popR();
            output.emitInstruction(op, registerStack.pushR(), left, right, String.format("binary Expression %s", op));
        }
    }

    @Override
    public void visit(CompoundStatement compoundStatement) {
        compoundStatement.statements.forEach(statement -> statement.accept(this));
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        variableExpression.variable.accept(this);
        Register variableAddress = registerStack.popR();
        output.emitInstruction("ldw",registerStack.pushR(),variableAddress,0,"load variable");
    }

    private void branch(BinaryExpression.Operator compareOP, String lable){
        String opCode = "";
        switch (compareOP){
            case EQU:
                opCode = "beq";
                break;
            case GRE:
                opCode = "bge";
                break;
            case GRT:
                opCode = "bgt";
                break;
            case LSE:
                opCode = "ble";
                break;
            case LST:
                opCode = "blt";
                break;
            case NEQ:
                opCode = "bne";
                break;
        }
        Register op2 = registerStack.popR(), op1 = registerStack.popR();
        output.emitInstruction(opCode,op1,op2,lable);
    }

    private String getLable(){
        String back = "L" + lableNr;
        lableNr++;
        return back;
    }
}
