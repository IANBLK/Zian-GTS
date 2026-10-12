package com.zianblk.ziangts.storage;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Offline migration publishes its marker last; originals stay untouched until operator validation. */
public final class StorageMaintenance {
  private StorageMaintenance() {}
  public record Migration(int files,long bytes,Path database) {}
  public record Cleanup(long compressedPages,long protectedTemporaryDocuments,long retiredHistoryArchives) {}

  private static Path normalizedWorld(Path input) throws IOException {
    Path world=input.toAbsolutePath().normalize();
    if(!Files.isDirectory(world)||Files.isSymbolicLink(world))throw new IOException("Invalid world directory");
    return world;
  }
  private static FileChannel offline(Path world)throws IOException {
    FileChannel session=FileChannel.open(world.resolve("session.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
    try{if(session.tryLock()==null)throw new IOException("Stop the Minecraft server before maintenance");return session;}
    catch(Exception failure){session.close();throw StorageFiles.io(failure);}
  }
  static List<Path> roots(Path world)throws IOException {
    var roots=new ArrayList<Path>();Path data=world.resolve("data");
    if(StorageFiles.MOD.equals("gts")){if(Files.exists(world.resolve("ziangts-v2")))roots.add(world.resolve("ziangts-v2"));}
    else if(StorageFiles.MOD.equals("rct")){
      if(Files.isDirectory(data))try(var listing=Files.list(data)){roots.addAll(listing.filter(p->p.getFileName().toString().startsWith("zianrct-")).toList());}
    }else{
      Path root=data.resolve("zian"+StorageFiles.MOD);if(Files.exists(root))roots.add(root);
    }
    return roots;
  }
  public static Migration migrateOffline(Path input)throws IOException {
    Path world=normalizedWorld(input),dir=world.resolve("zian-storage");Files.createDirectories(dir);
    Path marker=dir.resolve("storage.properties"),target=dir.resolve(StorageFiles.MOD+".db"),maintenance=dir.resolve(StorageFiles.MOD+".maintenance");
    if(Files.exists(target)||Files.exists(maintenance))throw new IOException("Existing database or interrupted maintenance requires review");
    Properties settings=new Properties();if(Files.exists(marker))try(var r=Files.newBufferedReader(marker)){settings.load(r);}
    if(settings.containsKey(StorageFiles.MOD))throw new IOException("Storage already configured");
    Path stage=dir.resolve(StorageFiles.MOD+".db.stage-"+UUID.randomUUID());int files=0;long bytes=0;boolean published=false,ownsMaintenance=false;
    try(var session=offline(world)){
      Files.writeString(maintenance,"SQLite import; originals preserved; stage="+stage.getFileName(),StandardOpenOption.CREATE_NEW);ownsMaintenance=true;force(maintenance);
      try(var database=new StorageFiles.Database(stage,true)){
        for(Path root:roots(world)){
          List<Path> paths;try(var walk=Files.walk(root)){paths=walk.toList();}
          for(Path source:paths){
            if(Files.isSymbolicLink(source))throw new IOException("Symlink import refused");
            String name=world.relativize(source).toString().replace('\\','/');if(!StorageFiles.owned(name))continue;
            if(name.endsWith(".maintenance")||name.contains(".staging")||name.endsWith(".tmp"))throw new IOException("Unfinished legacy maintenance requires review");
            if(Files.isDirectory(source)){database.create(name,true,false);continue;}
            if(!Files.isRegularFile(source))throw new IOException("Unsupported storage entry");
            database.create(name,false,true);long total=0;
            MessageDigest before=digest(),after=digest();
            try(var in=Files.newInputStream(source);var out=new StorageFiles.PageChannel(database,name,StandardOpenOption.WRITE)){
              byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){before.update(buffer,0,n);out.write(ByteBuffer.wrap(buffer,0,n));out.force(true);total+=n;}
            }
            try(var check=new StorageFiles.PageChannel(database,name,StandardOpenOption.READ)){
              var buffer=ByteBuffer.allocate(65536);int n;while((n=check.read(buffer))!=-1){after.update(buffer.array(),0,n);buffer.clear();}
              if(check.size()!=Files.size(source)||!MessageDigest.isEqual(before.digest(),after.digest()))throw new IOException("SQLite import byte mismatch");
            }
            try(var q=database.connection.prepareStatement("update documents set modified=? where name=?")){q.setLong(1,Files.getLastModifiedTime(source).toMillis());q.setString(2,name);q.executeUpdate();}catch(SQLException e){throw StorageFiles.io(e);}
            files++;bytes+=total;
          }
        }
        Path players=world.resolve("playerdata");
        if(Files.isDirectory(players))try(var listing=Files.list(players)){
          for(Path player:listing.toList()){
            String filename=player.getFileName().toString();if(!filename.endsWith(".dat")||Files.isSymbolicLink(player))continue;
            UUID owner;try{owner=UUID.fromString(filename.substring(0,filename.length()-4));}catch(IllegalArgumentException invalid){continue;}
            try(var q=database.connection.prepareStatement("insert into owners values(?,?)")){q.setString(1,owner.toString());q.setLong(2,Files.getLastModifiedTime(player).toMillis());q.executeUpdate();}catch(SQLException error){throw StorageFiles.io(error);}
          }
        }
      }
      force(stage);Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE);forceDirectory(dir);
      settings.setProperty(StorageFiles.MOD,"sqlite-v1");
      settings.putIfAbsent("history-days","90");settings.putIfAbsent("inactive-days","30");settings.putIfAbsent("compress-cold-data","true");
      Path temporary=Files.createTempFile(dir,"storage-properties-",".tmp");
      try{try(var w=Files.newBufferedWriter(temporary)){settings.store(w,"Zian storage; maintenance is offline and configurable");}force(temporary);Files.move(temporary,marker,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);forceDirectory(dir);}finally{Files.deleteIfExists(temporary);}
      published=true;
    }finally{
      // Normal failures leave the full originals and an unpublished DB for manual inspection.
      // An abrupt process death retains the marker, preventing a partially published startup.
      if(ownsMaintenance&&(published||!Files.exists(marker)||!"sqlite-v1".equals(load(marker).getProperty(StorageFiles.MOD)))){Files.deleteIfExists(maintenance);forceDirectory(dir);}
    }
    return new Migration(files,bytes,target);
  }
  private static Properties load(Path p)throws IOException{Properties v=new Properties();if(Files.exists(p))try(var r=Files.newBufferedReader(p)){v.load(r);}return v;}
  private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
  private static void force(Path p)throws IOException{try(var c=FileChannel.open(p,StandardOpenOption.WRITE)){c.force(true);}}
  private static void forceDirectory(Path p)throws IOException{if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows"))try(var c=FileChannel.open(p,StandardOpenOption.READ)){c.force(true);}}

  public static Cleanup cleanupOffline(Path input,boolean apply)throws IOException {
    Path world=normalizedWorld(input),dir=world.resolve("zian-storage"),file=dir.resolve(StorageFiles.MOD+".db");
    Properties config=load(dir.resolve("storage.properties"));if(!"sqlite-v1".equals(config.getProperty(StorageFiles.MOD)))throw new IOException("SQLite storage is not enabled");
    int days=Integer.parseInt(config.getProperty(StorageFiles.MOD+".history-days",config.getProperty("history-days","90")));if(days<7||days>3650)throw new IOException("Invalid retention days (7..3650)");
    long cutoff=System.currentTimeMillis()-days*86400000L;
    try(var session=offline(world);var db=new StorageFiles.Database(file)){
      long pages=0,temporary=0,archives=0;try(var q=db.connection.prepareStatement("select count(*) from pages p join documents d using(name) where d.modified<? and p.encoding=0")){q.setLong(1,cutoff);try(var r=q.executeQuery()){r.next();pages=r.getLong(1);}}
      try(var q=db.connection.prepareStatement("select count(*) from documents where name like '%.tmp' and modified<?")){q.setLong(1,cutoff);try(var r=q.executeQuery()){r.next();temporary=r.getLong(1);}}
      if(apply){
        if(Boolean.parseBoolean(config.getProperty("compress-cold-data","true"))){
          var items=new ArrayList<Map.Entry<String,Long>>();try(var q=db.connection.prepareStatement("select p.name,p.page from pages p join documents d using(name) where d.modified<? and p.encoding=0")){q.setLong(1,cutoff);try(var r=q.executeQuery()){while(r.next())items.add(Map.entry(r.getString(1),r.getLong(2)));}}
          for(var entry:items){byte[] raw=db.page(entry.getKey(),entry.getValue()),packed;try(var out=new ByteArrayOutputStream()){try(var gzip=new GZIPOutputStream(out)){gzip.write(raw);}packed=out.toByteArray();}if(packed.length>=raw.length)continue;
            if(!Arrays.equals(raw,StorageFiles.inflate(packed)))throw new IOException("Compression round-trip failed");
            byte[] encoded=packed;db.transaction(()->{try(var q=db.connection.prepareStatement("update pages set body=?,encoding=1 where name=? and page=?")){q.setBytes(1,encoded);q.setString(2,entry.getKey());q.setLong(3,entry.getValue());q.executeUpdate();}});
          }
        }
        // A failed post-effect rename may leave valuable delivery evidence in a temp snapshot.
        // Never delete it merely because it is old.
        if(StorageFiles.MOD.equals("gts")){
          var candidates=new ArrayList<String>();
          try(var q=db.connection.prepareStatement("select name from documents where modified<? and name like 'ziangts-v2/history-retention.archive-%.wal.gz'")){q.setLong(1,cutoff);try(var r=q.executeQuery()){while(r.next())candidates.add(r.getString(1));}}
          for(String name:candidates)if(archiveReceiptsVerified(db,name)){db.remove(name);archives++;}
        }
        try(var s=db.connection.createStatement()){s.execute("pragma wal_checkpoint(TRUNCATE)");s.execute("VACUUM");}catch(SQLException e){throw StorageFiles.io(e);}
      }
      return new Cleanup(pages,temporary,archives);
    }catch(SQLException e){throw StorageFiles.io(e);}
  }

  private static boolean archiveReceiptsVerified(StorageFiles.Database db,String name)throws IOException {
    String token=name.substring("ziangts-v2/history-retention.archive-".length(),name.length()-".wal.gz".length());
    try{UUID.fromString(token);}catch(IllegalArgumentException bad){return false;}
    var idPattern=java.util.regex.Pattern.compile("\"operationId\"\\s*:\\s*\"([0-9a-f-]{36})\"");
    byte[] previous=new byte[32];long records=0;
    try(var channel=new StorageFiles.PageChannel(db,name,StandardOpenOption.READ);var in=new java.util.zip.GZIPInputStream(Channels.newInputStream(channel));var q=db.connection.prepareStatement("select 1 from receipts where scope='gts-history' and id=?")){
      while(true){int first=in.read();if(first<0)break;byte[] header=in.readNBytes(3);if(header.length!=3)throw new IOException("History archive truncated");int length=(first<<24)|((header[0]&255)<<16)|((header[1]&255)<<8)|(header[2]&255);if(length<1||length>1048576)throw new IOException("Invalid history archive frame");
        byte[] payload=in.readNBytes(length),stored=in.readNBytes(32);if(payload.length!=length||stored.length!=32)throw new IOException("History archive truncated");
        MessageDigest hash=digest();hash.update(previous);byte[] expected=hash.digest(payload);if(!MessageDigest.isEqual(expected,stored))throw new IOException("History archive checksum");previous=stored;
        var match=idPattern.matcher(new String(payload,java.nio.charset.StandardCharsets.UTF_8));if(!match.find())return false;String id=match.group(1);if(match.find())return false;UUID.fromString(id);q.setString(1,id);try(var r=q.executeQuery()){if(!r.next())return false;}records++;
      }
      return records>0;
    }catch(SQLException error){throw StorageFiles.io(error);}
  }

  public static String inspectOffline(Path input)throws IOException {
    Path world=normalizedWorld(input);try(var session=offline(world);var db=new StorageFiles.Database(world.resolve("zian-storage/"+StorageFiles.MOD+".db"));var s=db.connection.createStatement();var r=s.executeQuery("select count(*),coalesce(sum(size),0) from documents where kind=0")){r.next();return "mod="+StorageFiles.MOD+" documents="+r.getLong(1)+" logicalBytes="+r.getLong(2)+" integrity=ok";}catch(SQLException e){throw StorageFiles.io(e);}
  }
  public static void main(String[] args)throws Exception {
    if(args.length!=2)throw new IllegalArgumentException("Usage: migrate-offline|inspect-offline|cleanup-preview|cleanup-offline <world>; stop server first");
    Path world=Path.of(args[1]);switch(args[0]){
      case "migrate-offline"->System.out.println(migrateOffline(world));
      case "inspect-offline"->System.out.println(inspectOffline(world));
      case "cleanup-preview"->System.out.println(cleanupOffline(world,false));
      case "cleanup-offline"->System.out.println(cleanupOffline(world,true));
      default->throw new IllegalArgumentException("Unknown maintenance operation");
    }
  }
}
