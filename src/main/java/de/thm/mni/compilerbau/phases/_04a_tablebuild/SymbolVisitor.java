package de.thm.mni.compilerbau.phases._04a_tablebuild;

import de.thm.mni.compilerbau.absyn.*;
import de.thm.mni.compilerbau.absyn.visitor.DoNothingVisitor;
import de.thm.mni.compilerbau.table.*;
import de.thm.mni.compilerbau.types.ArrayType;
import de.thm.mni.compilerbau.utils.SplError;

import java.util.List;
import java.util.stream.Collectors;

public class SymbolVisitor extends DoNothingVisitor {

    private final SymbolTable table;
    private final boolean showTables;

    public SymbolVisitor(SymbolTable table,boolean showTables){
        this.table = table;
        this.showTables = showTables;
    }

    @Override
    public void visit(Program program) {
        program.declarations.forEach(dec -> dec.accept(this));
    }

    @Override
    public void visit(TypeDeclaration typeDeclaration) {
        typeDeclaration.typeExpression.accept(this);
        table.enter(
                typeDeclaration.name,
                new TypeEntry(typeDeclaration.typeExpression.dataType),
                SplError.RedeclarationAsType(typeDeclaration.position,typeDeclaration.name));
    }

    @Override
    public void visit(ArrayTypeExpression arrayTypeExpression) {
        arrayTypeExpression.baseType.accept(this);
        arrayTypeExpression.dataType = new ArrayType(arrayTypeExpression.baseType.dataType,arrayTypeExpression.arraySize);
    }

    @Override
    public void visit(NamedTypeExpression namedTypeExpression) {
        Entry entry = table.find(namedTypeExpression.name).orElseThrow( ()
                -> SplError.UndefinedType(namedTypeExpression.position, namedTypeExpression.name));
        if (!(entry instanceof TypeEntry)) {
           throw SplError.NotAType(namedTypeExpression.position, namedTypeExpression.name);
        }
        namedTypeExpression.dataType = ((TypeEntry) entry).type;
    }

    @Override
    public void visit(ProcedureDeclaration procedureDeclaration) {
        SymbolTable localSymbolTable = new SymbolTable(table);
        SymbolVisitor visitor = new SymbolVisitor(localSymbolTable,showTables);
        procedureDeclaration.parameters.forEach(param -> param.accept(visitor));
        procedureDeclaration.variables.forEach(var -> var.accept(visitor));
        List<ParameterType> parameterTypeList = procedureDeclaration.parameters.stream().map((paramDec)
                -> new ParameterType(paramDec.typeExpression.dataType, paramDec.isReference)).collect(Collectors.toList());
        table.enter(
                procedureDeclaration.name,
                new ProcedureEntry(localSymbolTable,parameterTypeList),
                SplError.RedeclarationAsProcedure(procedureDeclaration.position,
                        procedureDeclaration.name));
        if (showTables) TableBuilder.printSymbolTableAtEndOfProcedure(
                procedureDeclaration.name,
                (ProcedureEntry) table.lookup(procedureDeclaration.name));
    }

    @Override
    public void visit(ParameterDeclaration parameterDeclaration) {
        parameterDeclaration.typeExpression.accept(this);
        if (parameterDeclaration.typeExpression instanceof ArrayTypeExpression){
            if (!parameterDeclaration.isReference)
                //TODO ask if aa(ar : array [3] of int) or type a = array[3] of int; aa(ar : a)
                throw SplError.MustBeAReferenceParameter(parameterDeclaration.position,parameterDeclaration.name);
        }
        table.enter(parameterDeclaration.name,new VariableEntry(
                parameterDeclaration.typeExpression.dataType,
                parameterDeclaration.isReference),
                SplError.RedeclarationAsParameter(parameterDeclaration.position,parameterDeclaration.name));
    }

    @Override
    public void visit(VariableDeclaration variableDeclaration) {
        variableDeclaration.typeExpression.accept(this);
        table.enter(variableDeclaration.name, new VariableEntry(
                variableDeclaration.typeExpression.dataType,
                false),SplError.RedeclarationAsVariable(variableDeclaration.position,variableDeclaration.name));
    }

    public SymbolTable getTable() {
        return table;
    }
}
