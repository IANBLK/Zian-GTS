package com.zianblk.ziangts.storage;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;

/** Transactional document/page storage. Legacy file formats remain domain-owned. */
public final class StorageFiles {
  public static final String MOD = "gts";
  private static final int PAGE = 4096;
  private static final Map<Path, Database> DATABASES = new HashMap<>();
  private static Driver driver;
  private static URLClassLoader privateDriver;
  private StorageFiles() {}
  record Location(Path world, Path database, String name) {}

  static Location location(Path input) throws IOException {
    Path path = input.toAbsolutePath().normalize();
    for (Path root = path.getParent(); root != null; root = root.getParent()) {
      Path marker = root.resolve("zian-storage/storage.properties");
      if (!Files.isRegularFile(marker)) continue;
      String name = root.relativize(path).toString().replace('\\', '/');
      if (!owned(name)) return null;
      Properties config = new Properties();
      try (var reader = Files.newBufferedReader(marker)) { config.load(reader); }
      if (!"sqlite-v1".equals(config.getProperty(MOD))) return null;
      Path database = root.resolve("zian-storage/" + MOD + ".db");
      if (!Files.isRegularFile(database) || Files.isSymbolicLink(database))
        throw new IOException("Enabled SQLite database is missing or unsafe; refusing legacy fallback");
      if (Files.exists(root.resolve("zian-storage/" + MOD + ".maintenance")))
        throw new IOException("SQLite maintenance requires review");
      return new Location(root, database, name);
    }
    return null;
  }

  public static boolean owned(String name) {
    return switch (MOD) {
      case "manager" -> name.equals("data/zianmanager") || name.startsWith("data/zianmanager/");
      case "utilities" -> name.equals("data/zianutilities") || name.startsWith("data/zianutilities/");
      case "rct" -> name.startsWith("data/zianrct-");
      case "gts" -> name.equals("ziangts-v2") || name.startsWith("ziangts-v2/");
      default -> false;
    };
  }

  private static synchronized Database db(Location location) throws IOException {
    var cached = DATABASES.get(location.database());
    if (cached == null) { cached = new Database(location.database()); DATABASES.put(location.database(), cached); }
    return cached;
  }

