/* Netra family sites - dynamic banner, theme, live clock and "What's new".
   Honest data rules: festival dates are for India (source: timeanddate.com India calendar, 2026 and 2027);
   Independence Day: India only (15 August); other countries' independence days were removed on 4 Oct 2026.
   No death anniversaries, ever. After 2027 there is no festival data, so no festival banner is shown (nothing is invented).
   See docs/BANNER.md for how to add a condolence banner by hand. */
(function(){
"use strict";
/* ---- CONDOLENCE BANNERS: edit this array by hand when news breaks (see docs/BANNER.md) ----
   Example (do not leave examples uncommented):
   {from:"2026-10-05", to:"2026-10-08", name:"Full name", country:"Country", text:"Short respectful line"} */
var CONDOLENCES=[];

var FEST=[[20260103,"Hazarat Ali's Birthday",0],[20260113,"Lohri",0],[20260114,"Makar Sankranti",0],[20260114,"Pongal",0],[20260123,"Vasant Panchami",0],[20260126,"Republic Day",0],[20260201,"Guru Ravidas Jayanti",0],[20260212,"Maharishi Dayanand Saraswati Jayanti",0],[20260215,"Maha Shivaratri",0],[20260217,"Lunar New Year",0],[20260219,"Ramadan Start",0],[20260219,"Shivaji Jayanti",0],[20260303,"Holika Dahana",0],[20260304,"Holi",0],[20260319,"Gudi Padwa",0],[20260319,"Ugadi",0],[20260320,"Jamat Ul-Vida",0],[20260321,"Ramzan Id",0],[20260326,"Rama Navami",0],[20260331,"Mahavir Jayanti",0],[20260402,"First day of Passover",0],[20260402,"Maundy Thursday",0],[20260403,"Good Friday",0],[20260405,"Easter Day",0],[20260414,"Ambedkar Jayanti",0],[20260414,"Mesadi",0],[20260414,"Vaisakhi",0],[20260415,"Bahag Bihu",0],[20260501,"Buddha Purnima",0],[20260501,"International Worker's Day",0],[20260509,"Birthday of Rabindranath",0],[20260528,"Bakrid",0],[20260716,"Rath Yatra",0],[20260729,"Guru Purnima",0],[20260802,"Friendship Day",0],[20260826,"Milad un-Nabi",0],[20260826,"Onam",0],[20260828,"Raksha Bandhan",0],[20260904,"Janmashtami",0],[20260904,"Janmashtami (Smarta)",0],[20260914,"Ganesh Chaturthi",0],[20261002,"Mahatma Gandhi Jayanti",0],[20261011,"First Day of Sharad Navratri",0],[20261017,"First Day of Durga Puja Festivities",0],[20261018,"Maha Saptami",0],[20261019,"Maha Ashtami",0],[20261020,"Dussehra",0],[20261020,"Maha Navami",1],[20261021,"Durga Puja ends (Vijaya Dashami)",1],[20261026,"Maharishi Valmiki Jayanti",0],[20261029,"Karaka Chaturthi",0],[20261108,"Diwali/Deepavali",0],[20261108,"Naraka Chaturdasi",0],[20261109,"Govardhan Puja",0],[20261111,"Bhai Duj",0],[20261115,"Chhat Puja (Pratihar Sashthi/Surya Sashthi)",0],[20261124,"Guru Nanak Jayanti",0],[20261205,"First Day of Hanukkah",0],[20261212,"Last day of Hanukkah",0],[20261223,"Hazarat Ali's Birthday",0],[20261224,"Christmas Eve",0],[20261225,"Christmas",0],[20261231,"New Year's Eve",0],[20270101,"New Year's Day",0],[20270114,"Makar Sankranti",0],[20270115,"Guru Govind Singh Jayanti",0],[20270115,"Pongal",0],[20270126,"Republic Day",0],[20270206,"Lunar New Year",0],[20270209,"Ramadan Start",1],[20270211,"Vasant Panchami",0],[20270219,"Shivaji Jayanti",0],[20270220,"Guru Ravidas Jayanti",0],[20270302,"Maharishi Dayanand Saraswati Jayanti",0],[20270305,"Jamat Ul-Vida",0],[20270306,"Maha Shivaratri",0],[20270310,"Ramzan Id",1],[20270322,"Dolyatra",0],[20270322,"Holi",0],[20270322,"Holika Dahana",0],[20270325,"Maundy Thursday",0],[20270326,"Good Friday",0],[20270328,"Easter Day",0],[20270407,"Chaitra Sukhladi",0],[20270407,"Cheti Chand",0],[20270407,"Gudi Padwa",0],[20270407,"Ugadi",0],[20270414,"Ambedkar Jayanti",0],[20270414,"Mesadi",0],[20270414,"Vaisakhi",0],[20270414,"Vishu",0],[20270415,"Bahag Bihu (Assam)",0],[20270415,"Rama Navami",0],[20270415,"Vaisakhadi (Bengal)",0],[20270419,"Mahavir Jayanti",0],[20270422,"First day of Passover",0],[20270501,"International Worker's Day",0],[20270509,"Birthday of Rabindranath",0],[20270517,"Bakrid",1],[20270520,"Buddha Purnima",0],[20270705,"Rath Yatra",0],[20270801,"Friendship Day",0],[20270815,"Milad un-Nabi",1],[20270815,"Parsi New Year",0],[20270817,"Raksha Bandhan",0],[20270825,"Janmashtami",0],[20270825,"Janmashtami (Smarta)",0],[20270904,"Ganesh Chaturthi",0],[20270912,"Onam",0],[20270930,"First Day of Sharad Navratri",0],[20271002,"Mahatma Gandhi Jayanti",0],[20271005,"First Day of Durga Puja Festivities",0],[20271006,"Maha Saptami",0],[20271007,"Maha Ashtami",0],[20271008,"Maha Navami",0],[20271009,"Dussehra",0],[20271015,"Maharishi Valmiki Jayanti",0],[20271018,"Karaka Chaturthi",0],[20271028,"Naraka Chaturdasi",0],[20271029,"Diwali/Deepavali",0],[20271030,"Govardhan Puja",0],[20271031,"Bhai Duj",0],[20271104,"Chhat Puja (Pratihar Sashthi/Surya Sashthi)",0],[20271114,"Guru Nanak Jayanti",0],[20271212,"Hazarat Ali's Birthday",0],[20271224,"Christmas Eve",0],[20271225,"Christmas",0],[20271225,"First Day of Hanukkah",0],[20271231,"New Year's Eve",0]]; /* [yyyymmdd, name, dateMayShiftByADay] */
var INDEP=[["India","Independence Day",8,15]]; /* [country, event, month, day] */

var THEMES={
 default:null,
 holi:{accent:"#f472b6",accent2:"#facc15",bg:"#1a0b1e",card:"#26112b",card2:"#311a38",line:"#4d2a5a"},
 diwali:{accent:"#fbbf24",accent2:"#fb923c",bg:"#1a1005",card:"#261a0a",card2:"#32230f",line:"#54391a"},
 eid:{accent:"#34d399",accent2:"#a7f3d0",bg:"#07170f",card:"#0d2418",card2:"#133121",line:"#1f5238"},
 xmas:{accent:"#f87171",accent2:"#4ade80",bg:"#150a0a",card:"#221111",card2:"#2e1818",line:"#4d2626"},
 saffron:{accent:"#fb923c",accent2:"#fbbf24",bg:"#190e06",card:"#25160a",card2:"#311e0f",line:"#523219"},
 india:{accent:"#ff9933",accent2:"#22c55e",bg:"#0b1220",card:"#121c30",card2:"#17233b",line:"#22314f"},
 fest:{accent:"#a78bfa",accent2:"#f472b6",bg:"#100b1f",card:"#181230",card2:"#201840",line:"#352a63"},
 sorrow:{accent:"#cbd5e1",accent2:"#94a3b8",bg:"#09090b",card:"#121214",card2:"#18181b",line:"#2b2b30",text:"#e4e4e7",mute:"#a1a1aa"}
};
function themeFor(n){
 if(/Holi|Dolyatra/i.test(n))return"holi";
 if(/Diwali|Deepavali|Govardhan|Bhai Duj|Naraka/i.test(n))return"diwali";
 if(/Ramzan|Ramadan|Bakrid|Milad|Jamat|Id\b/i.test(n))return"eid";
 if(/Christmas|Easter|Good Friday|Maundy/i.test(n))return"xmas";
 if(/Buddha|Mahavir|Navratri|Durga|Dussehra|Ganesh|Janmashtami|Rama|Shivaratri|Rath|Raksha|Onam|Pongal|Sankranti|Lohri|Ugadi|Gudi|Vaisakhi|Guru|Navami|Ashtami|Saptami|Chhat|Karaka/i.test(n))return"saffron";
 if(/Republic Day/i.test(n))return"india";
 return"fest";
}
function applyTheme(key){
 var t=THEMES[key];if(!t)return;var s=document.documentElement.style;
 s.setProperty("--accent",t.accent);s.setProperty("--accent2",t.accent2);
 (key==="sorrow"?["bg","card","card2","line","text","mute"]:[]).forEach(function(k){if(t[k])s.setProperty("--"+k,t[k])});
}
function ymd(d){return d.getFullYear()*10000+(d.getMonth()+1)*100+d.getDate()}
function pick(now){
 var today=ymd(now),i;
 for(i=0;i<CONDOLENCES.length;i++){var c=CONDOLENCES[i];
  var f=Number(String(c.from).replace(/-/g,"")),t=Number(String(c.to).replace(/-/g,""));
  if(today>=f&&today<=t)return{kind:"sorrow",theme:"sorrow",title:"Shok sandesh",text:(c.name?c.name+(c.country?" ("+c.country+")":"")+" ke nidhan par shok. ":"")+(c.text||"")}}
 var m=now.getMonth()+1,d=now.getDate(),names=[],ind=[],th=null;
 FEST.forEach(function(f){if(f[0]===today){names.push(f[1]+(f[2]?" (date may differ by a day)":""));if(!th)th=themeFor(f[1])}});
 var indiaInd=false;
 INDEP.forEach(function(x){if(x[2]===m&&x[3]===d){ind.push(x[0]);if(x[0]==="India")indiaInd=true}});
 if(names.length||ind.length){
  var parts=[];
  if(names.length)parts.push("Aaj: "+names.join(", "));
  if(ind.length)parts.push("Independence Day: "+ind.join(", "));
  return{kind:"fest",theme:indiaInd?"india":th,title:"Shubhkamnayein",text:parts.join(" - ")}}
 var best=null;
 for(var k=1;k<=3&&!best;k++){var n=new Date(now.getFullYear(),now.getMonth(),now.getDate()+k),y=ymd(n),nm=[];
  FEST.forEach(function(f){if(f[0]===y)nm.push(f[1])});
  if(nm.length)best={kind:"soon",theme:themeFor(nm[0]),title:"Aane wala",text:nm.join(", ")+(k===1?" - kal":" - "+k+" din baad")}}
 return best;
}
/* ---- Festival open animation: a short, skippable, offline-safe animation matching the occasion (replaces the plain banner look).
   Shown once per day per occasion, not at all if the visitor prefers reduced motion. India flag is drawn with CSS (no image needed). ---- */
function festOpen(p){
 try{
  if(!/[?&]festpreview=/.test(location.search)&&(!p||p.kind!=="fest"))return;
  if(window.matchMedia&&window.matchMedia("(prefers-reduced-motion: reduce)").matches)return;
  var key="netra_fo_"+ymd(new Date())+"_"+(p?p.theme:"x");
  var pv=/[?&]festpreview=([^&]*)/.exec(location.search);
  if(pv){p={kind:"fest",theme:/india/i.test(pv[1])?"india":"x",text:decodeURIComponent(pv[1])}}
  else{if(localStorage.getItem(key))return;localStorage.setItem(key,"1")}
  var t=(p.text||"").toLowerCase(),em=null,flag=false;
  if(p.theme==="india"||/republic day|independence day/.test(t)&&/india/.test(t))flag=true;
  if(/durga|navratri|dussehra|dashami|navami|saptami|ashtami|vijaya/.test(t))em="\uD83C\uDF3A";
  else if(/diwali|deepavali|dhanteras|lakshmi|bhai dooj|govardhan/.test(t))em="\uD83E\uDE94";
  else if(/holi|holika/.test(t))em="\uD83C\uDFA8";
  else if(/eid|ramzan|ramadan|muharram|milad/.test(t))em="\uD83C\uDF19";
  else if(/christmas|new year/.test(t))em="\u2728";
  else if(/ganesh|chaturthi/.test(t))em="\uD83C\uDF3C";
  else if(/raksha|rakhi/.test(t))em="\uD83E\uDDFF";
  else if(/pongal|sankranti|lohri|baisakhi|onam/.test(t))em="\uD83C\uDF3E";
  else em="\uD83C\uDF89";
  var st=document.createElement("style");
  st.textContent="@keyframes nfFall{0%{transform:translateY(-10vh) rotate(0);opacity:0}10%{opacity:1}100%{transform:translateY(110vh) rotate(360deg);opacity:.9}}@keyframes nfWave{0%,100%{transform:skewY(0) rotate(-1deg)}50%{transform:skewY(3deg) rotate(1deg)}}@keyframes nfIn{from{opacity:0;transform:scale(.9)}to{opacity:1;transform:scale(1)}}";
  var o=document.createElement("div");o.id="netra-fest-open";o.setAttribute("role","dialog");o.setAttribute("aria-label","Festival greeting");
  o.style.cssText="position:fixed;inset:0;z-index:99999;background:var(--bg,#0b1220);display:flex;flex-direction:column;align-items:center;justify-content:center;color:var(--text,#e8eefc);text-align:center;overflow:hidden;font-family:system-ui,Segoe UI,Roboto,sans-serif";
  for(var i=0;i<26&&!flag;i++){var s=document.createElement("span");s.textContent=em;s.style.cssText="position:absolute;top:0;left:"+(Math.random()*96)+"%;font-size:"+(18+Math.random()*26)+"px;animation:nfFall "+(4+Math.random()*3)+"s linear "+(Math.random()*2.5)+"s infinite;pointer-events:none";o.appendChild(s)}
  var box=document.createElement("div");box.style.cssText="animation:nfIn .6s ease both;padding:16px;max-width:90vw";
  if(flag){var f=document.createElement("div");f.style.cssText="width:min(300px,70vw);height:min(200px,46vw);margin:0 auto 18px;border-radius:6px;overflow:hidden;box-shadow:0 8px 30px rgba(0,0,0,.5);animation:nfWave 2.4s ease-in-out infinite;display:flex;flex-direction:column;position:relative";
   ["#FF9933","#FFFFFF","#138808"].forEach(function(c){var b=document.createElement("div");b.style.cssText="flex:1;background:"+c;f.appendChild(b)});
   var ch=document.createElement("div");ch.style.cssText="position:absolute;left:50%;top:50%;width:18%;aspect-ratio:1;transform:translate(-50%,-50%);border:2px solid #000080;border-radius:50%;background:repeating-conic-gradient(#000080 0 3deg,transparent 3deg 15deg)";f.appendChild(ch);box.appendChild(f)}
  else{var big=document.createElement("div");big.textContent=em;big.style.cssText="font-size:min(96px,22vw);margin-bottom:10px";box.appendChild(big)}
  var h=document.createElement("div");h.textContent=p.text.replace(/^Aaj:\s*/,"");h.style.cssText="font-size:clamp(1.3rem,5vw,2.2rem);font-weight:700;margin-bottom:6px";box.appendChild(h);
  var sub=document.createElement("div");sub.textContent="Shubhkamnayein - Netra by Prayagi Team";sub.style.cssText="opacity:.85";box.appendChild(sub);
  var sk=document.createElement("button");sk.textContent="Skip";sk.style.cssText="margin-top:18px;padding:8px 22px;border-radius:999px;border:1px solid currentColor;background:transparent;color:inherit;font:inherit;cursor:pointer";
  box.appendChild(sk);o.appendChild(box);
  function close(){if(o.parentNode)o.parentNode.removeChild(o);if(st.parentNode)st.parentNode.removeChild(st)}
  sk.onclick=close;o.onclick=function(e){if(e.target===o)close()};document.addEventListener("keydown",function k(e){if(e.key==="Escape"){close();document.removeEventListener("keydown",k)}});
  document.head.appendChild(st);document.body.appendChild(o);setTimeout(close,6000);
 }catch(e){}
}

function render(){
 var el=document.getElementById("festbar"),p=pick(new Date());
 if(!el)return;
 if(!p){el.hidden=true;festOpen(null);return}
 applyTheme(p.theme);
 el.className="festbar "+p.kind;el.hidden=false;if(p.kind!=="sorrow"){el.style.background="transparent";el.style.border="0";el.style.color="var(--text,inherit)"}
 el.innerHTML="";var b=document.createElement("b");b.textContent=p.title+": ";el.appendChild(b);el.appendChild(document.createTextNode(p.text));
 festOpen(p);
}
function clock(){
 var el=document.getElementById("liveclock");if(!el)return;
 function t(){var d=new Date();el.textContent=d.toLocaleDateString(undefined,{weekday:"long",day:"numeric",month:"long",year:"numeric"})+" - "+d.toLocaleTimeString()}
 t();setInterval(t,1000);
}
function whatsNew(){
 var box=document.getElementById("whatsnew-list");if(!box)return;
 var repo=(window.NETRA_SITE&&window.NETRA_SITE.repo)||"prayagi-store-and-services/netra-eco";
 fetch("https://api.github.com/repos/"+repo+"/releases?per_page=5").then(function(r){if(!r.ok)throw 0;return r.json()}).then(function(rel){
  rel=rel.filter(function(x){return !x.draft&&!x.prerelease});box.innerHTML="";
  if(!rel.length){box.textContent="No releases yet.";return}
  rel.slice(0,3).forEach(function(x){
   var h=document.createElement("h3"),a=document.createElement("a");a.href=x.html_url;a.textContent=x.tag_name;h.appendChild(a);
   var dt=document.createElement("span");dt.className="sub";try{dt.textContent="  "+new Date(x.published_at).toLocaleString(undefined,{year:"numeric",month:"short",day:"numeric",hour:"numeric",minute:"2-digit"})}catch(e){}h.appendChild(dt);box.appendChild(h);
   var ul=document.createElement("ul"),n=0;
   String(x.body||"").split(/\r?\n/).forEach(function(l){
    var m=l.match(/^\s*[-*]\s+(.*)$/);if(m&&n<8){var li=document.createElement("li");li.textContent=m[1].replace(/\*\*/g,"");ul.appendChild(li);n++}});
   if(!n){var li=document.createElement("li");li.textContent="See the release page for the notes.";ul.appendChild(li)}
   box.appendChild(ul)});
 }).catch(function(){box.innerHTML="";var a=document.createElement("span");a.textContent="Could not load the update notes right now. Please try again later.";box.appendChild(a)});
}

/* ---- Live stats: visits and downloads. No personal data is collected.
   Visits: one anonymous counter increment per browser session (abacus.jasoncameron.dev, namespace netra-pages), no cookies, no IP stored by us.
   Downloads: read live from the GitHub Releases API (asset download_count, summed over every release; includes in-app updates). */
var AB="https://abacus.jasoncameron.dev/";
function statKey(){var s=window.NETRA_SITE||{};return s.appId?("visits-"+s.appId):"visits-eco"}
function visitCount(){
 var k=statKey(),path=(sessionStorage.getItem("netra_v_"+k)?"get/":"hit/")+"netra-pages/"+k;
 return fetch(AB+path).then(function(r){return r.json()}).then(function(d){sessionStorage.setItem("netra_v_"+k,"1");return typeof d.value==="number"?d.value:null}).catch(function(){return null});
}
function stats(){
 var s=window.NETRA_SITE||{};var visits=visitCount();
 if(!s.appId)return; /* the family site draws its own stats table */
 var foot=document.querySelector("footer");if(!foot)return;
 var box=document.createElement("div");box.id="netra-stats";box.style.cssText="text-align:center;padding:10px 16px;font-size:.9rem;opacity:.9";box.textContent="Loading live stats...";
 foot.parentNode.insertBefore(box,foot);
 var dl=fetch("https://api.github.com/repos/"+s.repo+"/releases?per_page=100").then(function(r){if(!r.ok)throw 0;return r.json()}).catch(function(){return null});
 Promise.all([visits,dl]).then(function(a){
  var parts=[];
  if(a[0]!=null)parts.push("Visits: "+a[0].toLocaleString());
  if(a[1]){var t=0,last=null;a[1].forEach(function(x){if(x.draft)return;(x.assets||[]).forEach(function(y){if(/\.apk$/i.test(y.name))t+=(y.download_count||0)});if(!last)last=x});
   parts.push("Downloads: "+t.toLocaleString()+" (all releases)");
   if(last&&last.published_at){try{parts.push("Latest "+last.tag_name+" released "+new Date(last.published_at).toLocaleString(undefined,{year:"numeric",month:"short",day:"numeric",hour:"numeric",minute:"2-digit"}))}catch(e){}}}
  if(parts.length){parts.push("Checked "+new Date().toLocaleTimeString());box.textContent=parts.join("  |  ")}else box.remove();
 });
}
render();clock();whatsNew();stats();
setInterval(render,600000);
})();

/* Navratri look, 11-20 Oct 2026 (device date, no network). Red, orange and gold accents plus a short festive strip. */
(function(){
"use strict";
try{
var d=new Date(),k=d.getFullYear()*10000+(d.getMonth()+1)*100+d.getDate();
if(k<20261011||k>20261020)return;
var st=document.createElement("style");
st.textContent=":root{--accent:#F2C14E!important;--accent2:#FF8A00!important;--line:#6b2a1c!important;--ds-teal:#B71C1C!important}"+
 "#navratri-strip{background:linear-gradient(90deg,#B71C1C,#E65100,#F2C14E,#E65100,#B71C1C);color:#fff;text-align:center;font:600 14px/34px system-ui,sans-serif;letter-spacing:.04em;text-shadow:0 1px 2px rgba(0,0,0,.45)}";
document.head.appendChild(st);
var s=document.createElement("div");s.id="navratri-strip";s.textContent="Jay Mata Di  |  Shubh Navratri";
s.setAttribute("role","note");
(document.body||document.documentElement).insertBefore(s,(document.body||document.documentElement).firstChild);
}catch(e){}
})();
