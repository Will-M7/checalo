<?php
use App\Enums\Rol;
use App\Models\{ReceptorPago, User, MovimientoFinanciero};
use App\Services\Payments\HuataYape;
use Illuminate\Support\Facades\{DB, Http, Storage, Crypt};
use Illuminate\Support\Str;
use Livewire\Livewire;

function huataYapeFixture(bool $legacy = false): array {
    config(['payments.enabled' => true, 'payments.url' => 'http://127.0.0.1:8087', 'payments.key' => str_repeat('x',40), 'payments.allow_local_http' => true]);
    Storage::fake('public'); Storage::disk('public')->put('qr/huata.png','qr-test-integrado');
    $admin = User::factory()->rol(Rol::Administrador)->create();
    $owner = User::factory()->rol(Rol::Vendedor)->create();
    $other = User::factory()->rol(Rol::Vendedor)->create();
    $r = ReceptorPago::create(['user_id'=>$owner->id,'nombre_receptor'=>'Receptor aislado','celular'=>'51999999999','whatsapp'=>'51999999999','referencia_externa'=>'huata-test-receiver','qr_path'=>'qr/huata.png','qr_sha256'=>hash('sha256','qr-test-integrado')]);
    Http::preventStrayRequests();
    Http::fake(function ($req) use ($legacy,$r) {
        if (str_ends_with($req->url(),'/health')) return Http::response(['mode'=>'commercial']);
        if ($req->method()==='GET' && str_ends_with($req->url(),'/api/admin/devices')) return Http::response($legacy ? [['id'=>'bridge-anterior','receiverId'=>$r->referencia_externa,'mode'=>'legacy','enabled'=>true]] : []);
        if (str_ends_with($req->url(),'/grant')) return Http::response(['token'=>str_repeat('g',64)]);
        if (str_ends_with($req->url(),'/state')) return Http::response(['enabled'=>$req['enabled']]);
        if (str_ends_with($req->url(),'/api/bridge/heartbeat')) return Http::response(['accepted'=>true]);
        if (str_ends_with($req->url(),'/api/bridge/notifications')) return Http::response(['accepted'=>true,'eventId'=>$req['bridgeEventId'],'result'=>empty($req['securityCode'])?'REVIEW_CODE_UNAVAILABLE':'OBSERVED','reviewRequired'=>empty($req['securityCode'])]);
        return Http::response(['id'=>$req['id']??'fixture','enabled'=>true]);
    });
    return [$admin,$owner,$other,$r,app(HuataYape::class)];
}
function huataYapeToken(User $user): string {
    $token = Str::random(40); DB::table('mobile_tokens')->insert(['user_id'=>$user->id,'token'=>hash('sha256',$token),'created_at'=>now(),'updated_at'=>now()]); return $token;
}
function huataYapeStatus($test, string $key, bool $consent=true, bool $listener=true) {
    return $test->postJson('/api/mobile/yape/status',['installation'=>$key,'label'=>'Teléfono de prueba','listener'=>$listener,'consent'=>$consent]);
}
function huataYapeEvent(ReceptorPago $r, string $id='huata_event_000000000001'): array {
    return ['receiverId'=>$r->referencia_externa,'bridgeEventId'=>$id,'packageName'=>'com.bcp.innovacxion.yapeapp','postedAt'=>now()->toIso8601String(),'title'=>'Confirmación de Pago','text'=>'Recibiste S/ 1.00','securityCode'=>null];
}

test('sesion sin autorizacion no activa ni transmite y no recibe credenciales',function(){
    [$admin,$owner,,,$s]=huataYapeFixture(); $key=str_repeat('a',64);
    $this->withToken(huataYapeToken($owner))->withHeader('X-Huata-Installation',$key);
    huataYapeStatus($this,$key)->assertOk()->assertJsonPath('authorized',false)->assertJsonMissingPath('credential');
    $this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertForbidden();
    $this->postJson('/api/mobile/yape/heartbeat',['listenerConnected'=>true])->assertForbidden();
    Http::assertNothingSent();
    expect(DB::table('huata_yape_installations')->count())->toBe(1);
});

test('titular autorizado exige consentimiento y listener y aprovisiona una sola identidad',function(){
    [$admin,$owner,, $r,$s]=huataYapeFixture();$key=str_repeat('b',64);$this->withToken(huataYapeToken($owner));
    huataYapeStatus($this,$key,false,false)->assertOk();$id=DB::table('huata_yape_installations')->sole()->id;
    $s->approve($admin,$id,$r->id,'personal',false);
    $this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertForbidden();
    huataYapeStatus($this,$key,true,false)->assertOk();$this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertForbidden();
    huataYapeStatus($this,$key)->assertOk()->assertJsonPath('authorized',true);
    $this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertOk()->assertJsonPath('status','active')->assertJsonMissingPath('credential');
    $this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertOk();
    expect(DB::table('payment_bridge_devices')->count())->toBe(1)->and($r->fresh()->verified_at)->toBeNull()->and($r->fresh()->enabled)->toBeFalse();
    expect(Http::recorded(fn($req) => $req->method() === 'POST' && str_ends_with($req->url(), '/api/admin/devices'))->count())->toBe(1);
});