  static synchronized Connection connect(Path path) throws IOException {
    try {
      if (driver == null) {
        try {
          Driver available = (Driver) Class.forName("org.sqlite.JDBC", true, StorageFiles.class.getClassLoader()).getConstructor().newInstance();
          try (Connection probe = available.connect("jdbc:sqlite::memory:", new Properties()); var s = probe.createStatement(); var r = s.executeQuery("select sqlite_version()")) {
            r.next(); String[] v = r.getString(1).split("\\.");
            if (Integer.parseInt(v[0]) > 3 || Integer.parseInt(v[0]) == 3 && (Integer.parseInt(v[1]) > 51 || Integer.parseInt(v[1]) == 51 && Integer.parseInt(v[2]) >= 3)) driver = available;
          }
        } catch (ClassNotFoundException missing) { /* Standalone NeoForge uses the private resource. */ }
        if (driver == null) {
          Path jar = Files.createTempFile("zian-sqlite-" + MOD + "-", ".jar");
          try (var source = StorageFiles.class.getResourceAsStream("/META-INF/zian-libraries/sqlite-jdbc.jar")) {
            if (source == null) throw new IOException("Missing bundled SQLite driver");
            Files.copy(source, jar, StandardCopyOption.REPLACE_EXISTING);
          }
          jar.toFile().deleteOnExit();
          // Parent sees Minecraft's logging API; the JDBC JAR is never a NeoForge module.
          privateDriver = new URLClassLoader(new URL[]{jar.toUri().toURL()}, StorageFiles.class.getClassLoader());
          // An old provided driver must not shadow our compatible private copy.
          if (classPresent("org.sqlite.JDBC")) {
            privateDriver.close();
            privateDriver = new URLClassLoader(new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
          }
          driver = (Driver) Class.forName("org.sqlite.JDBC", true, privateDriver).getConstructor().newInstance();
        }
      }
      Connection connection = driver.connect("jdbc:sqlite:" + path.toAbsolutePath(), new Properties());
      if (connection == null) throw new IOException("SQLite driver rejected the database");
      return connection;
    } catch (IOException error) { throw error; }
    catch (Exception error) { throw new IOException("SQLite driver unavailable; writes blocked", error); }
  }
  private static boolean classPresent(String name) {
    try { Class.forName(name, false, StorageFiles.class.getClassLoader()); return true; }
    catch (ClassNotFoundException missing) { return false; }
  }

  static final class Database implements AutoCloseable {
    final Connection connection;
    final FileChannel guard;
    final FileLock lease;
    final Set<String> locked = new HashSet<>();
    boolean healthy = true;
    Database(Path path) throws IOException { this(path,false); }
    Database(Path path, boolean initialize) throws IOException {
      Files.createDirectories(path.toAbsolutePath().getParent());
      guard = FileChannel.open(path.resolveSibling(path.getFileName() + ".guard"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      FileLock acquired = null; Connection opened = null;
      try {
        acquired = guard.tryLock();
        if (acquired == null) throw new IOException("SQLite storage already owned by another process");
        opened = connect(path); connection = opened; lease = acquired;
        try (var s = connection.createStatement()) {
          s.execute("pragma busy_timeout=5000"); s.execute("pragma foreign_keys=on");
          try (var r=s.executeQuery("pragma journal_mode=WAL")) { if(!r.next() || !"wal".equalsIgnoreCase(r.getString(1))) throw new IOException("SQLite WAL unavailable"); }
          s.execute("pragma synchronous=FULL");
          try (var r = s.executeQuery("pragma quick_check")) { if (!r.next() || !"ok".equals(r.getString(1))) throw new IOException("SQLite integrity check failed"); }
          if(initialize) s.execute("create table if not exists storage_schema(version integer not null)");
          try(var r=s.executeQuery("select version from storage_schema")){if(r.next()){if(r.getInt(1)!=1 || r.next())throw new IOException("Unsupported SQLite schema");}else {if(!initialize)throw new IOException("SQLite schema missing; refusing to initialize empty storage");s.execute("insert into storage_schema values(1)");}}
          if(initialize){
          s.execute("create table if not exists documents(name text primary key,size integer not null,kind integer not null,modified integer not null)");
          s.execute("create table if not exists pages(name text not null references documents(name) on delete cascade on update cascade,page integer not null,body blob not null,sha blob not null,encoding integer not null,primary key(name,page))");
          s.execute("create table if not exists receipts(scope text not null,id text not null,primary key(scope,id))");
          s.execute("create table if not exists owners(id text primary key,last_seen integer not null)");
          s.execute("create table if not exists reward_receipts(scope text not null,id text not null,owner text not null,source text not null,cycle integer not null,created integer not null,cooldown integer not null,battle text,sha blob not null,primary key(scope,id),unique(scope,owner,source,cycle))");
          s.execute("create index if not exists reward_receipts_source on reward_receipts(scope,owner,source,cycle)");
          s.execute("create index if not exists reward_receipts_battle on reward_receipts(scope,owner,source,battle)");
          s.execute("create index if not exists documents_modified on documents(kind,modified)");
          }else{
            for(String table:List.of("documents","pages","receipts","owners","reward_receipts")){
              try(var q=connection.prepareStatement("select 1 from sqlite_master where type='table' and name=?")){q.setString(1,table);try(var r=q.executeQuery()){if(!r.next())throw new IOException("SQLite table missing; refusing incomplete restore: "+table);}}
            }
          }
        }
      } catch (Exception error) {
        if(opened!=null)try{opened.close();}catch(Exception suppressed){error.addSuppressed(suppressed);}
        if(acquired!=null)try{acquired.release();}catch(Exception suppressed){error.addSuppressed(suppressed);}
        guard.close(); throw io(error);
      }
    }
    void check() throws IOException { if (!healthy) throw new IOException("SQLite storage unavailable; review before retrying"); }
    synchronized long[] info(String name) throws IOException {
      check(); try (var q=connection.prepareStatement("select size,kind from documents where name=?")) {q.setString(1,name);try(var r=q.executeQuery()){return r.next()?new long[]{r.getLong(1),r.getLong(2)}:null;}}catch(SQLException error){throw fail(error);}
    }
    synchronized byte[] page(String name,long index) throws IOException {
      check();try(var q=connection.prepareStatement("select body,sha,encoding from pages where name=? and page=?")){q.setString(1,name);q.setLong(2,index);try(var r=q.executeQuery()){
        if(!r.next())throw new IOException("SQLite page missing");byte[] raw=r.getBytes(1);if(r.getInt(3)==1)raw=inflate(raw);
        if(raw.length!=PAGE || !MessageDigest.isEqual(hash(raw),r.getBytes(2)))throw new IOException("SQLite page checksum failed");return raw;
      }}catch(IOException|SQLException error){throw fail(error);}
    }
    synchronized void create(String name,boolean directory,boolean exclusive) throws IOException {
      check();var old=info(name);if(old!=null){if(exclusive || old[1]!=(directory?1:0))throw new FileAlreadyExistsException(name);return;}
      try(var q=connection.prepareStatement("insert into documents values(?,?,?,?)")){q.setString(1,name);q.setLong(2,0);q.setInt(3,directory?1:0);q.setLong(4,System.currentTimeMillis());q.executeUpdate();}catch(SQLException error){throw fail(error);}
    }
    synchronized void flush(String name,long size,Map<Long,byte[]> dirty,boolean truncated) throws IOException {
      transaction(()->{
        if(truncated)try(var q=connection.prepareStatement("delete from pages where name=?")){q.setString(1,name);q.executeUpdate();}
        try(var q=connection.prepareStatement("insert into pages values(?,?,?,?,0) on conflict(name,page) do update set body=excluded.body,sha=excluded.sha,encoding=0")){
          for(var entry:dirty.entrySet()){q.setString(1,name);q.setLong(2,entry.getKey());q.setBytes(3,entry.getValue());q.setBytes(4,hash(entry.getValue()));q.addBatch();}q.executeBatch();
        }
        try(var q=connection.prepareStatement("update documents set size=?,modified=? where name=?")){q.setLong(1,size);q.setLong(2,System.currentTimeMillis());q.setString(3,name);if(q.executeUpdate()!=1)throw new IOException("SQLite document disappeared");}
      });
    }
    @FunctionalInterface interface Action {void run() throws Exception;}
    synchronized void transaction(Action action) throws IOException {
      check();try{connection.setAutoCommit(false);action.run();connection.commit();}catch(Exception error){try{connection.rollback();}catch(Exception suppressed){error.addSuppressed(suppressed);}throw fail(error);}finally{try{connection.setAutoCommit(true);}catch(SQLException error){healthy=false;}}
    }
    synchronized List<String> children(String name) throws IOException {
      check();String prefix=name+"/";var result=new ArrayList<String>();
      try(var q=connection.prepareStatement("select name from documents where name>=? and name<? order by name")){q.setString(1,prefix);q.setString(2,prefix.substring(0,prefix.length()-1)+"0");try(var r=q.executeQuery()){while(r.next()){String n=r.getString(1);if(n.indexOf('/',prefix.length())<0)result.add(n);}}return result;}catch(SQLException error){throw fail(error);}
    }
    synchronized void remove(String name) throws IOException {
      if(locked.contains(name))throw new IOException("SQLite document still in use");
      if(!children(name).isEmpty())throw new DirectoryNotEmptyException(name);
      try(var q=connection.prepareStatement("delete from documents where name=?")){q.setString(1,name);q.executeUpdate();}catch(SQLException error){throw fail(error);}
    }
    synchronized void move(String source,String target,boolean replace) throws IOException {
      if(locked.stream().anyMatch(n->n.equals(source)||n.startsWith(source+"/")||n.equals(target)||n.startsWith(target+"/")))throw new IOException("SQLite document still in use");
      if(info(source)==null)throw new NoSuchFileException(source);
      if(info(target)!=null){if(!replace)throw new FileAlreadyExistsException(target);if(!children(target).isEmpty())throw new DirectoryNotEmptyException(target);}
      transaction(()->{
        try(var q=connection.prepareStatement("delete from documents where name=?")){q.setString(1,target);q.executeUpdate();}
        var names=new ArrayList<String>();names.add(source);String prefix=source+"/";
        try(var q=connection.prepareStatement("select name from documents where name>=? and name<?")){q.setString(1,prefix);q.setString(2,prefix.substring(0,prefix.length()-1)+"0");try(var r=q.executeQuery()){while(r.next())names.add(r.getString(1));}}
        try(var q=connection.prepareStatement("update documents set name=? where name=?")){for(String n:names){q.setString(1,target+n.substring(source.length()));q.setString(2,n);q.addBatch();}q.executeBatch();}
      });
    }
    IOException fail(Exception error){healthy=false;return io(error);}
    @Override public synchronized void close() throws IOException {healthy=false;try{connection.close();}catch(SQLException error){throw io(error);}finally{try{lease.release();}finally{guard.close();}}}
  }

  static IOException io(Exception e){return e instanceof IOException i?i:new IOException("SQLite operation failed; writes blocked",e);}
  static byte[] hash(byte[] data){try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(NoSuchAlgorithmException error){throw new AssertionError(error);}}
  static byte[] inflate(byte[] bytes)throws IOException{try(var in=new GZIPInputStream(new ByteArrayInputStream(bytes))){byte[] result=in.readNBytes(PAGE+1);if(result.length!=PAGE || in.read()!=-1)throw new IOException("Invalid compressed SQLite page");return result;}}

  public static FileChannel channel(Path path,OpenOption... options)throws IOException {
    var at=location(path);if(at==null)return FileChannel.open(path,options);return new PageChannel(db(at),at.name(),options);
  }
  static final class PageChannel extends FileChannel {
    final Database db;final String name;final boolean writable,directory;final Map<Long,byte[]> dirty=new HashMap<>();
    long position,length,originalLength;boolean truncated=false,leased=false;
    PageChannel(Database db,String name,OpenOption... options)throws IOException {
      this.db=db;this.name=name;var opts=new HashSet<OpenOption>(Arrays.asList(options));writable=opts.contains(StandardOpenOption.WRITE)||opts.contains(StandardOpenOption.APPEND);
      if(opts.contains(StandardOpenOption.CREATE_NEW))db.create(name,false,true);
      else if(opts.contains(StandardOpenOption.CREATE))db.create(name,false,false);
      var info=db.info(name);if(info==null)throw new NoSuchFileException(name);directory=info[1]==1;length=originalLength=info[0];
      if(directory&&writable)throw new IOException("Directory cannot be written");
      if(writable&&opts.contains(StandardOpenOption.TRUNCATE_EXISTING)){truncated=true;length=0;}
      if(opts.contains(StandardOpenOption.APPEND))position=length;
    }
    void check()throws IOException{if(!isOpen())throw new ClosedChannelException();db.check();}
    byte[] block(long page)throws IOException{byte[] b=dirty.get(page);if(b!=null)return b;if(truncated||page*PAGE>=originalLength)return new byte[PAGE];return db.page(name,page);}
    @Override public synchronized int read(ByteBuffer to)throws IOException{int n=read(to,position);if(n>0)position+=n;return n;}
    @Override public synchronized int read(ByteBuffer to,long at)throws IOException{check();if(at<0)throw new IllegalArgumentException();if(directory)throw new IOException("Directory read");if(!to.hasRemaining())return 0;if(at>=length)return -1;int n=(int)Math.min(to.remaining(),length-at),remaining=n;while(remaining>0){int offset=(int)(at%PAGE),take=Math.min(remaining,PAGE-offset);to.put(block(at/PAGE),offset,take);at+=take;remaining-=take;}return n;}
    @Override public synchronized int write(ByteBuffer from)throws IOException{int n=write(from,position);position+=n;return n;}
    @Override public synchronized int write(ByteBuffer from,long at)throws IOException{check();if(!writable)throw new NonWritableChannelException();if(at<0||at>length)throw new IOException("Sparse SQLite journal write refused");int n=from.remaining();while(from.hasRemaining()){long page=at/PAGE;byte[] b=dirty.get(page);if(b==null){b=block(page);dirty.put(page,b);}int offset=(int)(at%PAGE),take=Math.min(from.remaining(),PAGE-offset);from.get(b,offset,take);at+=take;}length=Math.max(length,at);return n;}
    @Override public synchronized long position()throws IOException{check();return position;}
    @Override public synchronized FileChannel position(long value)throws IOException{check();if(value<0)throw new IllegalArgumentException();position=value;return this;}
    @Override public synchronized long size()throws IOException{check();return length;}
    @Override public synchronized FileChannel truncate(long size)throws IOException{check();if(!writable)throw new NonWritableChannelException();if(size<0)throw new IllegalArgumentException();if(size<length){if(size!=0)throw new IOException("Partial journal truncation refused");dirty.clear();truncated=true;length=0;position=0;}return this;}
    @Override public synchronized void force(boolean metadata)throws IOException{check();if(!directory&&writable&&(!dirty.isEmpty()||truncated)){db.flush(name,length,dirty,truncated);dirty.clear();truncated=false;originalLength=length;}}
    @Override protected synchronized void implCloseChannel()throws IOException{try{if(!directory&&writable&&(!dirty.isEmpty()||truncated)&&db.healthy)db.flush(name,length,dirty,truncated);}finally{if(leased)synchronized(db){db.locked.remove(name);leased=false;}}}
    @Override public synchronized FileLock tryLock(long p,long s,boolean shared)throws IOException{check();if(p!=0||s!=Long.MAX_VALUE||shared)throw new IOException("Only exclusive whole-document locks are supported");synchronized(db){if(db.locked.contains(name))throw new OverlappingFileLockException();db.locked.add(name);leased=true;}
      return new FileLock(this,p,s,false){boolean valid=true;@Override public boolean isValid(){return valid&&PageChannel.this.isOpen();}@Override public void release(){synchronized(db){if(!valid)return;db.locked.remove(name);leased=false;valid=false;}}};}
    @Override public FileLock lock(long p,long s,boolean shared)throws IOException{return tryLock(p,s,shared);}
    @Override public long read(ByteBuffer[] b,int o,int l)throws IOException{long n=0;for(int i=o;i<o+l;i++){int r=read(b[i]);if(r<0)return n==0?-1:n;n+=r;}return n;}
    @Override public long write(ByteBuffer[] b,int o,int l)throws IOException{long n=0;for(int i=o;i<o+l;i++)n+=write(b[i]);return n;}
    @Override public long transferTo(long p,long count,WritableByteChannel target)throws IOException{var b=ByteBuffer.allocate(PAGE);long n=0;while(n<count){b.clear();b.limit((int)Math.min(PAGE,count-n));int r=read(b,p+n);if(r<0)break;b.flip();while(b.hasRemaining())target.write(b);n+=r;}return n;}
    @Override public long transferFrom(ReadableByteChannel source,long p,long count)throws IOException{var b=ByteBuffer.allocate(PAGE);long n=0;while(n<count){b.clear();b.limit((int)Math.min(PAGE,count-n));int r=source.read(b);if(r<0)break;if(r==0)break;b.flip();write(b,p+n);n+=r;}return n;}
    @Override public MappedByteBuffer map(MapMode mode,long p,long size)throws IOException{throw new IOException("Memory-mapped SQLite documents are unsupported");}
  }

  public static boolean exists(Path p,LinkOption... ignored){try{var l=location(p);return l==null?Files.exists(p,ignored):db(l).info(l.name())!=null;}catch(IOException e){throw new UncheckedIOException(e);}}
  public static boolean notExists(Path p,LinkOption... ignored){return !exists(p,ignored);}
  public static boolean isDirectory(Path p,LinkOption... ignored){try{var l=location(p);if(l==null)return Files.isDirectory(p,ignored);var i=db(l).info(l.name());return i!=null&&i[1]==1;}catch(IOException e){throw new UncheckedIOException(e);}}
  public static boolean isRegularFile(Path p,LinkOption... ignored){return exists(p,ignored)&&!isDirectory(p,ignored);}
  public static boolean isSymbolicLink(Path p){return Files.isSymbolicLink(p);}
  public static long size(Path p)throws IOException{var l=location(p);if(l==null)return Files.size(p);var i=db(l).info(l.name());if(i==null)throw new NoSuchFileException(p.toString());return i[0];}
  public static Path createDirectories(Path p,FileAttribute<?>... attrs)throws IOException{var l=location(p);if(l==null)return Files.createDirectories(p,attrs);String[] parts=l.name().split("/");String name="";for(String part:parts){name+=name.isEmpty()?part:"/"+part;db(l).create(name,true,false);}return p;}
  public static Path createDirectory(Path p,FileAttribute<?>... attrs)throws IOException{var l=location(p);if(l==null)return Files.createDirectory(p,attrs);db(l).create(l.name(),true,true);return p;}
  public static Path createTempFile(Path dir,String prefix,String suffix,FileAttribute<?>... attrs)throws IOException{if(location(dir)==null)return Files.createTempFile(dir,prefix,suffix,attrs);Path p=dir.resolve(prefix+UUID.randomUUID()+(suffix==null?".tmp":suffix));try(var c=channel(p,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){}return p;}
  public static InputStream newInputStream(Path p,OpenOption... options)throws IOException{return Channels.newInputStream(channel(p,options.length==0?new OpenOption[]{StandardOpenOption.READ}:options));}
  public static OutputStream newOutputStream(Path p,OpenOption... options)throws IOException{var requested=new LinkedHashSet<OpenOption>(Arrays.asList(options));if(requested.contains(StandardOpenOption.READ))throw new IllegalArgumentException("Output stream cannot use READ");requested.add(StandardOpenOption.WRITE);if(options.length==0){requested.add(StandardOpenOption.CREATE);requested.add(StandardOpenOption.TRUNCATE_EXISTING);}return Channels.newOutputStream(channel(p,requested.toArray(OpenOption[]::new)));}
  public static byte[] readAllBytes(Path p)throws IOException{long size=size(p);if(size>Integer.MAX_VALUE)throw new IOException("Document too large");try(var in=newInputStream(p)){byte[] result=in.readAllBytes();if(result.length!=size)throw new IOException("Document length changed");return result;}}
  public static String readString(Path p)throws IOException{return readString(p,StandardCharsets.UTF_8);}
  public static String readString(Path p,Charset charset)throws IOException{return charset.newDecoder().decode(ByteBuffer.wrap(readAllBytes(p))).toString();}
  public static BufferedReader newBufferedReader(Path p)throws IOException{return newBufferedReader(p,StandardCharsets.UTF_8);}
  public static BufferedReader newBufferedReader(Path p,Charset charset)throws IOException{return new BufferedReader(new InputStreamReader(newInputStream(p),charset.newDecoder()));}
  public static BufferedWriter newBufferedWriter(Path p,OpenOption... options)throws IOException{return newBufferedWriter(p,StandardCharsets.UTF_8,options);}
  public static BufferedWriter newBufferedWriter(Path p,Charset charset,OpenOption... options)throws IOException{return new BufferedWriter(new OutputStreamWriter(newOutputStream(p,options),charset));}
  public static Path write(Path p,byte[] data,OpenOption... options)throws IOException{try(var out=newOutputStream(p,options)){out.write(data);}return p;}
  public static Path writeString(Path p,CharSequence text,OpenOption... options)throws IOException{return write(p,text.toString().getBytes(StandardCharsets.UTF_8),options);}
  public static Path writeString(Path p,CharSequence text,Charset charset,OpenOption... options)throws IOException{return write(p,text.toString().getBytes(charset),options);}
  public static List<String> readAllLines(Path p,Charset charset)throws IOException{try(var r=newBufferedReader(p,charset)){var lines=new ArrayList<String>();String line;while((line=r.readLine())!=null)lines.add(line);return lines;}}
  public static Stream<Path> list(Path p)throws IOException{var l=location(p);if(l==null)return Files.list(p);if(!isDirectory(p))throw new NotDirectoryException(p.toString());return db(l).children(l.name()).stream().map(n->l.world().resolve(n));}
  public static boolean deleteIfExists(Path p)throws IOException{var l=location(p);if(l==null)return Files.deleteIfExists(p);if(db(l).info(l.name())==null)return false;db(l).remove(l.name());return true;}
  public static void delete(Path p)throws IOException{if(!deleteIfExists(p))throw new NoSuchFileException(p.toString());}
  public static Path move(Path source,Path target,CopyOption... options)throws IOException{var a=location(source);var b=location(target);if(a==null&&b==null)return Files.move(source,target,options);if(a==null||b==null||!a.database().equals(b.database()))throw new IOException("Cross-storage move refused");db(a).move(a.name(),b.name(),Arrays.asList(options).contains(StandardCopyOption.REPLACE_EXISTING));return target;}
  public static Path copy(Path source,Path target,CopyOption... options)throws IOException{if(location(source)==null&&location(target)==null)return Files.copy(source,target,options);if(exists(target)&&!Arrays.asList(options).contains(StandardCopyOption.REPLACE_EXISTING))throw new FileAlreadyExistsException(target.toString());try(var in=newInputStream(source);var out=newOutputStream(target)){in.transferTo(out);}return target;}

  public static String status(Path world)throws IOException {
    Path sample=world.resolve(switch(MOD){case "manager"->"data/zianmanager/claims.json";case "utilities"->"data/zianutilities/breeding_v1/journal.wal";case "rct"->"data/zianrct-rewards.json";default->"ziangts-v2/history-v2.wal";});
    var at=location(sample);if(at==null)return "mode=legacy";var store=db(at);
    synchronized(store){try(var s=store.connection.createStatement();var r=s.executeQuery("select count(*),coalesce(sum(size),0) from documents where kind=0")){r.next();return "mode=sqlite documents="+r.getLong(1)+" logicalBytes="+r.getLong(2)+" driver="+driver.getClass().getName();}catch(SQLException error){throw store.fail(error);}}
  }
  public static void touchOwner(Path world,UUID player)throws IOException {
    Path sample=world.resolve(switch(MOD){case "manager"->"data/zianmanager/claims.json";case "utilities"->"data/zianutilities/breeding_v1/journal.wal";case "rct"->"data/zianrct-rewards.json";default->"ziangts-v2/history-v2.wal";});
    var at=location(sample);if(at==null)return;var store=db(at);
    synchronized(store){try(var q=store.connection.prepareStatement("insert into owners values(?,?) on conflict(id) do update set last_seen=excluded.last_seen")){q.setString(1,player.toString());q.setLong(2,System.currentTimeMillis());q.executeUpdate();}catch(SQLException error){throw store.fail(error);}}
  }
  public static boolean wasRecorded(Path path,String scope,UUID id)throws IOException {
    var at=location(path);if(at==null)return false;var store=db(at);
    synchronized(store){try(var q=store.connection.prepareStatement("select 1 from receipts where scope=? and id=?")){q.setString(1,scope);q.setString(2,id.toString());try(var r=q.executeQuery()){return r.next();}}catch(SQLException error){throw store.fail(error);}}
  }
  public static void preserveReceipts(Path path,String scope,Collection<UUID> ids)throws IOException {
    var at=location(path);if(at==null)throw new IOException("SQLite receipts require the migrated store");var store=db(at);
    store.transaction(()->{try(var q=store.connection.prepareStatement("insert or ignore into receipts values(?,?)")){for(UUID id:ids){q.setString(1,scope);q.setString(2,id.toString());q.addBatch();}q.executeBatch();}});
  }
  public static boolean knownInactive(Path path,UUID owner,long cutoff)throws IOException {
    var at=location(path);if(at==null)return false;
    Path saved=at.world().resolve("playerdata").resolve(owner+".dat");
    // A recent autosave protects a long-lived session whose logout was lost in a crash.
    if(Files.isRegularFile(saved) && Files.getLastModifiedTime(saved).toMillis()>=cutoff)return false;
    var store=db(at);
    synchronized(store){try(var q=store.connection.prepareStatement("select last_seen from owners where id=?")){q.setString(1,owner.toString());try(var r=q.executeQuery()){return r.next()&&r.getLong(1)<cutoff;}}catch(SQLException error){throw store.fail(error);}}
  }


  public record RewardReceipt(UUID id,UUID player,String trainer,long cycle,long created,long cooldown,UUID battle) {}
  private static byte[] receiptHash(String scope,RewardReceipt r)throws IOException {
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeUTF(scope);out.writeUTF(r.id().toString());out.writeUTF(r.player().toString());out.writeUTF(r.trainer());out.writeLong(r.cycle());out.writeLong(r.created());out.writeLong(r.cooldown());out.writeUTF(r.battle()==null?"":r.battle().toString());out.flush();return hash(bytes.toByteArray());
    }
  }
  private static RewardReceipt receipt(String scope,ResultSet row)throws IOException,SQLException {
    var receipt=new RewardReceipt(UUID.fromString(row.getString("id")),UUID.fromString(row.getString("owner")),row.getString("source"),row.getLong("cycle"),row.getLong("created"),row.getLong("cooldown"),row.getString("battle")==null?null:UUID.fromString(row.getString("battle")));
    if(receipt.cycle()<0||receipt.created()<0||receipt.cooldown()<0||!MessageDigest.isEqual(receiptHash(scope,receipt),row.getBytes("sha")))throw new IOException("Retired reward receipt checksum failed");return receipt;
  }
  public static RewardReceipt retiredReward(Path path,UUID owner,String trainer,UUID id)throws IOException {
    var at=location(path);if(at==null)return null;var store=db(at);
    synchronized(store){String filter=id==null?"source=? order by cycle desc limit 1":"id=?";
      try(var q=store.connection.prepareStatement("select * from reward_receipts where scope=? "+(owner==null?"":"and owner=? ")+"and "+filter)){
        q.setString(1,at.name());int parameter=2;if(owner!=null)q.setString(parameter++,owner.toString());q.setString(parameter,id==null?trainer:id.toString());try(var r=q.executeQuery()){return r.next()?receipt(at.name(),r):null;}
      }catch(SQLException|IOException error){throw store.fail(error);}
    }
  }
  public static long retiredCycles(Path path,UUID owner,String trainer)throws IOException {
    var at=location(path);if(at==null)return 0;var store=db(at);
    synchronized(store){try(var q=store.connection.prepareStatement("select count(*) from reward_receipts where scope=? and owner=? and source=?")){q.setString(1,at.name());q.setString(2,owner.toString());q.setString(3,trainer);try(var r=q.executeQuery()){r.next();return r.getLong(1);}}catch(SQLException error){throw store.fail(error);}}
  }
  public static boolean retiredBattle(Path path,UUID owner,String trainer,UUID battle)throws IOException {
    var at=location(path);if(at==null||battle==null)return false;var store=db(at);
    synchronized(store){try(var q=store.connection.prepareStatement("select * from reward_receipts where scope=? and owner=? and source=? and battle=? limit 1")){q.setString(1,at.name());q.setString(2,owner.toString());q.setString(3,trainer);q.setString(4,battle.toString());try(var r=q.executeQuery()){if(!r.next())return false;receipt(at.name(),r);return true;}}catch(SQLException|IOException error){throw store.fail(error);}}
  }
  public static void retireRewards(Path path,Collection<RewardReceipt> receipts,byte[] replacement)throws IOException {
    var at=location(path);if(at==null)throw new IOException("Reward retirement requires SQLite");var store=db(at);
    store.transaction(()->{
      try(var q=store.connection.prepareStatement("insert into reward_receipts values(?,?,?,?,?,?,?,?,?)")){
        for(var r:receipts){q.setString(1,at.name());q.setString(2,r.id().toString());q.setString(3,r.player().toString());q.setString(4,r.trainer());q.setLong(5,r.cycle());q.setLong(6,r.created());q.setLong(7,r.cooldown());q.setString(8,r.battle()==null?null:r.battle().toString());q.setBytes(9,receiptHash(at.name(),r));q.addBatch();}q.executeBatch();
      }
      try(var q=store.connection.prepareStatement("delete from pages where name=?")){q.setString(1,at.name());q.executeUpdate();}
      try(var q=store.connection.prepareStatement("insert into pages values(?,?,?,?,0)")){
        for(int offset=0;offset<replacement.length;offset+=PAGE){byte[] block=new byte[PAGE];System.arraycopy(replacement,offset,block,0,Math.min(PAGE,replacement.length-offset));q.setString(1,at.name());q.setLong(2,offset/PAGE);q.setBytes(3,block);q.setBytes(4,hash(block));q.addBatch();}q.executeBatch();
      }
      try(var q=store.connection.prepareStatement("update documents set size=?,modified=? where name=?")){q.setLong(1,replacement.length);q.setLong(2,System.currentTimeMillis());q.setString(3,at.name());if(q.executeUpdate()!=1)throw new IOException("Reward document disappeared");}
    });
  }


  public static long modifiedMillis(Path path)throws IOException {
    var at=location(path);if(at==null)return Files.getLastModifiedTime(path).toMillis();var store=db(at);
    synchronized(store){try(var q=store.connection.prepareStatement("select modified from documents where name=?")){q.setString(1,at.name());try(var r=q.executeQuery()){if(!r.next())throw new NoSuchFileException(path.toString());return r.getLong(1);}}catch(SQLException error){throw store.fail(error);}}
  }
  public static List<Path> documents(Path world)throws IOException {
    Path sample=world.resolve(switch(MOD){case "manager"->"data/zianmanager/claims.json";case "utilities"->"data/zianutilities/breeding_v1/journal.wal";case "rct"->"data/zianrct-rewards.json";default->"ziangts-v2/history-v2.wal";});
    var at=location(sample);if(at==null)throw new IOException("SQLite is not enabled");var store=db(at);
    synchronized(store){try(var s=store.connection.createStatement();var r=s.executeQuery("select name from documents where kind=0 order by name")){var result=new ArrayList<Path>();while(r.next())result.add(at.world().resolve(r.getString(1)));return result;}catch(SQLException error){throw store.fail(error);}}
  }

  public static synchronized void closeAll()throws IOException{IOException failure=null;for(var d:DATABASES.values())try{d.close();}catch(IOException e){failure=e;}DATABASES.clear();if(failure!=null)throw failure;}
}
