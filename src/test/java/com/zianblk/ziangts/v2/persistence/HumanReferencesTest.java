package com.zianblk.ziangts.v2.persistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class HumanReferencesTest {
 @TempDir Path temp;
 @Test void stableOwnerBoundAliasesSurviveRestartAndLegacyUuidsRemainValid()throws Exception{
  var path=temp.resolve("refs.json");var refs=new HumanReferences(path,"GTS");var owner=UUID.randomUUID();var id=UUID.randomUUID();
  String code=refs.reference(id,owner,"IANBLK");assertEquals("GTS-IANBLK-0001",code);assertEquals(id,new HumanReferences(path,"GTS").resolve(code,owner));assertEquals(code,refs.reference(id,owner,"NEWNAME"));assertEquals(id,refs.resolve(id.toString(),owner));assertThrows(IllegalArgumentException.class,()->refs.resolve(code,UUID.randomUUID()));assertThrows(IllegalArgumentException.class,()->refs.reference(id,UUID.randomUUID(),"IANBLK"));assertNotEquals(code,refs.reference(UUID.randomUUID(),owner,"IANBLK"));
 }
 @Test void unreadableIndexIsNotOverwritten()throws Exception{var path=temp.resolve("refs.json");java.nio.file.Files.writeString(path,"{}");assertThrows(java.io.IOException.class,()->new HumanReferences(path,"GTS"));assertEquals("{}",java.nio.file.Files.readString(path));}
}
