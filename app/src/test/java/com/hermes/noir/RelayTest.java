package com.hermes.noir;
import org.junit.Test;
import static org.junit.Assert.*;
public class RelayTest {
    @Test public void parsesCommonInputs(){assertEquals(3,Relay.parse("3"));assertEquals(1,Relay.parse(" 1 "));assertEquals(0,Relay.parse(""));assertEquals(0,Relay.parse(null));assertEquals(0,Relay.parse("abc"));assertEquals(0,Relay.parse("3.5"));}
    @Test public void clampsToSafeBounds(){assertEquals(0,Relay.parse("-2"));assertEquals(10,Relay.parse("99"));assertEquals(10,Relay.clamp(50));assertEquals(0,Relay.clamp(-5));assertEquals(7,Relay.clamp(7));}
}
