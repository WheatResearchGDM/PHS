package br.com.phs.coleta;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class ScanGateTest {
  @Test
  public void savedTagIsIgnoredWhileItRemainsVisible() {
    ScanGate gate = new ScanGate();
    gate.afterSave("123");
    assertNull(gate.accept(List.of("123"), 0));
    assertNull(gate.accept(List.of("123"), 5000));
  }

  @Test
  public void differentTagCanBeReadImmediately() {
    ScanGate gate = new ScanGate();
    gate.afterSave("123");
    assertEquals("456", gate.accept(List.of("123", "456"), 1));
  }

  @Test
  public void tagCanBeReadAfterLeavingFrame() {
    ScanGate gate = new ScanGate();
    gate.afterSave("123");
    gate.accept(List.of(), 0);
    gate.accept(List.of(), 800);
    assertEquals("123", gate.accept(List.of("123"), 1000));
  }

  @Test
  public void transientDetectionLossDoesNotRearm() {
    ScanGate gate = new ScanGate();
    gate.afterSave("123");
    gate.accept(List.of(), 0);
    assertNull(gate.accept(List.of("123"), 200));
  }

  @Test
  public void manualRereadAllowsEditingSameTag() {
    ScanGate gate = new ScanGate();
    gate.afterSave("123");
    gate.clear();
    assertEquals("123", gate.accept(List.of("123"), 0));
  }
}
