package com.example
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.engine.*
import com.example.data.service.LocationContextInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class OfficialWeatherWorkerTest {
 private val ctx get()=ApplicationProvider.getApplicationContext<Application>()
 private val now=1793241000000L
 private val location=LocationContextInfo(latitude=25.5,longitude=82.5,district="TestArea",state="State",locationStatus="LOCATION_AVAILABLE")
 private val imd="""{"type":"FeatureCollection","features":[{"geometry":{"type":"Polygon","coordinates":[[[82,25],[83,25],[83,26],[82,26],[82,25]]]},"properties":{"Date":"2026-10-29","Day1_Color":3,"SUBDIV":"Fixture area","Day_1":"2"}}]}"""
 private fun cap(id:String,event:String,severity:String,sent:String,category:String="Safety")="""<cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2"><cap:identifier>$id</cap:identifier><cap:sender>Official</cap:sender><cap:sent>$sent</cap:sent><cap:status>Actual</cap:status><cap:info><cap:language>en-IN</cap:language><cap:category>$category</cap:category><cap:event>$event</cap:event><cap:severity>$severity</cap:severity><cap:expires>2026-10-29T12:00:00+05:30</cap:expires><cap:headline>$id warning</cap:headline><cap:area><cap:areaDesc>TestArea</cap:areaDesc></cap:area></cap:info></cap:alert>"""
 private fun reset(){ctx.getSharedPreferences(SachetWatchWorker.PREFS,0).edit().clear().commit();ctx.getSharedPreferences(AlertArea.PREFS,0).edit().clear().commit()}
 private fun run(io:OfficialWorkerIo)=runBlocking { TestListenableWorkerBuilder<SachetWatchWorker>(ctx).build().apply{testIo=io}.doWork() }
 @Test fun rotatingBatchesRetainRevalidatedReportsAndEqualTimeOldRevisionCannotReplay() {
  reset();val notifications=mutableListOf<String>()
  val caps=(0 until 45).map { i -> if(i==0)cap("b","Flood","Moderate","2026-10-29T08:00:00+05:30") else if(i==40)cap("a","Flood","Severe","2026-10-29T08:00:00+05:30") else cap("id$i","Hazard$i","Minor","2026-10-29T08:00:00+05:30") }
  val io=OfficialWorkerIo(now,location,{u->if(u==IMD_WARNING_URL)imd else if(u==SACHET_RSS)"<rss><channel>"+caps.indices.joinToString(""){"<item><guid>$it</guid><link>https://sachet.ndma.gov.in/$it</link></item>"}+"</channel></rss>" else caps[u.substringAfterLast('/').toInt()]},{e,_->notifications.add(e);true},{null},{_,done->done(true)})
  run(io);assertTrue(OfficialWeatherReports.text(ctx,now).contains("Hazard1:"))
  run(io);assertTrue(OfficialWeatherReports.text(ctx,now).contains("Hazard1:"));assertTrue(OfficialWeatherReports.text(ctx,now).contains("Hazard44:"))
  assertFalse(notifications.any{it.startsWith("Flood")})
  run(io);assertFalse(notifications.any{it.startsWith("Flood")})
  assertFalse(ctx.getSharedPreferences(SachetWatchWorker.PREFS,0).all.keys.any{it.contains("25.5") || it.contains("82.5")})
 }
 @Test fun blockedSpeechDoesNotBlockNotificationAndOnlyFailedOutputRetries(){
  reset();var n=0;var s=0;var blocked=true
  val io=OfficialWorkerIo(now,location,{u->if(u==IMD_WARNING_URL)imd else "<rss><channel/></rss>"},{_,_->n++;true},{if(blocked)"off" else null},{_,done->s++;done(true)})
  run(io);assertEquals(1,n);assertEquals(0,s);assertTrue(OfficialWeatherReports.text(ctx,now).contains("Yellow"))
  blocked=false;run(io);assertEquals(1,n);assertEquals(1,s);run(io);assertEquals(1,n);assertEquals(1,s)
 }
 @Test fun notificationDenialDoesNotBlockSpeechOrReports(){
  reset();var s=0
  val io=OfficialWorkerIo(now,location,{u->if(u==IMD_WARNING_URL)imd else "<rss><channel/></rss>"},{_,_->false},{null},{_,done->s++;done(true)})
  run(io);assertEquals(1,s);assertTrue(OfficialWeatherReports.text(ctx,now).contains("Yellow"));run(io);assertEquals(1,s)
 }
 @Test fun mixedSourcesAndMultipleSafetyAlertsDispatchOnceAndFailureClearsReport(){
  reset();val n=mutableListOf<String>();val spoken=mutableListOf<String>()
  val caps=listOf(cap("old","Flood","Severe","2026-10-29T07:00:00+05:30"),cap("new","Flood","Moderate","2026-10-29T08:00:00+05:30"),cap("fire","Fire","Severe","2026-10-29T08:00:00+05:30"),cap("chem","Chemical","Extreme","2026-10-29T08:00:00+05:30"),cap("weather","Rain","Severe","2026-10-29T08:00:00+05:30","Met"))
  var fail=false
  val io=OfficialWorkerIo(now,location,{u->if(fail)null else if(u==IMD_WARNING_URL)imd else if(u==SACHET_RSS)"<rss><channel>"+caps.indices.joinToString(""){"<item><guid>$it</guid><link>https://sachet.ndma.gov.in/$it</link></item>"}+"</channel></rss>" else caps[u.substringAfterLast('/').toInt()]},{e,_->n.add(e);true},{null},{e,done->spoken.add(e);done(true)})
  run(io);assertEquals(3,n.size);assertEquals(3,spoken.size);assertFalse(n.any{it.startsWith("Flood")});assertTrue(OfficialWeatherReports.text(ctx,now).contains("Moderate"))
  run(io);assertEquals(3,n.size);assertEquals(3,spoken.size)
  fail=true;run(io);assertTrue(OfficialWeatherReports.text(ctx,now).contains("unavailable or expired"))
 }
 @Test fun invalidLocationClearsReportAndAreaMutationInvalidates(){
  reset();val io=OfficialWorkerIo(now,location,{u->if(u==IMD_WARNING_URL)imd else "<rss><channel/></rss>"},{_,_->true},{null},{_,done->done(true)})
  run(io);assertTrue(OfficialWeatherReports.text(ctx,now).contains("Yellow"));AlertArea.clear(ctx);assertTrue(OfficialWeatherReports.text(ctx,now).contains("unavailable"))
  run(io);run(io.copy(location=location.copy(locationStatus="PERMISSION_NOT_GRANTED")));assertTrue(OfficialWeatherReports.text(ctx,now).contains("unavailable"))
 }
}
