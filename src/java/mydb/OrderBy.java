package mydb;

import mydb.Exception.DBException;
import mydb.Exception.TransactionAbortedException;
import mydb.Operation.Join.Comparison;
import mydb.TupleDetail.Tuple;
import mydb.TupleDetail.TupleDetail;

import java.io.IOException;
import java.util.*;

/**
 * OrderBy is an operator that implements a relational ORDER BY.
 */
public class OrderBy extends Operator {

    private static final long serialVersionUID = 1236724521661547763L;
    private DbIterator child;
    private TupleDetail td;
    private ArrayList<Tuple> childTups = new ArrayList<Tuple>();
    private int orderByField;
    private String orderByFieldName;
    private Iterator<Tuple> it;
    private boolean asc;

    /**
     * Creates a new OrderBy node over the tuples from the iterator.
     * 
     * @param orderbyField
     *            the field to which the sort is applied.
     * @param asc
     *            true if the sort order is ascending.
     * @param child
     *            the tuples to sort.
     */
    public OrderBy(int orderbyField, boolean asc, DbIterator child) {
        this.child = child;
        td = child.getTupleDetail();
        this.orderByField = orderbyField;
        this.orderByFieldName = td.getFieldName(orderbyField);
        this.asc = asc;
    }
    
    public boolean isASC()
    {
	return this.asc;
    }
    
    public int getOrderByField()
    {
        return this.orderByField;
    }
    
    public String getOrderFieldName()
    {
	return this.orderByFieldName;
    }
    
    public TupleDetail getTupleDetail() {
        return td;
    }

    public void open() throws DBException, NoSuchElementException,
            TransactionAbortedException, IOException {
        child.open();
        // load all the tuples in a collection, and sort it
        while (child.hasNext())
            childTups.add((Tuple) child.next());
        Collections.sort(childTups, new TupleComparator(orderByField, asc));
        it = childTups.iterator();
        super.open();
    }

    public void close() {
        super.close();
        it = null;
    }

    public void rewind() throws DBException, TransactionAbortedException {
        it = childTups.iterator();
    }

    /**
     * Operator.fetchNext implementation. Returns tuples from the child operator
     * in order
     * 
     * @return The next tuple in the ordering, or null if there are no more
     *         tuples
     */
    protected Tuple fetchNext() throws NoSuchElementException,
            TransactionAbortedException, DBException {
        if (it != null && it.hasNext()) {
            return it.next();
        } else
            return null;
    }

    @Override
    public DbIterator[] getChildren() {
        return new DbIterator[] { this.child };
    }

    @Override
    public void setChildren(DbIterator[] children) {
        this.child = children[0];
    }

}

class TupleComparator implements Comparator<Tuple> {
    int field;
    boolean asc;

    public TupleComparator(int field, boolean asc) {
        this.field = field;
        this.asc = asc;
    }

    public int compare(Tuple o1, Tuple o2) {
        Field t1 = (o1).getField(field);
        Field t2 = (o2).getField(field);
        if (t1.compareWith(Comparison.Operation.EQUALS, t2))
            return 0;
        if (t1.compareWith(Comparison.Operation.GREATER_THAN, t2))
            return asc ? 1 : -1;
        else
            return asc ? -1 : 1;
    }
    
}
