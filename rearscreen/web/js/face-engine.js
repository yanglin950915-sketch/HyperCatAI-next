(function(root){"use strict";
var MOODS=["idle","happy","curious","thinking","sleepy","sad","angry","surprised","love","sleep"];
var P={
idle:{y:1,w:1,p:1,b:0,m:"soft",gx:0,gy:0},happy:{y:.62,w:1.08,p:.9,b:0,m:"smile",gx:0,gy:0},
curious:{y:1.08,w:.94,p:.86,b:.1,m:"small",gx:.18,gy:-.05},thinking:{y:.88,w:.94,p:.84,b:.12,m:"think",gx:.34,gy:-.1},
sleepy:{y:.34,w:1.05,p:.7,b:0,m:"soft",gx:0,gy:.12},sad:{y:.78,w:.94,p:.86,b:.24,m:"frown",gx:0,gy:.18},
angry:{y:.78,w:1.04,p:.9,b:-.28,m:"flat",gx:0,gy:0},surprised:{y:1.18,w:1.02,p:.72,b:.28,m:"open",gx:0,gy:0},
love:{y:.72,w:1.08,p:.9,b:0,m:"smile",gx:0,gy:0},sleep:{y:.08,w:1,p:0,b:0,m:"sleep",gx:0,gy:.1}};
function now(){return (root.performance&&root.performance.now)?root.performance.now():Date.now();}function clamp(v,a,b){return Math.max(a,Math.min(b,v));}function lerp(a,b,t){return a+(b-a)*t;}function smooth(t){return t*t*(3-2*t);}
function create(canvas,options){
if(!canvas||!canvas.getContext)throw Error("HyperCatFace: canvas required");options=options||{};
var c=canvas.getContext("2d");if(!c)throw Error("HyperCatFace: Canvas 2D unavailable");
var s={mood:"idle",fromMood:"idle",at:0,duration:360,gx:0,gy:0,tx:0,ty:0,blink:0,blinkAt:0,nextBlink:now()+1800,run:false,raf:0,w:1,h:1,dpr:1,reduced:!!options.reducedMotion};
var dead=false,rafFn=function(f){return root.setTimeout(function(){f(now());},33);},caf=root.clearTimeout;
function resize(){var r=canvas.getBoundingClientRect?canvas.getBoundingClientRect():{width:canvas.clientWidth||320,height:canvas.clientHeight||240};s.w=Math.max(1,r.width||320);s.h=Math.max(1,r.height||240);s.dpr=Math.min(1.5,root.devicePixelRatio||1);var pw=Math.round(s.w*s.dpr),ph=Math.round(s.h*s.dpr);if(canvas.width!==pw||canvas.height!==ph){canvas.width=pw;canvas.height=ph;c.setTransform(s.dpr,0,0,s.dpr,0,0);}}
function setMood(m,d){m=String(m||"idle").toLowerCase();if(!P[m])m="idle";if(m!==s.mood){s.fromMood=s.mood;s.mood=m;s.at=now();s.duration=Math.max(0,Number(d==null?360:d));if(m==="sleep"||m==="sleepy")s.nextBlink=now()+5000;}return m;}
function setGaze(x,y){s.tx=clamp(Number(x)||0,-1,1);s.ty=clamp(Number(y)||0,-1,1);}
function eye(x,y,w,h,m,side,gx,gy,blink,override){
var p=override||P[m],eh=h*p.y*(1-blink*.94),ew=w*p.w,tilt=m==="angry"?(side===0?-.1:.1):0;c.save();c.translate(x,y);c.rotate(tilt);
c.shadowColor="rgba(36,190,255,.32)";c.shadowBlur=Math.min(9,Math.max(3,w*.09));
var g=c.createLinearGradient(0,-eh*.55,0,eh*.55);g.addColorStop(0,"#e0fbff");g.addColorStop(.2,"#91edff");g.addColorStop(.65,"#36bdec");g.addColorStop(1,"#126dbb");c.fillStyle=g;c.beginPath();c.ellipse(0,0,ew*.5,Math.max(.8,eh*.5),0,0,Math.PI*2);c.fill();c.shadowBlur=0;
if(p.p>.05&&blink<.72){var ir=Math.min(ew,eh)*.31*p.p,ix=gx*w*.17,iy=gy*h*.12+eh*.045,ig=c.createRadialGradient(ix-ir*.25,iy-ir*.3,ir*.08,ix,iy,ir);ig.addColorStop(0,"#183c59");ig.addColorStop(.75,"#071a30");ig.addColorStop(1,"#020913");c.fillStyle=ig;c.beginPath();c.ellipse(ix,iy,ir*.88,ir,0,0,Math.PI*2);c.fill();c.fillStyle="#fff";c.beginPath();c.arc(ix-ir*.27,iy-ir*.34,Math.max(1.4,ir*.22),0,Math.PI*2);c.fill();c.fillStyle="rgba(255,255,255,.48)";c.beginPath();c.arc(ix+ir*.25,iy+ir*.28,Math.max(.8,ir*.09),0,Math.PI*2);c.fill();}
if(p.b){c.strokeStyle="#8cecff";c.lineWidth=Math.max(2,w*.045);c.lineCap="round";c.beginPath();var by=-h*.68;c.moveTo(-ew*.36,by+p.b*h*.25);c.quadraticCurveTo(0,by-p.b*h*.22,ew*.36,by-p.b*h*.04);c.stroke();}c.restore();}
function mouth(x,y,w,h,m){var k=P[m].m;c.save();c.translate(x,y);c.strokeStyle="#72dfff";c.fillStyle="#72dfff";c.lineWidth=Math.max(2,w*.055);c.lineCap="round";c.lineJoin="round";c.shadowColor="rgba(43,190,255,.22)";c.shadowBlur=3;c.beginPath();
if(k==="smile"){c.moveTo(-w*.5,-h*.05);c.quadraticCurveTo(0,h*.72,w*.5,-h*.05);c.stroke();}
else if(k==="frown"){c.moveTo(-w*.46,h*.28);c.quadraticCurveTo(0,-h*.38,w*.46,h*.28);c.stroke();}
else if(k==="open"){c.ellipse(0,0,w*.25,h*.42,0,0,Math.PI*2);c.fill();}
else if(k==="think"){c.arc(w*.12,0,Math.max(2,w*.09),0,Math.PI*2);c.fill();}
else if(k==="sleep"){c.moveTo(-w*.25,0);c.lineTo(w*.25,0);c.stroke();}
else if(k==="flat"){c.moveTo(-w*.38,0);c.lineTo(w*.38,0);c.stroke();}
else if(k==="small"){c.arc(0,0,Math.max(2,w*.1),0,Math.PI*2);c.stroke();}
else{c.moveTo(-w*.35,0);c.quadraticCurveTo(0,h*.2,w*.35,0);c.stroke();}c.restore();}
function draw(t){var m=s.mood,target=P[m],source=P[s.fromMood]||P.idle,elapsed=t-s.at,mix=s.duration<=0?1:smooth(clamp(elapsed/s.duration,0,1));var p={y:lerp(source.y,target.y,mix),w:lerp(source.w,target.w,mix),p:lerp(source.p,target.p,mix),b:lerp(source.b,target.b,mix),m:target.m,gx:lerp(source.gx,target.gx,mix),gy:lerp(source.gy,target.gy,mix)};var blink=0,cx=s.w*.5,ey=s.h*.43,ew=Math.min(s.w*.245,s.h*.32),eh=Math.min(s.h*.34,s.w*.30),gap=Math.min(s.w*.18,s.h*.2);
if(!s.reduced&&m!=="sleep"&&m!=="sleepy"&&t>=s.nextBlink){s.blink=1;s.blinkAt=t;s.nextBlink=t+1900+Math.random()*2700;}
if(s.blink){var bt=(t-s.blinkAt)/180;blink=bt<.5?bt*2:Math.max(0,1-(bt-.5)*2);if(bt>=1)s.blink=0;}
s.gx=lerp(s.gx,s.tx+p.gx,.075);s.gy=lerp(s.gy,s.ty+p.gy,.075);
c.clearRect(0,0,s.w,s.h);
var breathe=s.reduced?0:Math.sin(t/1500)*.012;ey+=s.h*breathe;var pulse=m==="love"&&!s.reduced?1+Math.sin(t/230)*.035:1;ew*=pulse;eh*=pulse;
eye(cx-gap,ey,ew,eh,m,0,s.gx,s.gy,blink,p);eye(cx+gap,ey,ew,eh,m,1,s.gx,s.gy,blink,p);
if(m!=="sleep"){var ny=ey+eh*.69,ns=Math.max(2.2,Math.min(s.w,s.h)*.01),ng=c.createRadialGradient(cx-ns*.3,ny-ns*.3,.5,cx,ny,ns*1.4);ng.addColorStop(0,"#e0fbff");ng.addColorStop(1,"#55bfe8");c.fillStyle=ng;c.beginPath();c.moveTo(cx-ns,ny-ns*.15);c.quadraticCurveTo(cx,ny-ns*1.15,cx+ns,ny-ns*.15);c.quadraticCurveTo(cx,ny+ns*.9,cx-ns,ny-ns*.15);c.fill();}
if(m==="happy"||m==="love"){var cr=Math.max(3,Math.min(s.w,s.h)*.018);c.fillStyle="rgba(255,112,150,.20)";c.beginPath();c.ellipse(cx-gap*1.58,ey+eh*.78,cr*1.8,cr*.72,0,0,Math.PI*2);c.ellipse(cx+gap*1.58,ey+eh*.78,cr*1.8,cr*.72,0,0,Math.PI*2);c.fill();}mouth(cx,ey+eh*.94,Math.min(s.w*.075,s.h*.11),Math.min(s.h*.055,s.w*.06),m);}
function tick(t){if(dead||!s.run)return;draw(t==null?now():t);s.raf=rafFn(tick);}
function start(){if(dead||s.run)return api;s.run=true;resize();s.at=now();s.raf=rafFn(tick);return api;}
function stop(){s.run=false;if(s.raf)caf(s.raf);s.raf=0;return api;}
function destroy(){stop();dead=true;if(root.removeEventListener)root.removeEventListener("resize",resize);if(root.document)root.document.removeEventListener("visibilitychange",visibilityChanged);}
function visibilityChanged(){if(root.document&&root.document.hidden)stop();else if(!dead)start();}
var api={start:start,stop:stop,destroy:destroy,resize:resize,setMood:setMood,setGaze:setGaze,getMood:function(){return s.mood;},getMoods:function(){return MOODS.slice();}};
if(root.addEventListener)root.addEventListener("resize",resize);if(root.document)root.document.addEventListener("visibilitychange",visibilityChanged);if(options.autoStart!==false)start();return api;}
root.HyperCatFace={version:"0.1.0",moods:MOODS.slice(),create:create};
}(typeof window!=="undefined"?window:this));
