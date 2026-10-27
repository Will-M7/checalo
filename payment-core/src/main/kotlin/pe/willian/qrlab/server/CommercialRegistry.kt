package pe.willian.qrlab.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pe.willian.qrlab.payment.SqlitePaymentRepository
import java.sql.Connection
import java.time.Instant
import java.util.UUID

@Serializable data class RegisterReceiver(val operationId:String,val actor:String,val id:String,val name:String,val phone:String,val qrSha256:String)
@Serializable data class RegisterDevice(val operationId:String,val actor:String,val id:String,val receiverId:String,val key:String,val mode:String,val owner:String?=null)
@Serializable data class ChangeDevice(val operationId:String,val actor:String,val enabled:Boolean)
@Serializable data class GrantRequest(val actor:String,val eventId:String)
@Serializable data class DeviceGrant(val token:String,val expiresAt:String)
@Serializable data class DeviceRegistrationView(val id:String,val receiverId:String,val mode:String,val enabled:Boolean)

/** Server-scoped provisioning; personal evidence also requires a fresh, event-bound session grant. */
class CommercialRegistry(private val repo:SqlitePaymentRepository,private val config:CommercialConfig,private val clock:()->Instant) {
    init { repo.atomic { db ->
        db.exec("CREATE TABLE IF NOT EXISTS canonical_receiver_catalog(id TEXT PRIMARY KEY,client_id TEXT NOT NULL,name TEXT NOT NULL,phone TEXT NOT NULL,qr_sha256 TEXT NOT NULL)")
        db.exec("CREATE TABLE IF NOT EXISTS canonical_device_authorizations(id TEXT PRIMARY KEY,client_id TEXT NOT NULL,receiver_id TEXT NOT NULL,key_hash TEXT UNIQUE NOT NULL,mode TEXT NOT NULL,owner TEXT,enabled INTEGER NOT NULL)")
        db.exec("CREATE TABLE IF NOT EXISTS canonical_device_grants(token_hash TEXT PRIMARY KEY,device_id TEXT NOT NULL,event_id TEXT NOT NULL,expires_at INTEGER NOT NULL,used INTEGER NOT NULL DEFAULT 0)")
        db.exec("CREATE TABLE IF NOT EXISTS canonical_registry_audit(operation_id TEXT PRIMARY KEY,client_id TEXT NOT NULL,actor TEXT NOT NULL,action TEXT NOT NULL,subject TEXT NOT NULL,payload_hash TEXT NOT NULL,created_at TEXT NOT NULL)")
    } }
    fun receivers(db:Connection):List<PaymentReceiver> = config.receivers + db.rows("SELECT * FROM canonical_receiver_catalog").filter { x -> config.receivers.none { it.id==x["id"] } }.map { PaymentReceiver(it["id"]!!,it["name"]!!,it["phone"]!!,it["qr_sha256"]!!) }
    fun receiver(db:Connection,id:String)=receivers(db).singleOrNull { it.id==id } ?: throw NoSuchElementException()
    fun allowed(db:Connection,c:PaymentClient,id:String)=id in c.receivers || db.rows("SELECT id FROM canonical_receiver_catalog WHERE id=? AND client_id=?",id,c.id).isNotEmpty()
    fun devices(db:Connection):List<PaymentDevice> {
        val rows=db.rows("SELECT * FROM canonical_device_authorizations")
        return config.devices.filter { d -> rows.none { it["id"]==d.id } } + rows.map { PaymentDevice(it["id"]!!,"",it["receiver_id"]!!) }
    }
    fun enabled(db:Connection,id:String):Boolean {
        val row=db.rows("SELECT enabled FROM canonical_device_authorizations WHERE id=?",id).singleOrNull()
        if(row!=null)return row["enabled"]=="1"
        val legacy=config.devices.singleOrNull { it.id==id } ?: return false
        return db.rows("SELECT id FROM canonical_device_authorizations WHERE receiver_id=?",legacy.receiverId).isEmpty()
    }
    fun list(c:PaymentClient):List<DeviceRegistrationView> {
        if(!c.receiverAdministration || config.client(c.key)!=c) throw ReceiverAdminDenied()
        return repo.atomic { db -> devices(db).filter { allowed(db,c,it.receiverId) }.map { d ->
            val row=db.rows("SELECT mode FROM canonical_device_authorizations WHERE id=?",d.id).singleOrNull()
            DeviceRegistrationView(d.id,d.receiverId,row?.get("mode") ?: "legacy",enabled(db,d.id))
        } }
    }
    fun device(token:String):PaymentDevice?=repo.atomic { db ->
        val r=db.rows("SELECT * FROM canonical_device_authorizations WHERE key_hash=?",CanonicalPayments.sha256(token)).singleOrNull()
        if(r!=null) return@atomic if(r["enabled"]=="1") PaymentDevice(r["id"]!!,token,r["receiver_id"]!!) else null
        config.device(token)?.takeIf { enabled(db,it.id) && db.rows("SELECT id FROM canonical_device_authorizations WHERE id=?",it.id).isEmpty() }
    }
    private fun admin(c:PaymentClient,actor:String) {
        if(!c.receiverAdministration || config.client(c.key)!=c) throw ReceiverAdminDenied()
        require(actor.matches(Regex("staff_[1-9][0-9]{0,18}")))
    }
    private fun repeated(db:Connection,c:PaymentClient,op:String,digest:String):Boolean {
        require(op.matches(Regex("[a-zA-Z0-9_-]{16,100}")))
        val old=db.rows("SELECT * FROM canonical_registry_audit WHERE operation_id=?",op).singleOrNull() ?: return false
        if(old["client_id"]!=c.id || old["payload_hash"]!=digest) throw ReceiverAdminConflict("OPERATION_CONFLICT")
        return true
    }
    private fun audit(db:Connection,c:PaymentClient,op:String,actor:String,action:String,subject:String,digest:String) { db.exec("INSERT INTO canonical_registry_audit VALUES(?,?,?,?,?,?,?)",op,c.id,actor,action,subject,digest,clock().toString()) }
    fun registerReceiver(c:PaymentClient,r:RegisterReceiver):ReceiverView {
        admin(c,r.actor)
        require(r.id.matches(Regex("[a-zA-Z0-9_-]{1,100}")) && r.name.isNotBlank() && r.name.length<=100 && r.phone.matches(Regex("519[0-9]{8}")) && r.qrSha256.matches(Regex("[a-f0-9]{64}")))
        return repo.atomic { db ->
            val digest=CanonicalPayments.sha256(Json.encodeToString(r));val old=receivers(db).singleOrNull { it.id==r.id }
            if(old!=null && !allowed(db,c,r.id)) throw ReceiverAdminDenied()
            if(!repeated(db,c,r.operationId,digest)) {
                if(old!=null) {
                    if(old.name!=r.name || old.phone.takeLast(9)!=r.phone.takeLast(9) || old.qrSha256!=r.qrSha256) throw ReceiverAdminConflict("REGISTERED_IDENTITY_MISMATCH")
                } else {
                    require(receivers(db).none { it.phone.takeLast(9)==r.phone.takeLast(9) })
                    db.exec("INSERT INTO canonical_receiver_catalog VALUES(?,?,?,?,?)",r.id,c.id,r.name,r.phone,r.qrSha256)
                    db.exec("INSERT INTO canonical_receiver_state VALUES(?,?,0,0,0,NULL,NULL)",r.id,CanonicalPayments.sha256("${r.name}|${r.phone}|${r.qrSha256}"))
                }
                audit(db,c,r.operationId,r.actor,"register_receiver",r.id,digest)
            }
            val s=db.rows("SELECT * FROM canonical_receiver_state WHERE receiver_id=?",r.id).single()
            ReceiverView(r.id,r.name,r.phone,r.qrSha256,s["verified"]=="1",s["enabled"]=="1",false,s["version"]!!.toLong())
        }
    }
    fun registerDevice(c:PaymentClient,r:RegisterDevice):DeviceRegistrationView {
        admin(c,r.actor)
        require(r.id.matches(Regex("[a-zA-Z0-9_-]{1,100}")) && r.key.length in 32..512 && r.mode in listOf("personal","shared","direct"))
        require(r.mode!="personal" || r.owner?.matches(Regex("staff_[1-9][0-9]{0,18}"))==true)
        return repo.atomic { db ->
            if(!allowed(db,c,r.receiverId)) throw ReceiverAdminDenied();receiver(db,r.receiverId)
            val digest=CanonicalPayments.sha256(Json.encodeToString(r))
            val old=db.rows("SELECT * FROM canonical_device_authorizations WHERE id=?",r.id).singleOrNull();val configured=config.devices.singleOrNull { it.id==r.id }
            if(old!=null && (old["client_id"]!=c.id || old["receiver_id"]!=r.receiverId)) throw ReceiverAdminDenied()
            if(configured!=null && configured.receiverId!=r.receiverId) throw ReceiverAdminDenied()
            require(config.clients.none { it.key==r.key } && config.devices.none { it.id!=r.id && it.key==r.key })
            if(!repeated(db,c,r.operationId,digest)) {
                db.exec("INSERT INTO canonical_device_authorizations VALUES(?,?,?,?,?,?,1) ON CONFLICT(id) DO UPDATE SET key_hash=excluded.key_hash,mode=excluded.mode,owner=excluded.owner,enabled=1",r.id,c.id,r.receiverId,CanonicalPayments.sha256(r.key),r.mode,r.owner)
                db.exec("UPDATE canonical_devices SET listener=0,seen=0 WHERE id=?",r.id)
                audit(db,c,r.operationId,r.actor,"register_device",r.id,digest)
            }
            DeviceRegistrationView(r.id,r.receiverId,r.mode,enabled(db,r.id))
        }
    }
    fun changeDevice(c:PaymentClient,id:String,r:ChangeDevice):DeviceRegistrationView {
        admin(c,r.actor)
        return repo.atomic { db ->
            val old=db.rows("SELECT * FROM canonical_device_authorizations WHERE id=?",id).singleOrNull();val configured=config.devices.singleOrNull { it.id==id }
            val receiver=old?.get("receiver_id") ?: configured?.receiverId ?: throw NoSuchElementException()
            if(!allowed(db,c,receiver) || (old!=null && old["client_id"]!=c.id)) throw ReceiverAdminDenied()
            val digest=CanonicalPayments.sha256(Json.encodeToString(r)+id)
            if(!repeated(db,c,r.operationId,digest)) {
                if(old==null) db.exec("INSERT INTO canonical_device_authorizations VALUES(?,?,?,?,?,?,?)",id,c.id,receiver,CanonicalPayments.sha256(configured!!.key),"direct",null,if(r.enabled) 1 else 0)
                else db.exec("UPDATE canonical_device_authorizations SET enabled=? WHERE id=?",if(r.enabled) 1 else 0,id)
                if(!r.enabled) db.exec("UPDATE canonical_device_grants SET used=1 WHERE device_id=?",id)
                if(!r.enabled) db.exec("UPDATE canonical_evidence SET result='REVIEW_DEVICE_SUSPENDED' WHERE device_id=? AND payment_id IS NULL",id)
                audit(db,c,r.operationId,r.actor,if(r.enabled) "enable_device" else "suspend_device",id,digest)
            }
            DeviceRegistrationView(id,receiver,old?.get("mode") ?: "direct",enabled(db,id))
        }
    }
    fun grant(c:PaymentClient,id:String,r:GrantRequest):DeviceGrant {
        admin(c,r.actor);require(r.eventId.matches(Regex("[a-zA-Z0-9_-]{16,128}")))
        return repo.atomic { db ->
            val d=db.rows("SELECT * FROM canonical_device_authorizations WHERE id=?",id).singleOrNull() ?: throw ReceiverAdminDenied()
            if(d["client_id"]!=c.id || d["enabled"]!="1" || d["mode"]!="personal" || d["owner"]!=r.actor) throw ReceiverAdminDenied()
            val token=UUID.randomUUID().toString()+UUID.randomUUID().toString();val until=clock().plusSeconds(30)
            db.exec("INSERT INTO canonical_device_grants VALUES(?,?,?,?,0)",CanonicalPayments.sha256(token),id,r.eventId,until.epochSecond)
            DeviceGrant(token,until.toString())
        }
    }
    fun consumeGrant(db:Connection,d:PaymentDevice,raw:CanonicalRaw) {
        val row=db.rows("SELECT * FROM canonical_device_authorizations WHERE id=?",d.id).singleOrNull() ?: return
        if(row["enabled"]!="1") throw ReceiverAdminDenied()
        if(row["mode"]!="personal") return
        val grant=raw.authorizationGrant?.let { db.rows("SELECT * FROM canonical_device_grants WHERE token_hash=? AND device_id=? AND event_id=? AND used=0 AND expires_at>=?",CanonicalPayments.sha256(it),d.id,raw.bridgeEventId,clock().epochSecond).singleOrNull() }
        if(grant==null) throw ReceiverAdminDenied()
        db.exec("UPDATE canonical_device_grants SET used=1 WHERE token_hash=?",grant["token_hash"])
    }
}
