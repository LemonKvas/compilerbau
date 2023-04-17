package de.thm.mni.compilerbau.phases._05_varalloc;

import de.thm.mni.compilerbau.absyn.*;
import de.thm.mni.compilerbau.absyn.visitor.DoNothingVisitor;
import de.thm.mni.compilerbau.table.ParameterType;
import de.thm.mni.compilerbau.table.ProcedureEntry;
import de.thm.mni.compilerbau.table.SymbolTable;
import de.thm.mni.compilerbau.table.VariableEntry;
import de.thm.mni.compilerbau.types.PrimitiveType;

public class StackVisitor extends DoNothingVisitor {

    private final SymbolTable table;
    private int hasCall;

    public StackVisitor(SymbolTable table){
        this.table = table;
    }

    @Override
    public void visit(Program program) {
        program.declarations.forEach(dec -> dec.accept(this));
    }

    @Override
    public void visit(ProcedureDeclaration procedureDeclaration) {
        ProcedureEntry proc = (ProcedureEntry) table.lookup(procedureDeclaration.name);
        StackLayout sl = proc.stackLayout;
        sl.argumentAreaSize = procedureDeclaration.parameters.size() * PrimitiveType.intType.byteSize;
        sl.outgoingAreaSize = -1;
        hasCall = -1;

        procedureDeclaration.body.forEach(statement -> statement.accept(this));

        if (hasCall != -1){
            sl.outgoingAreaSize = hasCall * PrimitiveType.intType.byteSize;
        }

        int varoffset = 0;
        for (VariableDeclaration var : procedureDeclaration.variables){
            VariableEntry varEntry = (VariableEntry) proc.localTable.lookup(var.name);
            varoffset -= varEntry.type.byteSize;
            varEntry.offset = varoffset;
        }

        int procargsoffset = 0;
        for (ParameterDeclaration param : procedureDeclaration.parameters){
            VariableEntry paramEntry = (VariableEntry) proc.localTable.lookup(param.name);
            paramEntry.offset = procargsoffset;
            procargsoffset += (param.isReference) ? PrimitiveType.intType.byteSize : paramEntry.type.byteSize;
        }

        int paramoffset = 0;
        for (ParameterType param : proc.parameterTypes){
            param.offset = paramoffset;
            paramoffset += (param.isReference) ? PrimitiveType.intType.byteSize : param.type.byteSize;
        }

        sl.localVarAreaSize = Math.abs(varoffset);
    }

    @Override
    public void visit(IfStatement ifStatement) {
        ifStatement.thenPart.accept(this);
        ifStatement.elsePart.accept(this);
    }

    @Override
    public void visit(WhileStatement whileStatement) {
        whileStatement.body.accept(this);
    }

    @Override
    public void visit(CompoundStatement compoundStatement) {
        compoundStatement.statements.forEach(statement -> statement.accept(this));
    }

    @Override
    public void visit(CallStatement callStatement) {
        if (callStatement.arguments.size() > hasCall){
            hasCall = callStatement.arguments.size();
        }
    }

}
