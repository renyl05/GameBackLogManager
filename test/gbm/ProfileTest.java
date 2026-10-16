package gbm;
import java.nio.file.*;import java.util.*;
public class ProfileTest {
 static void check(boolean v,String message){IntegrationTest.check(v,message);}
 public static void main(String[] args)throws Exception{
  Path file=Files.createTempDirectory("gbm-profile").resolve("state.json");Properties cfg=new Properties();cfg.setProperty("GBM_TEST_ISOLATED","true");String id;
  try(Server server=new Server(file,0,cfg)){server.start();Api owner=new Api("http://127.0.0.1:"+server.port());Map<String,Object> account=owner.call("register",Json.obj("email","profile@example.com","username","profileOwner","password","Profile-test-123"));owner.token=Json.str(account,"token");id=Json.str(Json.map(account.get("user")),"id");
   owner.call("profile-update",Json.obj("username","profileOwner","age","23","gender","Uomo","origin","Salerno","bio","Giochi e musica"));
   owner.call("entry",Json.obj("game","local:minecraft","favorite",true,"rating",9));
   owner.call("list-create",Json.obj("title","Lista privata","games",List.of("local:minecraft"),"public",false));owner.call("list-create",Json.obj("title","Lista pubblica","games",List.of("local:minecraft"),"public",true));
   Api visitor=new Api(owner.base);visitor.token=Json.str(visitor.call("register",Json.obj("email","visitor@example.com","username","profileVisitor","password","Visitor-test-123")),"token");
   Map<String,Object> own=owner.call("profile",Json.obj("user",id)),other=visitor.call("profile",Json.obj("user",id));check(Json.list(own.get("lists")).size()==2,"owner sees private and public lists");check(Json.list(other.get("lists")).size()==1,"visitor only sees public lists");check(Json.list(other.get("entries")).isEmpty(),"private favorites and ownership stay private");check(Json.str(Json.map(other.get("user")),"bio").equals("Giochi e musica"),"public bio available on profile");check(!Json.map(other.get("user")).containsKey("email")&&!Json.map(other.get("user")).containsKey("hash"),"profile excludes credentials");
   IntegrationTest.denied(owner,"profile-update",Json.obj("username","profileOwner","age","-5","bio","invalid change"),"invalid age rejected");check(Json.str(Json.map(owner.call("profile",Json.obj("user",id)).get("user")),"bio").equals("Giochi e musica"),"invalid profile does not partially change bio");
   owner.call("profile-update",Json.obj("username","renamedOwner"));check(Json.str(Json.map(owner.call("profile",Json.obj("user",id)).get("user")),"age").equals("23"),"username-only update preserves profile fields");
   owner.call("settings",Json.obj("visibility","public"));check(Json.list(visitor.call("profile",Json.obj("user",id)).get("entries")).size()==1,"public favorites include games outside library");
   visitor.call("profile-update",Json.obj("username","profileVisitor","user",id,"bio","Visitor bio"));check(Json.str(Json.map(owner.call("profile",Json.obj("user",id)).get("user")),"bio").equals("Giochi e musica"),"profile update cannot impersonate another user");
  }
  Store restored=new Store(file,cfg);check(Json.str(restored.find("users","id",id),"origin").equals("Salerno"),"profile survives restart");System.out.println("PASS profile validation, privacy and persistence");
 }
}
