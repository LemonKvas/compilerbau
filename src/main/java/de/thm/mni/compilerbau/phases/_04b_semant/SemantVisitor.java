package de.thm.mni.compilerbau.phases._04b_semant;

import de.thm.mni.compilerbau.absyn.*;
import de.thm.mni.compilerbau.absyn.visitor.DoNothingVisitor;
import de.thm.mni.compilerbau.table.*;
import de.thm.mni.compilerbau.types.ArrayType;
import de.thm.mni.compilerbau.types.PrimitiveType;
import de.thm.mni.compilerbau.types.Type;
import de.thm.mni.compilerbau.utils.SplError;

public class SemantVisitor extends DoNothingVisitor {

    private SymbolTable table;


    public SemantVisitor(SymbolTable table){
        this.table = table;

        Entry main = table.find(new Identifier("main")).orElseThrow(() -> SplError.MainIsMissing());
        if (!(main instanceof ProcedureEntry)) throw SplError.MainIsNotAProcedure();
        ProcedureEntry mainproc = (ProcedureEntry) main;
        if (mainproc.parameterTypes.size() > 0) throw SplError.MainMustNotHaveParameters();
    }

    @Override
    public void visit(Program program) {
        program.declarations.forEach(dec -> dec.accept(this));
    }

    @Override
    public void visit(ProcedureDeclaration procedureDeclaration) {
        ProcedureEntry proc =(ProcedureEntry) table.lookup(procedureDeclaration.name,SplError.UndefinedProcedure(procedureDeclaration.position,procedureDeclaration.name));
        SemantVisitor visitor = new SemantVisitor(proc.localTable);
        procedureDeclaration.body.forEach( statement -> statement.accept(visitor));
    }

    @Override
    public void visit(AssignStatement assignStatement) {
        assignStatement.value.accept(this);
        assignStatement.target.accept(this);
        if (assignStatement.value.dataType != PrimitiveType.intType)
            throw SplError.AssignmentRequiresIntegers(assignStatement.position);
        if (assignStatement.target instanceof ArrayAccess){
            if (getBaseTypeFromArrayAccess((ArrayAccess) assignStatement.target,1) == assignStatement.value.dataType) return;
        }
        if (assignStatement.target.dataType != assignStatement.value.dataType)
            throw SplError.AssignmentHasDifferentTypes(assignStatement.position);
    }

    @Override
    public void visit(IfStatement ifStatement) {
        ifStatement.condition.accept(this);
        ifStatement.thenPart.accept(this);
        ifStatement.elsePart.accept(this);
        if (ifStatement.condition.dataType != PrimitiveType.boolType)
            throw SplError.IfConditionMustBeBoolean(ifStatement.position);
    }

    @Override
    public void visit(WhileStatement whileStatement) {
        whileStatement.condition.accept(this);
        whileStatement.body.accept(this);
        if (whileStatement.condition.dataType != PrimitiveType.boolType)
            throw  SplError.WhileConditionMustBeBoolean(whileStatement.position);
    }

    @Override
    public void visit(CallStatement callStatement) {
        callStatement.arguments.forEach(arg -> arg.accept(this));
        Entry entry = table.find(callStatement.procedureName).orElseThrow(() ->SplError.UndefinedProcedure(callStatement.position,callStatement.procedureName));
        if (!(entry instanceof ProcedureEntry))
            throw SplError.CallOfNonProcedure(callStatement.position,callStatement.procedureName);
        ProcedureEntry procedureEntry = (ProcedureEntry) entry;
        if (procedureEntry.parameterTypes.size() > callStatement.arguments.size())
            throw SplError.TooFewArguments(callStatement.position,callStatement.procedureName);
        if (procedureEntry.parameterTypes.size() < callStatement.arguments.size())
            throw SplError.TooManyArguments(callStatement.position,callStatement.procedureName);
        for (int i = 0 ; i < procedureEntry.parameterTypes.size() ; i++ ){
            if (procedureEntry.parameterTypes.get(i).type != callStatement.arguments.get(i).dataType)
                throw SplError.ArgumentTypeMismatch(callStatement.position,callStatement.procedureName,i);
            if (!procedureEntry.parameterTypes.get(i).isReference){ // if argument isnt a refernece check if its array on true throw error
                if ((callStatement.arguments.get(i) instanceof VariableExpression)){
                    VariableExpression var =  (VariableExpression) callStatement.arguments.get(i);
                    if ((var.variable.dataType instanceof ArrayType))
                        throw SplError.ArgumentMustBeAVariable(callStatement.position,callStatement.procedureName,i);
                }
            }
        }
    }

    @Override
    public void visit(BinaryExpression binaryExpression) {
        binaryExpression.leftOperand.accept(this);
        binaryExpression.rightOperand.accept(this);
        if (binaryExpression.leftOperand.dataType != binaryExpression.rightOperand.dataType)
            throw SplError.OperatorDifferentTypes(binaryExpression.position);
        if (binaryExpression.leftOperand.dataType != PrimitiveType.intType) {
            if (binaryExpression.isArithmetic()) {
                throw SplError.ArithmeticOperatorNonInteger(binaryExpression.position);
            } else {
                throw SplError.ComparisonNonInteger(binaryExpression.position);
            }
        }
        binaryExpression.dataType = binaryExpression.isArithmetic() ? PrimitiveType.intType : PrimitiveType.boolType;
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        variableExpression.variable.accept(this);
        variableExpression.dataType = variableExpression.variable.dataType;
    }

    @Override
    public void visit(NamedVariable namedVariable) {
        Entry entry = table.find(namedVariable.name).orElseThrow(() -> SplError.UndefinedVariable(namedVariable.position,namedVariable.name));
        if (!(entry instanceof VariableEntry)) throw  SplError.NotAVariable(namedVariable.position,namedVariable.name);
        namedVariable.dataType = ((VariableEntry) entry).type;
    }

    @Override
    public void visit(ArrayAccess arrayAccess) {
        arrayAccess.index.accept(this);
        arrayAccess.array.accept(this);
        if (arrayAccess.index.dataType != PrimitiveType.intType) throw SplError.IndexingWithNonInteger(arrayAccess.position);
        if (!(arrayAccess.array.dataType instanceof ArrayType)) throw  SplError.IndexingNonArray(arrayAccess.position);
        arrayAccess.dataType = getBaseTypeFromArrayAccess(arrayAccess,1);
    }

    @Override
    public void visit(IntLiteral intLiteral) {
        intLiteral.dataType = PrimitiveType.intType;
    }

    @Override
    public void visit(CompoundStatement compoundStatement) {
        compoundStatement.statements.forEach(statement -> statement.accept(this));
    }

    private Type getBaseTypeFromArrayAccess(ArrayAccess arrayAccess,int level){
        Type datatype;
        if (arrayAccess.array instanceof NamedVariable){
            VariableEntry entry = (VariableEntry) table.lookup(((NamedVariable) arrayAccess.array).name);
            int depth = level;
            if (entry.type instanceof ArrayType){
                datatype = ((ArrayType) entry.type).baseType;
                depth--;
                while (depth > 0 && datatype instanceof ArrayType){
                    datatype = ((ArrayType) datatype).baseType;
                }
            } else {
                datatype = entry.type;
            }
        } else {
            datatype = getBaseTypeFromArrayAccess((ArrayAccess)arrayAccess.array, level + 1);
        }
        return datatype;
    }
}