test('notificacion sin codigo llega al ingreso existente y se conserva para revision sin dinero',function(){
    [$admin,$owner,,$r,$s]=huataYapeFixture();$key=str_repeat('c',64);$token=huataYapeToken($owner);$this->withToken($token)->withHeader('X-Huata-Installation',$key);
    huataYapeStatus($this,$key);$s->approve($admin,DB::table('huata_yape_installations')->sole()->id,$r->id,'personal',false);
    $this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertOk();
    $this->postJson('/api/mobile/yape/evidence',huataYapeEvent($r))->assertStatus(202)->assertJsonPath('result','REVIEW_CODE_UNAVAILABLE')->assertJsonPath('receiptOnly',true)->assertJsonPath('reviewRequired',true);
    expect(MovimientoFinanciero::count())->toBe(0);
    Http::assertSent(fn($req)=>str_ends_with($req->url(),'/api/bridge/notifications') && $req->hasHeader('Authorization') && $req->header('Authorization')[0]!=='Bearer '.$token && $req['authorizationGrant']===str_repeat('g',64));
});

test('revocacion bloquea evidencia y conserva dispositivos y cuenta',function(){
    [$admin,$owner,,$r,$s]=huataYapeFixture();$key=str_repeat('d',64);$this->withToken(huataYapeToken($owner))->withHeader('X-Huata-Installation',$key);
    huataYapeStatus($this,$key);$id=DB::table('huata_yape_installations')->sole()->id;$s->approve($admin,$id,$r->id,'personal',false);
    $this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertOk();$s->revoke($admin,$id);
    $this->postJson('/api/mobile/yape/evidence',huataYapeEvent($r))->assertForbidden();
    huataYapeStatus($this,$key)->assertJsonPath('authorized',false);
    expect(DB::table('payment_bridge_devices')->count())->toBe(1)->and(ReceptorPago::count())->toBe(1)->and(DB::table('huata_yape_installations')->find($id)->status)->toBe('revoked');
});

test('cambiar sesion invalida la transmision personal incluso con token anterior',function(){
    [$admin,$owner,$other,$r,$s]=huataYapeFixture();$key=str_repeat('e',64);$token=huataYapeToken($owner);$this->withToken($token)->withHeader('X-Huata-Installation',$key);
    huataYapeStatus($this,$key);$id=DB::table('huata_yape_installations')->sole()->id;$s->approve($admin,$id,$r->id,'personal',false);$this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertOk();
    $this->withToken(huataYapeToken($other));huataYapeStatus($this,$key)->assertJsonPath('authorized',false);
    $this->withToken($token)->postJson('/api/mobile/yape/evidence',huataYapeEvent($r))->assertForbidden();
    huataYapeStatus($this,$key)->assertJsonPath('authorized',true);
    $this->postJson('/api/mobile/yape/heartbeat',['listenerConnected'=>true])->assertOk()->assertJsonPath('accepted',true);
});

test('receptor central no requiere capacidad comercial y rechaza receptor suplantado',function(){
    [$admin,,$other,$r,$s]=huataYapeFixture();$r->update(['user_id'=>$admin->id]);$key=str_repeat('f',64);$this->withToken(huataYapeToken($admin))->withHeader('X-Huata-Installation',$key);
    expect($admin->puedeVender('vendedor.ventas'))->toBeFalse();huataYapeStatus($this,$key);
    $s->approve($admin,DB::table('huata_yape_installations')->sole()->id,$r->id,'shared',false);$this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertOk();
    $this->postJson('/api/mobile/yape/heartbeat',['listenerConnected'=>true])->assertOk();
    $payload=huataYapeEvent($r);$payload['receiverId']='otro-receptor';$this->postJson('/api/mobile/yape/evidence',$payload)->assertForbidden();
});

test('bridge anterior se conserva hasta transferencia fisica explicita y no se crea colector duplicado',function(){
    [$admin,$owner,,$r,$s]=huataYapeFixture(true);$key=str_repeat('1',64);$this->withToken(huataYapeToken($owner));
    huataYapeStatus($this,$key);$id=DB::table('huata_yape_installations')->sole()->id;$s->approve($admin,$id,$r->id,'personal',false);
    $this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertUnprocessable();expect(DB::table('payment_bridge_devices')->count())->toBe(0);
    $s->approve($admin,$id,$r->id,'personal',true);$this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertOk();
    expect(DB::table('payment_bridge_devices')->sole()->id)->toBe('bridge-anterior')->and(DB::table('payment_bridge_devices')->count())->toBe(1);
});

