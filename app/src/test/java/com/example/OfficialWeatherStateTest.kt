package com.example
import com.example.data.engine.*
import org.junit.Assert.*
import org.junit.Test
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[34], application=android.app.Application::class)
class OfficialWeatherStateTest {
 @Test fun outOfOrderRevisionsStayNewestAcrossBatchesAndRestart() {
  val old=CapAlert("a","s","Flood","Severe","old","A",1000,sentMillis=10)
  val newer=old.copy(id="b",severity="Moderate",sentMillis=20)
  val ledger=org.json.JSONObject()
  assertEquals(listOf(newer),latestCapRevisions(listOf(newer,old),ledger))
  val restored=org.json.JSONObject(ledger.toString())
  assertTrue(latestCapRevisions(listOf(old),restored).isEmpty())
  assertEquals(listOf(newer),latestCapRevisions(listOf(newer),restored))
 }
 @Test fun perItemExpiryDoesNotRemoveImdText() {
  val a=CapAlert("a","s","Flood","Severe","short","A",101)
  val b=a.copy(event="Fire",headline="long",expiresMillis=500)
  val merged=mergeCapReports(org.json.JSONArray(),listOf(a,b),100)
  assertTrue(renderCapItems(merged.toString(),100).contains("short"))
  assertFalse(renderCapItems(merged.toString(),102).contains("short"))
  assertTrue(renderCapItems(merged.toString(),102).contains("long"))
 }
 @Test fun capLifecycleExpiresAndResetsBothOutputTokens() {
  val a=capLifecycle(null,"Severe",100,1)
  assertEquals(a.token,capLifecycle(a,"Severe",200,50).token)
  val expired=capLifecycle(a,"Severe",300,101)
  assertNotEquals(a.token,expired.token)
  val low=capLifecycle(a,"Moderate",200,50)
  val high=capLifecycle(low,"Severe",300,60)
  assertNotEquals(a.token,high.token)
  assertEquals(OutputDue(true,true),officialOutputsDue(high.token,a.token,a.token,true))
 }
 @Test fun separateOutputStateDoesNotConsumeOtherChannel() {
  assertEquals(OutputDue(false,true),officialOutputsDue("Yellow","Yellow",null,true))
  assertEquals(OutputDue(true,false),officialOutputsDue("Yellow",null,"Yellow",true))
  assertEquals(OutputDue(false,false),officialOutputsDue("Green",null,null,false))
 }
 @Test fun reportsExpireAndAreaChangeInvalidates() {
  val n=1793241000000L;val r=OfficialReportSnapshot(3,n,n+1000,"Area","Report")
  assertTrue(reportVisible(r,3,n));assertFalse(reportVisible(r,4,n));assertFalse(reportVisible(r,3,n+1001));assertFalse(reportVisible(r,3,n-1))
 }
 @Test fun cursorAdvancesPastFailedFirstForty() {
  assertEquals((0..39).toList(),officialBatch(100,0));assertEquals((40..79).toList(),officialBatch(100,40));assertEquals(0,officialBatch(100,80)[20])
 }
 @Test fun responseLimitRejectsOversize() {
  assertEquals("ok",boundedOfficialText("ok".byteInputStream(),2))
  try { boundedOfficialText("too big".byteInputStream(),2);fail() } catch (_:IllegalArgumentException) { }
 }
 @Test fun weatherIsNotSecondSpokenCapSourceAndOtherHazardsRemain() {
  val a=CapAlert("id","sender","Flood","Severe","Warning","Area",1)
  assertFalse(capIsWeather(a));assertTrue(capIsWeather(a.copy(event="Thunderstorm")));assertTrue(capIsWeather(a.copy(categories=setOf("Met"))))
 }
}
