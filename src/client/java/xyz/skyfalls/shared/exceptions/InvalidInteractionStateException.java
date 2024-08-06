package xyz.skyfalls.shared.exceptions;

import lombok.Getter;
import xyz.skyfalls.shared.InteractionManager;

public class InvalidInteractionStateException extends Exception{
    @Getter
    private final InteractionManager.State state;

    public InvalidInteractionStateException(InteractionManager.State state){
        this.state = state;
    }
}
