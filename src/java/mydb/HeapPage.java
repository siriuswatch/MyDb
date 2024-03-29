package mydb;

import mydb.Database.BufferPool;
import mydb.Database.Catalog;
import mydb.Database.Database;
import mydb.Exception.DBException;
import mydb.TupleDetail.Tuple;
import mydb.TupleDetail.TupleDetail;

import java.io.*;
import java.text.ParseException;
import java.util.Iterator;
import java.util.NoSuchElementException;


/**
 * Each instance of HeapPage stores data for one page of HeapFiles and 
 * implements the Page interface that is used by BufferPool.
 *
 * @see HeapFile
 * @see BufferPool
 *
 */
public class HeapPage implements Page {

    private HeapPageId heapPageId;
    private TupleDetail tupleDetail;
    private byte[] header;
    private Tuple[] tuples;
    private int tupleNumbersInPage;

    private byte[] oldData;

    private TransactionId transactionId;

    /**
     * Create a HeapPage from a set of bytes of data read from disk.
     * The format of a HeapPage is a set of header bytes indicating
     * the slots of the page that are in use, some number of tuple slots.
     *  Specifically, the number of tuples is equal to: <p>
     *          floor((BufferPool.PAGE_SIZE*8) / (tuple size * 8 + 1))
     * <p> where tuple size is the size of tuples in this
     * database table, which can be determined via {@link Catalog#getTupleDetail}.
     * The number of 8-bit header words is equal to:
     * <p>
     *      ceiling(no. tuple slots / 8)
     * <p>
     * @see Database#getCatalog
     * @see Catalog#getTupleDetail
     * @see BufferPool#PAGE_SIZE
     */
    //read from which page and table
    //download data
    public HeapPage(HeapPageId id, byte[] data) throws IOException {
        // get page id
        this.heapPageId = id;
        // get tuple detail : by get table info  in catalog
        this.tupleDetail = Database.getCatalog().getTupleDetail(heapPageId.getTableId());
        // define : tuple number = floor((BufferPool.PAGE_SIZE*8) / (tuple size * 8 + 1))
        tupleNumbersInPage = (BufferPool.PAGE_SIZE * 8 )/ (tupleDetail.getSize() * 8 + 1);
        // read data by bytes
        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(data));

        // allocate and read the header slots of this page
        int headerSize = (int) Math.ceil(((double) tupleNumbersInPage) / 8.0);

        // put data into head
        header = new byte[headerSize];
        for (int i = 0; i< headerSize; i++)
            header[i] = dataInputStream.readByte();

        try{
            // allocate and read the actual records of this page
            tuples = new Tuple[tupleNumbersInPage];
            for (int i = 0; i< tupleNumbersInPage; i++)
                tuples[i] = readNextTuple(dataInputStream,i);

        }catch(NoSuchElementException | ParseException e){
            e.printStackTrace();
        }
        dataInputStream.close();
        setBeforeImage();
    }


    /**
     * Computes the number of bytes in the header of a page in a HeapFile with each tuple occupying tupleSize bytes
     * @return the number of bytes in the header of a page in a HeapFile with each tuple occupying tupleSize bytes
     */
