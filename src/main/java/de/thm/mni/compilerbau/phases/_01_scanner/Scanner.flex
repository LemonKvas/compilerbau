package de.thm.mni.compilerbau.phases._01_scanner;

import de.thm.mni.compilerbau.CommandLineOptions;
import de.thm.mni.compilerbau.absyn.Position;
import de.thm.mni.compilerbau.phases._02_03_parser.Sym;
import de.thm.mni.compilerbau.table.Identifier;import de.thm.mni.compilerbau.utils.SplError;
import java_cup.runtime.*;

%%


%class Scanner
%public
%line
%column
%cup
%eofval{
    return new java_cup.runtime.Symbol(Sym.EOF, yyline + 1, yycolumn + 1);   //This needs to be specified when using a custom sym class name
%eofval}

LineTerminator = [\n | \r | \r\n]

%{
    public CommandLineOptions options = null;
  
    private Symbol symbol(int type) {
      return new Symbol(type, yyline + 1, yycolumn + 1);
    }

    private Symbol symbol(int type, Object value) {
      return new Symbol(type, yyline + 1, yycolumn + 1, value);
    }


%}

%%

    //Keywords
else  {return symbol(Sym.ELSE);}
while {return symbol(Sym.WHILE);}
ref   {return symbol(Sym.REF);}
if    {return symbol(Sym.IF);}
of    {return symbol(Sym.OF);}
type  {return symbol(Sym.TYPE);}
proc  {return symbol(Sym.PROC);}
array {return symbol(Sym.ARRAY);}
var   {return symbol(Sym.VAR);}

     //Comment
"//".*{LineTerminator}? {/* do nothing comment */}

      // INTLITs
"0x"[0-9a-fA-F]+    {return symbol(Sym.INTLIT,Integer.parseInt(yytext().substring(2),16));}
[0-9]+  {return symbol(Sym.INTLIT,Integer.parseInt(yytext()));}

[']\\n[']   {return symbol(Sym.INTLIT,10);}
['].[']   {return symbol(Sym.INTLIT,(int) yytext().charAt(1));}



    //Operanten
[<][=]  {return symbol(Sym.LE);}
[<]   {return symbol(Sym.LT);}
[>][=]  {return symbol(Sym.GE);}
[>]   {return symbol(Sym.GT);}
[+]   {return symbol(Sym.PLUS);}
[-]   {return symbol(Sym.MINUS);}
[/]   {return symbol(Sym.SLASH);}
[*]   {return symbol(Sym.STAR);}
[:][=]  {return symbol(Sym.ASGN);}
[=]   {return symbol(Sym.EQ);}
[(]   {return symbol(Sym.LPAREN);}
[)]   {return symbol(Sym.RPAREN);}
[{]   {return symbol(Sym.LCURL);}
[}]   {return symbol(Sym.RCURL);}
[#]   {return symbol(Sym.NE);}
\[    {return symbol(Sym.LBRACK);}
\]    {return symbol(Sym.RBRACK);}
[:]   {return symbol(Sym.COLON);}
[,]   {return symbol(Sym.COMMA);}
[;]   {return symbol(Sym.SEMIC);}
    // Identifier
[a-zA-Z_][a-zA-Z0-9_]* {return symbol(Sym.IDENT,new Identifier(yytext()));}

    //Unvalid charaters and whitespace
[ \t\n]  { /* Whitespace do nothing */}

' {throw SplError.IllegalApostrophe(new Position(yyline + 1, yycolumn + 1));}

{LineTerminator} {/* End of Line */}
[^]	{throw SplError.IllegalCharacter(new Position(yyline + 1, yycolumn + 1), yytext().charAt(0));}

