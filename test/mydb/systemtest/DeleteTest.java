package mydb.systemtest;

import mydb.*;
import mydb.Exception.DBException;
import mydb.Exception.TransactionAbortedException;
import mydb.Operation.Join.Comparison;
import mydb.Operation.Join.Filter;
import mydb.TupleDetail.Tuple;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;

public class DeleteTest extends mydb.systemtest.FilterBase {
    ArrayList<ArrayList<Integer>> expectedTuples = null;

    @Override
    protected int applyPredicate(HeapFile table, TransactionId tid, Comparison comparison)
            throws DBException, TransactionAbortedException, IOException {
        SeqScan ss = new SeqScan(tid, table.getId(), "");
        Filter filter = new Filter(comparison, ss);
        Delete deleteOperator = new Delete(tid, filter);
//        Query q = new Query(deleteOperator, tid);

//        q.start();
        deleteOperator.open();
        boolean hasResult = false;
        int result = -1;
        while (deleteOperator.hasNext()) {
            Tuple t = deleteOperator.next();
            assertFalse(hasResult);
            hasResult = true;
            assertEquals(SystemTestUtil.SINGLE_INT_DESCRIPTOR, t.getTupleDetail());
            result = ((IntField) t.getField(0)).getValue();
        }
        assertTrue(hasResult);

        deleteOperator.close();

        // As part of the same transaction, scan the table
        if (result == 0) {
            // Deleted zero tuples: all tuples still in table
            expectedTuples = createdTuples;
        } else {
            assert result == createdTuples.size();
            expectedTuples = new ArrayList<ArrayList<Integer>>();
        }
        SystemTestUtil.matchTuples(table, tid, expectedTuples);
        return result;
    }

    @Override
    protected void validateAfter(HeapFile table)
            throws DBException, TransactionAbortedException, IOException {
        // As part of a different transaction, scan the table
        SystemTestUtil.matchTuples(table, expectedTuples);
    }

    /** Make test compatible with older version of ant. */
    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(DeleteTest.class);
    }
}