//    private int getHeaderSize() {
//        return (int)Math.ceil((double)getNumTuples()/8.0);
//    }
    
    /** Return a view of this page before it was modified
        -- used by recovery */
    public HeapPage getBeforeImage(){
        try {
            return new HeapPage(heapPageId,oldData);
        } catch (IOException e) {
            e.printStackTrace();
            //should never happen -- we parsed it OK before!
            System.exit(1);
        }
        return null;
    }
    
    public void setBeforeImage() {
        oldData = getPageData().clone();
    }

    public int getTupleNumber() {
        int result = 0;
        for(int i = 0; i< tuples.length ; i++)
            if (tuples[i] != null)
                result++;
        return result;
    }

    /**
     * @return the PageId associated with this page.
     */
    public HeapPageId getId() {
        return heapPageId;
    }

    /**
     * Suck up tuples from the source file.
     */
    private Tuple readNextTuple(DataInputStream dataInputStream, int slotId)
            throws NoSuchElementException, ParseException, IOException {
        // if associated bit is not set, read forward to the next tuple, and return null
        Tuple tuple = null;
        if(isSlotUsed(slotId)){
            tuple = new Tuple(tupleDetail);
            for(int i = 0; i< tupleDetail.fieldNumber();i++)
                tuple.setField(i,tupleDetail.getFieldType(i).parse(dataInputStream));
            tuple.setRecordId( new RecordId(heapPageId, slotId));
        }else {
            for(int i = 0; i< tupleDetail.getSize(); i++)
                dataInputStream.readByte();
        }
        return tuple;
    }

    /**
     * Generates a byte array representing the contents of this page.
     * Used to serialize this page to disk.
     * <p>
     * The invariant here is that it should be possible to pass the byte
     * array generated by getPageData to the HeapPage constructor and
     * have it produce an identical HeapPage object.
     *
     * @see #HeapPage
     * @return A byte array correspond to the bytes of this page.
     */
    public byte[] getPageData() {
        int len = BufferPool.PAGE_SIZE;
        ByteArrayOutputStream output = new ByteArrayOutputStream(len);
        DataOutputStream dos = new DataOutputStream(output);

        // create the header of the page
        for (byte b : header) {
            try {
                dos.writeByte(b);
            } catch (IOException e) {
                // this really shouldn't happen
                e.printStackTrace();
            }
        }

        // create the tuples
        for (int i=0; i<tuples.length; i++) {

            // empty slot
            if (!isSlotUsed(i)) {
                for (int j = 0; j< tupleDetail.getSize(); j++) {
                    try {
                        dos.writeByte(0);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                continue;
            }

            // non-empty slot
            for (int j = 0; j< tupleDetail.fieldNumber(); j++) {
                Field f = tuples[i].getField(j);
                try {
                    f.serialize(dos);
                
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // padding
        int zerolen = BufferPool.PAGE_SIZE - (header.length + tupleDetail.getSize() * tuples.length); //- numSlots * td.getSize();
        byte[] zeroes = new byte[zerolen];
        try {
            dos.write(zeroes, 0, zerolen);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return output.toByteArray();
    }

    /**
     * Static method to generate a byte array corresponding to an empty
     * HeapPage.
     * Used to add new, empty pages to the file. Passing the results of
     * this method to the HeapPage constructor will create a HeapPage with
     * no valid tuples in it.
     *
     * @return The returned ByteArray.
     */
    // create the empty bytes
    // write in the page
    public static byte[] createEmptyPageData() {
        return new byte[BufferPool.PAGE_SIZE]; //all 0
    }

    /**
     * Delete the specified tuple from the page;  the tuple should be updated to reflect
     *   that it is no longer stored on any page.
     * @throws DBException if this tuple is not on this page, or tuple slot is
     *         already empty.
     */
    public void deleteTuple(Tuple tuple) throws DBException {
        if(tuple ==null) throw new DBException("delete tuple is invalid");
        if(!(heapPageId).equals(tuple.getRecordId().getPageId())) throw new DBException("delete tuple heappageId not equal");
        int TupleNo = tuple.getRecordId().tupleno();
        if(!isSlotUsed(TupleNo)) throw new DBException("tuple slot is already empty");
        tuples[TupleNo] = null;
        markSlotUsed(TupleNo, false);
    }

    /**
     * Adds the specified tuple to the page;  the tuple should be updated to reflect
     *  that it is now stored on this page.
     * @throws DBException if the page is full (no empty slots) or tupledesc
     *         is mismatch.
     */
    public void insertTuple(Tuple tuple) throws DBException {
        if (tuple == null) throw new DBException("insert tuple is invalid");
        if (!tuple.getTupleDetail().equals(tupleDetail)) throw new DBException("insert error: tupledetail not match");
        for (int i = 0; i < tupleNumbersInPage; i++){
            if (isSlotUsed(i)) continue;
            tuples[i] = tuple;
            markSlotUsed(i, true);
            RecordId recordId = new RecordId(heapPageId,i);
            tuple.setRecordId(recordId);
            return;
        }
        throw new DBException("insert error: page cannot be inserted tuples");
    }

    /**
     * Marks this page as dirty/not dirty and record that transaction
     * that did the dirtying
     */
    public void markDirty(boolean dirty, TransactionId tid) {
        transactionId = dirty? tid:null;
    }

    /**
     * Returns the tid of the transaction that last dirtied this page, or null if the page is not dirty
     */
    public TransactionId isDirty() {
        return transactionId;
    }

    /**
     * Returns the number of empty slots on this page.
     */
    public int getNumEmptySlots() {
        int result = 0;
        for(int i = 0; i< tupleNumbersInPage; i++){
            result = isSlotUsed(i)? result:result+1;
        }
        return result;
    }

    /**
     * Returns true if associated slot on this page is filled.
     */
    public boolean isSlotUsed(int i) {
        // i/8 to calculate where section the data is
        int t = i/8;
        int d = i%8;
        // if head[i] == 1 return true
        return (byte) (header[t] << (7 - d)) < 0;
    }

    /**
     * Abstraction to fill or clear a slot on this page.
     */
    // TODO: site: 补位
    // https://blog.csdn.net/weixx3/article/details/78560048
    private void markSlotUsed(int i, boolean value) {
        int t = i/8;
        int d = i%8;
        byte a = (byte) ((byte)1 << d);
        header[t] = value? (byte)(header[t] | a): (byte) (header[t] & ~a);
    }

    /**
     * @return an iterator over all tuples on this page (calling remove on this iterator throws an UnsupportedOperationException)
     * (note that this iterator shouldn't return tuples in empty slots!)
     */
    public Iterator<Tuple> iterator() {
        return new UsedTupleIterator();
    }

    class UsedTupleIterator implements Iterator<Tuple> {

        int divide = 0;
        int count = 0;
        int usedTuplesNum = tupleNumbersInPage - getNumEmptySlots();

        @Override
        public boolean hasNext() {
            return tupleNumbersInPage > count && divide < usedTuplesNum;
        }

        @Override
        public Tuple next() {
            if(!hasNext()) throw new NoSuchElementException();
//            while (!isSlotUsed(count)) count++;
            for (; !isSlotUsed(count); count++) {
            }
            divide++;
            //count = count +1;
            return tuples[count++];
        }
    }

    public static void main(String[] args) {
        byte b = (byte) 1;
        System.out.println(Integer.toBinaryString(b));
        int a = b << 7;
        System.out.println(Integer.toBinaryString(a));
        System.out.println((byte) a);
    }

}