test('administracion de dispositivos exige permisos y vendedor no ve descarga ni tecnicismos',function(){
    [$admin,$owner,,$r,$s]=huataYapeFixture();$key=str_repeat('2',64);$this->withToken(huataYapeToken($owner));huataYapeStatus($this,$key);$id=DB::table('huata_yape_installations')->sole()->id;
    expect(fn()=>$s->approve($owner,$id,$r->id,'personal',false))->toThrow(\Symfony\Component\HttpKernel\Exception\HttpException::class);
    $this->actingAs($owner);Livewire::test('pages::vendedor.configuracion-pagos')->assertForbidden();
    $this->actingAs($admin);Livewire::test(\App\Livewire\DispositivosHuataYape::class,['receptorId'=>$r->id])->assertOk()->assertSee('Teléfono de prueba');
});

test('fallo de alta conserva clave y referencia en reintento incluso despues del vencimiento',function(){
    [$admin,$owner,,$r,$s]=huataYapeFixture();$key=str_repeat('3',64);$this->withToken(huataYapeToken($owner));huataYapeStatus($this,$key);
    $id=DB::table('huata_yape_installations')->sole()->id;$s->approve($admin,$id,$r->id,'personal',false);
    Http::swap(new \Illuminate\Http\Client\Factory);Http::preventStrayRequests();$fail=true;$operations=[];
    Http::fake(function($req) use (&$fail,&$operations){
        if(str_ends_with($req->url(),'/health'))return Http::response(['mode'=>'commercial']);
        if($req->method()==='GET' && str_ends_with($req->url(),'/api/admin/devices'))return Http::response([]);
        if($req->method()==='POST' && str_ends_with($req->url(),'/api/admin/devices')){
            $operations[]=$req['operationId'];if($fail){$fail=false;return Http::response(['error'=>'temporary'],503);}
        }
        return Http::response(['id'=>$req['id']??'fixture','enabled'=>true]);
    });
    $this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertUnprocessable();
    $hash=DB::table('payment_bridge_devices')->sole()->secret_hash;$ticket=DB::table('huata_yape_installations')->find($id)->ticket_encrypted;
    $this->travel(11)->minutes();huataYapeStatus($this,$key);
    $this->postJson('/api/mobile/yape/activate',['installation'=>$key])->assertOk();
    expect(DB::table('payment_bridge_devices')->sole()->secret_hash)->toBe($hash)->and(DB::table('huata_yape_installations')->find($id)->ticket_encrypted)->toBe($ticket)->and($operations[0])->toBe($operations[1])->and(DB::table('payment_bridge_devices')->count())->toBe(1);
});

test('selección del vendedor retirada conserva la asignación administrativa sin cambiar propietarios',function(){
    [$admin,$owner,$other,$r]=huataYapeFixture();
    $shared=ReceptorPago::create(['user_id'=>$other->id,'nombre_receptor'=>'Compartido autorizado','celular'=>'51988888888','whatsapp'=>'51988888888','referencia_externa'=>'shared-test','verified_at'=>now()]);
    $a=app(\App\Services\Payments\AsignacionesReceptor::class);
    $a->asignar($admin,$owner,'shared',$shared->id,true,true);
    expect(fn()=>$a->seleccionar($owner,'personal',null))->toThrow(\Symfony\Component\HttpKernel\Exception\HttpException::class);
    expect($a->receptor($owner)->id)->toBe($shared->id);
    expect($shared->fresh()->user_id)->toBe($other->id)->and($r->fresh()->user_id)->toBe($owner->id);
});

test('segundo dispositivo Huata no obtiene la misma cuenta sin transferencia administrativa',function(){
    [$admin,$owner,,$r,$s]=huataYapeFixture();$this->withToken(huataYapeToken($owner));
    huataYapeStatus($this,str_repeat('4',64));$one=DB::table('huata_yape_installations')->sole()->id;$s->approve($admin,$one,$r->id,'personal',false);
    huataYapeStatus($this,str_repeat('5',64));$two=DB::table('huata_yape_installations')->where('id','!=',$one)->sole()->id;
    expect(fn()=>$s->approve($admin,$two,$r->id,'personal',false))->toThrow(\Illuminate\Validation\ValidationException::class);
    expect(DB::table('huata_yape_installations')->where('status','authorized')->count())->toBe(1);Http::assertNothingSent();
});
