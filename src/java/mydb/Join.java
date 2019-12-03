package mydb;
import mydb.Exception.DBException;
import mydb.Exception.TransactionAbortedException;
import mydb.TupleDetail.Tuple;
import mydb.TupleDetail.TupleDetail;
import java.io.IOException;
import java.util.*;

/**
 * The Join operator implements the relational join operation.
 */
public class Join extends Operator {

    private static final long serialVersionUID = 1421683959262591903L;
    // The predicate to use to join the children
    private JoinCompare p;
    // Iterator for the left(outer) relation to join
    private DbIterator child1;
    // Iterator for the right(inner) relation to join
    private DbIterator child2;
    private Tuple[] leftBuffer;
    private Tuple[] rightBuffer;
    private ArrayList<Tuple> tempTps;

    //131072 is the default buffer of mysql join operation
    private static final int BLOCK_MEMORY = 131072 * 5;


    public Join(JoinCompare p, DbIterator child1, DbIterator child2) {
        this.p = p;
        this.child1 = child1;
        this.child2 = child2;
    }

    public JoinCompare getJoinPredicate() {
        return p;
    }

    //the field name of join field1
    public String getJoinField1Name() {
        SeqScan scan = (SeqScan)child1;
        return scan.getTableName();
    }

    public String getJoinField2Name() {
        SeqScan scan = (SeqScan)child2;
        return scan.getAlias();
    }

    public TupleDetail getTupleDetail() {
        TupleDetail td1 = child1.getTupleDetail();
        TupleDetail td2 = child2.getTupleDetail();
        return TupleDetail.merge(td1, td2);
    }

    public void open() throws DBException, NoSuchElementException,
            TransactionAbortedException, IOException {
        super.open();
        child1.open();
        child2.open();
        tpIter = getAllFetchNext();
    }

    public void close() {
        child1.close();
        child2.close();
        super.close();
        tpIter = null;
    }

    public void rewind() throws DBException, TransactionAbortedException, IOException {
        child1.rewind();
        child2.rewind();
        tpIter = getAllFetchNext();
    }
    private Iterator<Tuple> tpIter = null;

    // Returns the next tuple generated by the join
    protected Tuple fetchNext() throws TransactionAbortedException, DBException {
        if (tpIter == null) return null;
        Tuple tp = null;
        if (tpIter.hasNext()){
            tp = tpIter.next();
        }
        return tp;
    }

    /*执行 sort-merge 算法 link(https://en.wikipedia.org/wiki/Sort-merge_join)
     * join算法有很多种，我目前想到了三种解法
     * 1. 用hashmap缓存一张表。但是缺点是只适合于外表内容很少的情况下
     * 2. 用数组缓存，使用BNL算法，缺点是速度还是满，第一个query用了0.3s，第二个query用了2s，第三个query用了423.04s，第三个速度太慢
     * 3. sort-merge 算法 排序算法决定整个程序运行速度下限，刚开始使用冒泡排序，第二个query用了140多秒，第三个就更不用说了
     *    之后使用java内置的sort速度明显提高，第一个0.40s，第二个2.30s，第三个6.28s
     */
    private Iterator<Tuple> getAllFetchNext() throws TransactionAbortedException, DBException, IOException {
        tempTps = new ArrayList<>();

        //use sorted-merge algorithm
        int leftBufferSize = BLOCK_MEMORY / child1.getTupleDetail().getSize();
        int rightBufferSize = BLOCK_MEMORY / child2.getTupleDetail().getSize();

        leftBuffer = new Tuple[leftBufferSize];
        rightBuffer = new Tuple[rightBufferSize];

        int leftIndex = 0;
        int rightIndex = 0;

        //先将数据读取到buffer里面
        while (child1.hasNext()){
            Tuple tp1 = child1.next();
            leftBuffer[leftIndex++] = tp1;

            //左缓存没有读满就一直读
            if (leftIndex < leftBufferSize) continue;

            //左缓存读满，读右表，直至读满
            while (child2.hasNext()){
                Tuple tp2 = child2.next();
                rightBuffer[rightIndex++] = tp2;

                //右缓存没有读满就一直读
                if (rightIndex < rightBufferSize) continue;

                //右缓存读满 && 左缓存读满

                sortMerge(leftIndex, rightIndex);

                //重置右缓存
                rightIndex = 0;
            }

            //处理剩余的右缓存（右缓存没满 && 左缓存已满）
            if (rightIndex < rightBufferSize) {

                sortMerge(leftIndex, rightIndex);

                //重置右缓存
                rightIndex = 0;
            }

            //reset buffer
            leftIndex = 0;
            child2.rewind();
        }

        //左缓存没满
        if (leftIndex != 0) {

            //读右表，直至读满
            while (child2.hasNext()){
                Tuple tp2 = child2.next();
                rightBuffer[rightIndex++] = tp2;

                //右缓存没有读满就一直读
                if (rightIndex < rightBufferSize) continue;

                //右缓存读满 && 左缓存没满

                sortMerge(leftIndex, rightIndex);

                //重置右缓存
                rightIndex = 0;
            }

            //（右缓存没满 && 左缓存没满）
            if (rightIndex < rightBufferSize) {

                sortMerge(leftIndex, rightIndex);

                //重置右缓存
                rightIndex = 0;
            }

        }

        return tempTps.iterator();
    }

