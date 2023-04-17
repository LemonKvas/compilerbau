package de.thm.mni.compilerbau.phases._06_codegen;

import de.thm.mni.compilerbau.utils.SplError;

public class RegisterStack {

    private int freeReg = 8;

    public Register pushR(){
        if (freeReg > 23){
            throw SplError.RegisterOverflow();
        }
        freeReg++;
        return new Register(freeReg-1);
    }

    public Register popR(){
        if (freeReg <= 8){
            throw new ArrayIndexOutOfBoundsException();
        }
        freeReg--;
        return new Register(freeReg);
    }
}
