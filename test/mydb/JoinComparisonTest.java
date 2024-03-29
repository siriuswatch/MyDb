package mydb;

import mydb.Operation.Join.Comparison;
import mydb.systemtest.MyDbTestBase;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import junit.framework.JUnit4TestAdapter;

public class JoinComparisonTest extends MyDbTestBase {

  /**
   * Unit test for JoinPredicate.filter()
   */
  @Test public void filterVaryingVals() {
    int[] vals = new int[] { -1, 0, 1 };

    for (int i : vals) {
      JoinCompare p = new JoinCompare(0,
          Comparison.Operation.EQUALS, 0);
      assertFalse(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i - 1)));
      assertTrue(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i)));
      assertFalse(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i + 1)));
    }

    for (int i : vals) {
      JoinCompare p = new JoinCompare(0,
          Comparison.Operation.GREATER_THAN, 0);
      assertTrue(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i - 1)));
      assertFalse(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i)));
      assertFalse(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i + 1)));
    }

    for (int i : vals) {
      JoinCompare p = new JoinCompare(0,
          Comparison.Operation.GREATER_THAN_OR_EQ, 0);
      assertTrue(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i - 1)));
      assertTrue(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i)));
      assertFalse(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i + 1)));
    }

    for (int i : vals) {
      JoinCompare p = new JoinCompare(0,
          Comparison.Operation.LESS_THAN, 0);
      assertFalse(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i - 1)));
      assertFalse(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i)));
      assertTrue(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i + 1)));
    }

    for (int i : vals) {
      JoinCompare p = new JoinCompare(0,
          Comparison.Operation.LESS_THAN_OR_EQ, 0);
      assertFalse(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i - 1)));
      assertTrue(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i)));
      assertTrue(p.filter(Utility.getHeapTuple(i), Utility.getHeapTuple(i + 1)));
    }
  }

  /**
   * JUnit suite target
   */
  public static junit.framework.Test suite() {
    return new JUnit4TestAdapter(JoinComparisonTest.class);
  }
}

