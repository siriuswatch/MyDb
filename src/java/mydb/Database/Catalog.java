package mydb.Database;

import mydb.DbFile;
import mydb.HeapFile;
import mydb.TupleDetail.TupleDetail;
import mydb.Type;

import java.io.*;
import java.util.*;

/**
 * The Catalog keeps track of all available tables in the database and their
 * associated schemas.
 * For now, this is a stub catalog that must be populated with tables by a
 * user program before it can be used -- eventually, this should be converted
 * to a catalog that reads a catalog table from disk.
 */

public class Catalog {

    private HashMap<String, Integer> tableNameIdMap;

    private HashMap<Integer, DbFile> tableIdFileMap;

    private HashMap<Integer, String> tableIdKeyMap;

    private HashMap<Integer, String> tableIdNameMap;

    /**
     * Constructor.
     * Creates a new, empty catalog.
     */
    Catalog() {
        tableNameIdMap = new HashMap<>();
        tableIdFileMap =new HashMap<>();
        tableIdKeyMap = new HashMap<>();
        tableIdNameMap = new HashMap<>();
    }

    /**
     * Add a new table to the catalog.
     * This table's contents are stored in the specified DbFile.
     * @param file the contents of the table to add;  file.getId() is the identfier of
     *    this file/tupledesc param for the calls getTupleDesc and getFile
     * @param name the name of the table -- may be an empty string.  May not be null.  If a name
     * @param pkeyField the name of the primary key field
     * conflict exists, use the last table to be added as the table for a given name.
     */
    private void addTable(DbFile file, String name, String pkeyField) {
        if (name == null || pkeyField == null || file == null)  throw new IllegalArgumentException();
        tableNameIdMap.put(name, file.getId());
        tableIdFileMap.put(file.getId(), file);
        tableIdKeyMap.put(file.getId(),pkeyField);
        tableIdNameMap.put(file.getId(),name);
    }

    public void addTable(DbFile file, String name) {
        addTable(file, name, "");
    }

    /**
     * Add a new table to the catalog.
     * This table has tuples formatted using the specified TupleDesc and its
     * contents are stored in the specified DbFile.
     * @param file the contents of the table to add;  file.getId() is the identfier of
     *    this file/tupledesc param for the calls getTupleDesc and getFile
     */
    public void addTable(DbFile file) {
        addTable(file, (UUID.randomUUID()).toString());
    }

    /**
     * Return the id of the table with a specified name,
     * @throws NoSuchElementException if the table doesn't exist
     */
    public int getTableId(String name) throws NoSuchElementException {
        if(name == null) throw new NoSuchElementException();
        Integer result = tableNameIdMap.get(name);
        if (result != null) return  result;
        throw new NoSuchElementException();
    }

    /**
     * Returns the tuple descriptor (schema) of the specified table
     * @param tableid The id of the table, as specified by the DbFile.getId()
     *     function passed to addTable
     * @throws NoSuchElementException if the table doesn't exist
     */
    public TupleDetail getTupleDetail(int tableid) throws NoSuchElementException {
        DbFile result = tableIdFileMap.get(tableid);
        if(result!=null) return result.getTupleDetail();
        throw new NoSuchElementException();
    }

    /**
     * Returns the DbFile that can be used to read the contents of the
     * specified table.
     * @param tableid The id of the table, as specified by the DbFile.getId()
     *     function passed to addTable
     */
    public DbFile getDbFile(int tableid) throws NoSuchElementException {
        DbFile result = tableIdFileMap.get(tableid);
        if(result!=null) return result;
        throw new NoSuchElementException();
    }

    public String getPrimaryKey(int tableid) {
        String result = tableIdKeyMap.get(tableid);
        if(result!=null) return result;
        throw new NoSuchElementException();
    }

    public Iterator<Integer> tableIdIterator() {
        return tableIdFileMap.keySet().iterator();
    }

    public String getTableName(int id) {
        String result = tableIdNameMap.get(id);
        if(result!=null) return result;
        throw new NoSuchElementException();
    }
    
    /** Delete all tables from the catalog */
    public void clear() {
        tableNameIdMap.clear();
        tableIdFileMap.clear();
        tableIdKeyMap.clear();
        tableIdNameMap.clear();
    }
    
    // Reads the schema from a file and creates the appropriate tables in the database.
    public void loadSchema(String catalogFile) {
        String line = "";
        String baseFolder=new File(catalogFile).getParent();
        try {
            // read from disk
            BufferedReader br = new BufferedReader(new FileReader(new File(catalogFile)));
            
            while ((line = br.readLine()) != null) {
                //assume line is of the format name (field type, field type, ...)
                String name = line.substring(0, line.indexOf("(")).trim();
                System.out.println("TABLE NAME: " + name);
                String fields = line.substring(line.indexOf("(") + 1, line.indexOf(")")).trim();
                String[] els = fields.split(",");
                ArrayList<String> names = new ArrayList<>();
                ArrayList<Type> types = new ArrayList<>();
                String primaryKey = "";
                for (String e : els) {
                    String[] els2 = e.trim().split(" ");
                    names.add(els2[0].trim());
                    if (els2[1].trim().toLowerCase().equals("int"))
                        types.add(Type.INT_TYPE);
                    else if (els2[1].trim().toLowerCase().equals("string"))
                        types.add(Type.STRING_TYPE);
                    else {
                        System.out.println("Unknown type " + els2[1]);
                        System.exit(0);
                    }
                    if (els2.length == 3) {
                        if (els2[2].trim().equals("pk"))
                            primaryKey = els2[0].trim();
                        else {
                            System.out.println("Unknown annotation " + els2[2]);
                            System.exit(0);
                        }
                    }
                }
                Type[] typeAr = types.toArray(new Type[0]);
                String[] namesAr = names.toArray(new String[0]);
                TupleDetail t = new TupleDetail(typeAr, namesAr);


                HeapFile tabHf = new HeapFile(new File(baseFolder+"/"+name + ".dat"), t);
                addTable(tabHf,name,primaryKey);
                System.out.println("TABLE: " + name + "; attribute: " + t + " ; end; ");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        } catch (IndexOutOfBoundsException e) {
            System.out.println ("Invalid catalog entry : " + line);
            System.exit(0);
        }
    }
}

