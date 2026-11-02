package com.tcrrry.vivo;

import org.junit.Test;
import static org.junit.Assert.*;

public class FavoriteRequestGateTest {
    @Test public void duplicateSystemRequestsCannotToggleFavoriteTwice(){
        FavoriteRequestGate gate=new FavoriteRequestGate();gate.update("a",false);
        assertTrue(gate.submit(true,100));gate.update("a",false);
        assertFalse(gate.submit(true,200));gate.update("a",true);
        assertFalse(gate.submit(true,300));assertTrue(gate.submit(false,400));
    }
    @Test public void missingStateDoesNotToggleAndSongChangeReleasesPendingRequest(){
        FavoriteRequestGate gate=new FavoriteRequestGate();gate.update("a",null);assertFalse(gate.submit(true,100));
        gate.update("a",false);assertTrue(gate.submit(true,200));
        gate.update("b",false);assertTrue(gate.submit(true,300));
    }
    @Test public void timeoutAllowsRetryAfterUnacknowledgedNativeAction(){
        FavoriteRequestGate gate=new FavoriteRequestGate();gate.update("a",false);
        assertTrue(gate.submit(true,100));assertFalse(gate.submit(true,3099));assertTrue(gate.submit(true,3100));
    }
}
