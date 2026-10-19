package com.zianblk.ziangts.v2.persistence;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

/** Cosmetic stable references. Internal journals and owner UUID authorization remain unchanged. */
public final class HumanReferences {
 public record Entry(UUID id,UUID owner,String reference,String ownerName){}
 private record State(int schema,long sequence,Map<String,Entry> entries){}
 private final Path path;private final String family;private State state;
 public HumanReferences(Path path,String family)throws IOException{
  if(!family.matches("[A-Z]{2,16}"))throw new IllegalArgumentException("Familia inválida");
  this.path=path;this.family=family;
  state=Files.exists(path)?new Gson().fromJson(Files.readString(path,StandardCharsets.UTF_8),State.class):new State(1,0,Map.of());
  if(state==null||state.schema()!=1||state.sequence()<0||state.entries()==null)throw new IOException("Índice de referencias inválido; archivo conservado");
  var seen=new HashSet<String>();for(var row:state.entries().entrySet()){var e=row.getValue();if(e==null||e.id()==null||e.owner()==null||e.reference()==null||!row.getKey().equals(e.id().toString())||!e.reference().matches("[A-Z]{2,16}-[A-Z0-9_]{1,24}-[0-9]{4,20}")||!e.reference().startsWith(family+"-")||Long.parseLong(e.reference().substring(e.reference().lastIndexOf('-')+1))>state.sequence()||!seen.add(e.reference()))throw new IOException("Referencia duplicada o inválida");}
 }
 public synchronized String reference(UUID id,UUID owner,String ownerName){
  Objects.requireNonNull(id);Objects.requireNonNull(owner);
  var old=state.entries().get(id.toString());if(old!=null){if(!old.owner().equals(owner))throw new IllegalArgumentException("La referencia no corresponde al propietario");return old.reference();}
  String name=ownerName==null?"JUGADOR":ownerName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");if(name.isBlank())name="JUGADOR";if(name.length()>24)name=name.substring(0,24);
  long sequence=Math.addExact(state.sequence(),1);String ref=family+"-"+name+"-"+String.format(Locale.ROOT,"%04d",sequence);
  var entries=new LinkedHashMap<>(state.entries());entries.put(id.toString(),new Entry(id,owner,ref,name));var next=new State(1,sequence,Map.copyOf(entries));
  try{save(next);}catch(IOException error){throw new IllegalStateException("No se pudo guardar la referencia; el registro original se conserva",error);}state=next;return ref;
 }
 public synchronized UUID resolve(String value,UUID owner){
  try{return UUID.fromString(value);}catch(IllegalArgumentException ignored){}
  return state.entries().values().stream().filter(e->e.reference().equalsIgnoreCase(value)&&(owner==null||e.owner().equals(owner))).map(Entry::id).findFirst().orElseThrow(()->new IllegalArgumentException("Referencia inexistente o ajena al jugador"));
 }
 public synchronized Entry entry(UUID id){return state.entries().get(id.toString());}
 private void save(State next)throws IOException{
  Files.createDirectories(path.toAbsolutePath().getParent());Path tmp=Files.createTempFile(path.toAbsolutePath().getParent(),".references-",".tmp");
  try{byte[] bytes=new GsonBuilder().setPrettyPrinting().create().toJson(next).getBytes(StandardCharsets.UTF_8);try(var ch=FileChannel.open(tmp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){var b=java.nio.ByteBuffer.wrap(bytes);while(b.hasRemaining())ch.write(b);ch.force(true);}Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}
 }
}