    private void sortMerge(int leftSize, int rightSize) {
        if (leftSize == 0 || rightSize == 0 ) return;

        //EQUALS, GREATER_THAN, LESS_THAN, LESS_THAN_OR_EQ, GREATER_THAN_OR_EQ, LIKE, NOT_EQUALS;
        switch (p.getOperator()){
            case EQUALS:
                handleEqual(leftSize, rightSize);
                break;
            case GREATER_THAN:
            case GREATER_THAN_OR_EQ:
                handleGreaterThan(leftSize, rightSize);
                break;
            case LESS_THAN:
            case LESS_THAN_OR_EQ:
                handleLessThan(leftSize, rightSize);
                break;
        }

    }

    private void handleLessThan(int leftSize, int rightSize) {
        sort(leftBuffer, leftSize, p.getField1(), false);
        sort(rightBuffer, rightSize, p.getField2(), false);

        int left = 0;
        int right = 0;

        while (left < leftSize && right < rightSize) {
            Tuple ltp = leftBuffer[left];
            Tuple rtp = rightBuffer[right];

            if (p.filter(ltp, rtp)){
                for (int i = right; i < rightSize; i++) {
                    Tuple rtpTemp = rightBuffer[i];
                    Tuple tp = mergeTuple(ltp, rtpTemp);
                    tempTps.add(tp);
                }
                left++;
            } else {
                right++;
            }
        }
    }

    private void handleGreaterThan(int leftSize, int rightSize) {
        sort(leftBuffer, leftSize, p.getField1(), true);
        sort(rightBuffer, rightSize, p.getField2(), true);

        int left = 0;
        int right = 0;

        while (left < leftSize && right < rightSize) {
            Tuple ltp = leftBuffer[left];
            Tuple rtp = rightBuffer[right];

            if (p.filter(ltp, rtp)){
                //将比他小的都合并在一起
                for (int i = right; i < rightSize; i++) {
                    Tuple rtpTemp = rightBuffer[i];
                    Tuple tp = mergeTuple(ltp, rtpTemp);
                    tempTps.add(tp);
                }
                left++;
            } else {
                right++;
            }
        }
    }

    private void handleEqual(int leftSize, int rightSize) {
        sort(leftBuffer, leftSize, p.getField1(), false);
        sort(rightBuffer, rightSize, p.getField2(), false);

        int left = 0;
        int right = 0;

        JoinCompare greatThan = new JoinCompare(p.getField1(), Comparison.Operation.GREATER_THAN, p.getField2());

        boolean equalFlag = true;
        int leftFlag = 0;

        while (left < leftSize && right < rightSize) {
            Tuple ltp = leftBuffer[left];
            Tuple rtp = rightBuffer[right];

            if (p.filter(ltp, rtp)){
                if (equalFlag) {
                    leftFlag = left;
                    equalFlag = !equalFlag;
                }
                Tuple tp = mergeTuple(ltp, rtp);
                tempTps.add(tp);
                left++;

                if (right < rightSize && left >= leftSize) {
                    right++;
                    left = leftFlag;
                    equalFlag = !equalFlag;
                }

            } else if (greatThan.filter(ltp, rtp)){
                right++;
                left = leftFlag;
                equalFlag = !equalFlag;
            } else {
                left++;
            }
        }
    }

    //根据tuple中的field进行排序
    private void sort(Tuple[] buffer, int length, int field, boolean reverse) {

        CompareTp co = new CompareTp(reverse, field);
        Arrays.sort(buffer, 0, length, co);
        // 冒泡排序
        // for (int i = 1; i < length; i++) {
        //     for (int j = 0; j < length - i; j++) {
        //         if (reverse) {
        //             if (greatThan.filter(buffer[j+1], buffer[j])) {
        //                 Tuple temp = buffer[j];
        //                 buffer[j] = buffer[j + 1];
        //                 buffer[j + 1] = temp;
        //             }
        //         } else {
        //             if (greatThan.filter(buffer[j], buffer[j+1])) {
        //                 Tuple temp = buffer[j];
        //                 buffer[j] = buffer[j + 1];
        //                 buffer[j + 1] = temp;
        //             }
        //         }
        //     }
        // }

    }

    class CompareTp implements Comparator<Tuple>{

        private JoinCompare cop;

        public CompareTp(boolean reverse, int field){
            super();
            if (reverse) {
                cop = new JoinCompare(field, Comparison.Operation.LESS_THAN, field);
            } else {
                cop = new JoinCompare(field, Comparison.Operation.GREATER_THAN, field);
            }
        }

        @Override
        public int compare(Tuple t1, Tuple t2){
            //t1>t2
            if (cop.filter(t1, t2)){
                return 1;
            } else if (cop.filter(t2, t1)){
                return -1;
            } else {
                return 0;
            }
        }
    }

    private Tuple mergeTuple(Tuple tp1, Tuple tp2) {
        int tpSize1 = tp1.getTupleDetail().fieldNumber();
        int tpSize2 = tp2.getTupleDetail().fieldNumber();

        Tuple tempTp = new Tuple(getTupleDetail());
        int i = 0;
        for (; i < tpSize1; i++){
            tempTp.setField(i, tp1.getField(i));
        }

        for (; i < tpSize2 + tpSize1 ; i++){
            tempTp.setField(i, tp2.getField(i-tpSize1));
        }

        return tempTp;
    }

    @Override
    public DbIterator[] getChildren() {
        // some code goes here
        return new DbIterator[]{ child1, child2 };
    }

    @Override
    public void setChildren(DbIterator[] children) {
        // some code goes here
        child1 = children[0];
        child2 = children[1];
    }

}